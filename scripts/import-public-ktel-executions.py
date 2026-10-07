#!/usr/bin/env python3
"""Import normalized read-only TicketWeb route-execution snapshots into the KTEL DB."""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
from datetime import datetime
from pathlib import Path


def digest(value: object) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def slug(value: object) -> str:
    return hashlib.sha1(str(value).encode()).hexdigest()[:24]


def iso(value: str | None) -> str | None:
    if not value:
        return None
    for fmt in ("%d/%m/%Y %H:%M", "%Y-%m-%dT%H:%M:%S%z", "%Y-%m-%dT%H:%M:%S"):
        try:
            return datetime.strptime(value, fmt).isoformat()
        except ValueError:
            pass
    return value


def service_date(value: str | None) -> str | None:
    parsed = iso(value)
    return parsed[:10] if parsed else None


def find_stop(conn: sqlite3.Connection, operator_id: str, external_id: str | None, name: str | None) -> str | None:
    if external_id:
        row = conn.execute(
            "select id from ktel_stops where operator_id=? and external_id=? and publication_state='published' limit 1",
            (operator_id, str(external_id)),
        ).fetchone()
        if row:
            return row[0]
        row = conn.execute(
            "select id from ktel_stop_places where operator_id=? and external_id=? and publication_state='published' limit 1",
            (operator_id, str(external_id)),
        ).fetchone()
        if row:
            return row[0]
    if name:
        row = conn.execute(
            "select id from ktel_stops where operator_id=? and lower(name)=lower(?) and publication_state='published' limit 1",
            (operator_id, name),
        ).fetchone()
        if row:
            return row[0]
    return None


def import_file(conn: sqlite3.Connection, path: Path, observed_at: str) -> int:
    data = json.loads(path.read_text())
    tenant = data["tenant"]
    # Explicit pair snapshots may be created independently of the collector and
    # therefore omit operatorId; keep the national operator registry identity.
    operator_id = data.get("operatorId") or {"ark": "ktel-arkadia"}.get(tenant, f"ktel-{tenant}")
    rows = data.get("normalizedJourneys", [])
    imported = 0
    for row in rows:
        origin = row.get("origin") or {}
        destination = row.get("destination") or {}
        line_data = row.get("line") or {}
        departure = iso(row.get("departureAt"))
        arrival = iso(row.get("arrivalAt"))
        if not departure or not origin.get("stopId") or not destination.get("stopId"):
            continue
        origin_stop = find_stop(conn, operator_id, origin.get("stopId"), origin.get("name"))
        destination_stop = find_stop(conn, operator_id, destination.get("stopId"), destination.get("name"))
        if not origin_stop or not destination_stop:
            continue

        # A stop used by a direct route-execution response is part of the
        # published journey surface even when the broader stop crawl kept a
        # duplicate candidate record. Promote only the exact matched records.
        for stop_id in (origin_stop, destination_stop):
            conn.execute("update ktel_stops set publication_state='published' where id=?", (stop_id,))
            conn.execute(
                "update ktel_stop_places set publication_state='published' where id=(select stop_place_id from ktel_stops where id=?)",
                (stop_id,),
            )

        line_external = str(line_data.get("id") or f"{origin.get('stopId')}->{destination.get('stopId')}")
        line_id = f"kl_{slug(operator_id + ':' + line_external)}"
        pattern_external = digest({"line": line_external, "from": origin.get("stopId"), "to": destination.get("stopId")})[:32]
        pattern_id = f"kjp_{slug(operator_id + ':' + pattern_external)}"
        trip_external = str(row.get("externalId") or digest(row))
        trip_external = f"{tenant}:{trip_external}:{service_date(row.get('departureAt'))}"
        trip_id = f"kt_{slug(operator_id + ':' + trip_external)}"
        fare_value = row.get("fare")
        fare = fare_value if isinstance(fare_value, dict) else {"amount": fare_value, "currency": row.get("currency") or "EUR"}
        availability_value = row.get("availability")
        availability = availability_value if isinstance(availability_value, dict) else {"status": availability_value, "availableSeats": row.get("availableSeats")}
        attrs = {
            "tenant": tenant,
            "routeExecutionId": row.get("externalId"),
            "availability": availability.get("status"),
            "availableSeats": availability.get("availableSeats"),
            "duration": row.get("duration"),
            "orderedStops": row.get("orderedStops", []),
            "provenance": row.get("provenance", {}),
            "snapshotFile": str(path),
        }
        conn.execute(
            "insert or ignore into ktel_lines(id,operator_id,public_code,name,name_el,source_id,external_id,data_status,publication_state,first_seen_at,last_seen_at) values(?,?,?,?,?,?,?,?,?,?,?)",
            (line_id, operator_id, line_external, line_data.get("name") or line_external, line_data.get("name"), "ticketweb", line_external, "observed", "published", observed_at, observed_at),
        )
        conn.execute(
            "insert or ignore into ktel_journey_patterns(id,line_id,operator_id,name,direction,source_id,external_id,geometry_status,publication_state,first_seen_at,last_seen_at) values(?,?,?,?,?,?,?,?,?,?,?)",
            (pattern_id, line_id, operator_id, line_data.get("name") or f"{origin.get('name')} - {destination.get('name')}", f"{origin.get('stopId')} > {destination.get('stopId')}", "ticketweb", pattern_external, "ordered_stops_only", "published", observed_at, observed_at),
        )
        conn.execute(
            "insert or ignore into ktel_trips(id,operator_id,line_id,pattern_id,service_date,departure_at,approximate_arrival_at,source_id,external_id,commercial_state,schedule_scope,publication_state,fare_amount,fare_currency,booking_url,observed_at,content_hash,public_attributes) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (trip_id, operator_id, line_id, pattern_id, service_date(row.get("departureAt")), departure, arrival, "ticketweb", trip_external, "bookable" if availability.get("status") == "ACTIVE" else "unknown", "date_specific_bookable", "published", fare.get("amount"), fare.get("currency") or "EUR", f"https://ktelbus.gr/{tenant}/ticketweb/", observed_at, digest(row), json.dumps(attrs, ensure_ascii=False, sort_keys=True)),
        )
        conn.execute(
            "insert or ignore into ktel_stop_times(trip_id,stop_id,stop_sequence,arrival_at,departure_at,time_status) values(?,?,?,?,?,?)",
            (trip_id, origin_stop, 1, departure, departure, "direct_response"),
        )
        conn.execute(
            "insert or ignore into ktel_stop_times(trip_id,stop_id,stop_sequence,arrival_at,departure_at,time_status) values(?,?,?,?,?,?)",
            (trip_id, destination_stop, 2, arrival, arrival, "direct_response"),
        )
        imported += 1
    return imported


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True, type=Path)
    parser.add_argument("--input", required=True, type=Path)
    args = parser.parse_args()
    observed_at = datetime.utcnow().replace(microsecond=0).isoformat() + "Z"
    conn = sqlite3.connect(args.db)
    paths = sorted(p for p in args.input.glob("*.json") if p.name != "_manifest.json") if args.input.is_dir() else [args.input]
    total = 0
    for path in paths:
        count = import_file(conn, path, observed_at)
        print(f"{path.name}: {count}")
        total += count
    conn.commit()
    print(f"total: {total}")


if __name__ == "__main__":
    main()
