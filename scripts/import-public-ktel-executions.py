#!/usr/bin/env python3
"""Import normalized read-only TicketWeb route-execution snapshots into the KTEL DB."""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo


def digest(value: object) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def slug(value: object) -> str:
    return hashlib.sha1(str(value).encode()).hexdigest()[:24]


# TicketWeb states departure and arrival as wall-clock time at the terminal,
# with no offset. Greece is the only timezone this source covers, so the offset
# is attached from the zone database rather than hardcoded: it is +03:00 in
# summer and +02:00 after EEST ends, and a fixed value would be wrong for half
# the year. Rows written without an offset were parsed as viewer-local time by
# the clients, which displayed a different instant from the rows that had one.
ATHENS = ZoneInfo("Europe/Athens")


def iso(value: str | None) -> str | None:
    if not value:
        return None
    for fmt in ("%d/%m/%Y %H:%M", "%Y-%m-%dT%H:%M:%S%z", "%Y-%m-%dT%H:%M:%S"):
        try:
            parsed = datetime.strptime(value, fmt)
        except ValueError:
            continue
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=ATHENS)
        return parsed.isoformat()
    return value


def service_date(value: str | None) -> str | None:
    parsed = iso(value)
    return parsed[:10] if parsed else None


# A stop the crawl has not promoted yet is still a legitimate match: this
# importer is what promotes it. Matching only `published` records deadlocked the
# refresh, because a candidate stop could never be matched and so could never be
# published. Quarantined and withdrawn records stay excluded, because those were
# rejected on purpose. `published` sorts first so an already-promoted record
# still wins over a duplicate candidate.
MATCHABLE = "publication_state in ('published','verified','candidate')"
MATCH_ORDER = "order by case publication_state when 'published' then 0 when 'verified' then 1 else 2 end"


def fold(value: str | None) -> str:
    """Case-fold a stop name for comparison, including Greek.

    SQLite's own `lower()` is ASCII only, so it never folded a Greek name: the
    existing matches worked purely because both sides happened to be uppercase.
    This strips combining accents and normalises final sigma as well.
    """
    text = unicodedata.normalize("NFD", (value or "").strip())
    text = "".join(c for c in text if not unicodedata.combining(c))
    return text.casefold().replace("\u03c2", "\u03c3")


def stops_by_name(conn: sqlite3.Connection, operator_id: str) -> dict[str, list[tuple[str, str | None]]]:
    """Every matchable stop for one operator, indexed by folded name."""
    index: dict[str, list[tuple[str, str | None]]] = {}
    rows = conn.execute(
        f"select id, name, stop_place_id from ktel_stops where operator_id=? and {MATCHABLE}",
        (operator_id,),
    ).fetchall()
    for stop_id, name, place_id in rows:
        index.setdefault(fold(name), []).append((stop_id, place_id))
    return index


def resolve_intermediate(index: dict[str, list[tuple[str, str | None]]], name: str) -> str | None:
    """A stop id for an intermediate call, or None when it is not certain.

    TicketWeb lists an intermediate call by bare name. A name usually matches
    two records, the inbound and outbound side of one physical place, and taking
    either is correct because they share a `stop_place_id`. When a bare name
    spans several distinct places, such as a town with a port stop and a square
    stop, there is no way to tell which one the coach calls at, so nothing is
    recorded. An intermediate stop is a boarding claim, and a guessed boarding
    claim is exactly what this product refuses to make.
    """
    candidates = index.get(fold(name)) or []
    if not candidates:
        return None
    places = {place for _, place in candidates if place}
    if len(places) > 1:
        return None
    return candidates[0][0]


def find_stop(conn: sqlite3.Connection, operator_id: str, external_id: str | None, name: str | None) -> str | None:
    if external_id:
        row = conn.execute(
            f"select id from ktel_stops where operator_id=? and external_id=? and {MATCHABLE} {MATCH_ORDER} limit 1",
            (operator_id, str(external_id)),
        ).fetchone()
        if row:
            return row[0]
        row = conn.execute(
            f"select id from ktel_stop_places where operator_id=? and external_id=? and {MATCHABLE} {MATCH_ORDER} limit 1",
            (operator_id, str(external_id)),
        ).fetchone()
        if row:
            return row[0]
    if name:
        row = conn.execute(
            f"select id from ktel_stops where operator_id=? and lower(name)=lower(?) and {MATCHABLE} {MATCH_ORDER} limit 1",
            (operator_id, name),
        ).fetchone()
        if row:
            return row[0]
    return None


def upsert_line(conn: sqlite3.Connection, operator_id: str, external_id: str, name: str | None, observed_at: str) -> str:
    """The id of this operator's line, creating it only if it is genuinely new.

    `ktel_lines` is unique on (operator_id, source_id, external_id), and the
    stop crawl may already hold that line under a different primary key. Deriving
    an id and inserting it with `or ignore` silently did nothing in that case, and
    the trip then pointed at a row that was never written, which failed the GTFS
    export as a dangling route. Resolve first, derive only as a fallback.
    """
    row = conn.execute(
        "select id from ktel_lines where operator_id=? and source_id='ticketweb' and external_id=?",
        (operator_id, external_id),
    ).fetchone()
    if row:
        # A line a live route execution runs on is part of the published surface.
        conn.execute(
            "update ktel_lines set publication_state='published', last_seen_at=? where id=?",
            (observed_at, row[0]),
        )
        return row[0]
    line_id = f"kl_{slug(operator_id + ':' + external_id)}"
    conn.execute(
        "insert into ktel_lines(id,operator_id,public_code,name,name_el,source_id,external_id,data_status,publication_state,first_seen_at,last_seen_at) values(?,?,?,?,?,?,?,?,?,?,?)",
        (line_id, operator_id, external_id, name or external_id, name, "ticketweb", external_id, "observed", "published", observed_at, observed_at),
    )
    return line_id


def upsert_pattern(
    conn: sqlite3.Connection,
    operator_id: str,
    line_id: str,
    external_id: str,
    name: str,
    direction: str,
    observed_at: str,
) -> str:
    """The id of this journey pattern, resolving an existing row the same way."""
    row = conn.execute(
        "select id from ktel_journey_patterns where operator_id=? and source_id='ticketweb' and external_id=?",
        (operator_id, external_id),
    ).fetchone()
    if row:
        conn.execute(
            "update ktel_journey_patterns set publication_state='published', last_seen_at=? where id=?",
            (observed_at, row[0]),
        )
        return row[0]
    pattern_id = f"kjp_{slug(operator_id + ':' + external_id)}"
    conn.execute(
        "insert into ktel_journey_patterns(id,line_id,operator_id,name,direction,source_id,external_id,geometry_status,publication_state,first_seen_at,last_seen_at) values(?,?,?,?,?,?,?,?,?,?,?)",
        (pattern_id, line_id, operator_id, name, direction, "ticketweb", external_id, "ordered_stops_only", "published", observed_at, observed_at),
    )
    return pattern_id


def import_file(conn: sqlite3.Connection, path: Path, observed_at: str) -> int:
    data = json.loads(path.read_text())
    tenant = data["tenant"]
    # Explicit pair snapshots may be created independently of the collector and
    # therefore omit operatorId; keep the national operator registry identity.
    operator_id = data.get("operatorId") or {"ark": "ktel-arkadia"}.get(tenant, f"ktel-{tenant}")
    rows = data.get("normalizedJourneys", [])
    index = stops_by_name(conn, operator_id)

    # One route execution is returned by several origin/destination queries, and
    # the trip key folds those together. Import the leg that lists the most
    # calls, so the boarding detail is the best the source offered rather than
    # whichever leg happened to come back first.
    richest: dict[str, dict] = {}
    for row in rows:
        key = f"{row.get('externalId')}:{service_date(row.get('departureAt'))}"
        current = richest.get(key)
        if current is None or len(row.get("orderedStops") or []) > len(current.get("orderedStops") or []):
            richest[key] = row

    imported = 0
    for row in richest.values():
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
        pattern_external = digest({"line": line_external, "from": origin.get("stopId"), "to": destination.get("stopId")})[:32]
        line_id = upsert_line(conn, operator_id, line_external, line_data.get("name"), observed_at)
        pattern_id = upsert_pattern(
            conn,
            operator_id,
            line_id,
            pattern_external,
            line_data.get("name") or f"{origin.get('name')} - {destination.get('name')}",
            f"{origin.get('stopId')} > {destination.get('stopId')}",
            observed_at,
        )
        trip_external = str(row.get("externalId") or digest(row))
        trip_external = f"{tenant}:{trip_external}:{service_date(row.get('departureAt'))}"
        trip_id = f"kt_{slug(operator_id + ':' + trip_external)}"
        fare_value = row.get("fare")
        fare = fare_value if isinstance(fare_value, dict) else {"amount": fare_value, "currency": row.get("currency") or "EUR"}
        availability_value = row.get("availability")
        availability = availability_value if isinstance(availability_value, dict) else {"status": availability_value, "availableSeats": row.get("availableSeats")}
        # The intermediate calls. The source names them but publishes no time
        # for them, so they carry null times and `unknown` quality, which the
        # contract already models. Writing the name without a time is honest;
        # interpolating a time would not be.
        ordered = [n for n in (row.get("orderedStops") or []) if isinstance(n, str)]
        intermediates: list[str] = []
        unresolved: list[str] = []
        for call_name in ordered[1:-1]:
            stop = resolve_intermediate(index, call_name)
            if stop and stop not in (origin_stop, destination_stop) and stop not in intermediates:
                intermediates.append(stop)
            else:
                unresolved.append(call_name)

        attrs = {
            "tenant": tenant,
            "unresolvedIntermediateStops": unresolved,
            "routeExecutionId": row.get("externalId"),
            "availability": availability.get("status"),
            "availableSeats": availability.get("availableSeats"),
            "duration": row.get("duration"),
            "orderedStops": row.get("orderedStops", []),
            "provenance": row.get("provenance", {}),
            "snapshotFile": str(path),
        }
        conn.execute(
            "insert or ignore into ktel_trips(id,operator_id,line_id,pattern_id,service_date,departure_at,approximate_arrival_at,source_id,external_id,commercial_state,schedule_scope,publication_state,fare_amount,fare_currency,booking_url,observed_at,content_hash,public_attributes) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (trip_id, operator_id, line_id, pattern_id, service_date(row.get("departureAt")), departure, arrival, "ticketweb", trip_external, "bookable" if availability.get("status") == "ACTIVE" else "unknown", "date_specific_bookable", "published", fare.get("amount"), fare.get("currency") or "EUR", f"https://ktelbus.gr/{tenant}/ticketweb/", observed_at, digest(row), json.dumps(attrs, ensure_ascii=False, sort_keys=True)),
        )
        # The departure is the operator's published time; the arrival is the
        # response's own approximation, which is why the two differ in status.
        calls: list[tuple[str, str | None, str | None, str]] = [
            (origin_stop, departure, departure, "scheduled"),
            *((stop, None, None, "unknown") for stop in intermediates),
            (destination_stop, arrival, arrival, "approximate"),
        ]
        for sequence, (stop, arrive, depart, quality) in enumerate(calls, start=1):
            conn.execute(
                "insert or ignore into ktel_stop_times(trip_id,stop_id,stop_sequence,arrival_at,departure_at,time_status) values(?,?,?,?,?,?)",
                (trip_id, stop, sequence, arrive, depart, quality),
            )
        # A stop the coach actually calls at is part of the published journey
        # surface, so the ones that resolved are promoted like the endpoints.
        for stop in intermediates:
            conn.execute("update ktel_stops set publication_state='published' where id=?", (stop,))
            conn.execute(
                "update ktel_stop_places set publication_state='published' where id=(select stop_place_id from ktel_stops where id=?)",
                (stop,),
            )
        imported += 1
    return imported


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db", required=True, type=Path)
    parser.add_argument("--input", required=True, type=Path)
    args = parser.parse_args()
    observed_at = datetime.now(timezone.utc).replace(microsecond=0, tzinfo=None).isoformat() + "Z"
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
