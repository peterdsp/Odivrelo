"""Normalized KTEL import contract and review workflow.

Adapters convert source-specific payloads into the documented normalized JSON
shape accepted here. Imported transport entities default to candidate state.
Nothing reaches public API queries until an explicit review publishes it.
"""
from __future__ import annotations

import json
import sqlite3
import uuid
from datetime import datetime, timezone
from typing import Any

from .ktel_registry import content_hash, coordinate_status, stable_entity_id

PUBLICATION_STATES = {
    "candidate",
    "verified",
    "published",
    "stale",
    "withdrawn",
    "quarantined",
}
REVIEWABLE_TABLES = {
    "stop_place": "ktel_stop_places",
    "stop": "ktel_stops",
    "line": "ktel_lines",
    "journey_pattern": "ktel_journey_patterns",
    "service_calendar": "ktel_service_calendars",
    "trip": "ktel_trips",
}


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _state(value: str | None, *, quarantine: bool = False) -> str:
    if quarantine:
        return "quarantined"
    state = value or "candidate"
    if state not in PUBLICATION_STATES:
        raise ValueError(f"invalid publicationState: {state}")
    return state


def _ensure_registry_row(
    conn: sqlite3.Connection,
    table: str,
    identifier: str,
) -> None:
    row = conn.execute(
        f"SELECT id FROM {table} WHERE id=?", (identifier,)
    ).fetchone()
    if not row:
        raise ValueError(f"unknown {table} id: {identifier}")


def import_normalized_snapshot(
    conn: sqlite3.Connection,
    payload: dict[str, Any],
) -> dict[str, Any]:
    operator_id = str(payload["operatorId"])
    source_id = str(payload["sourceId"])
    retrieved_at = str(payload.get("retrievedAt") or _now_iso())
    _ensure_registry_row(conn, "ktel_operators", operator_id)
    _ensure_registry_row(conn, "ktel_sources", source_id)

    run_id = str(payload.get("importRunId") or uuid.uuid4())
    counters = {
        "stopPlaces": 0,
        "stops": 0,
        "lines": 0,
        "journeyPatterns": 0,
        "trips": 0,
        "quarantined": 0,
    }
    stop_place_ids: dict[str, str] = {}
    stop_ids: dict[str, str] = {}
    line_ids: dict[str, str] = {}
    pattern_ids: dict[str, str] = {}

    conn.execute("BEGIN")
    try:
        conn.execute(
            """
            INSERT INTO ktel_import_runs(
                id, source_id, operator_id, run_kind, status, started_at
            ) VALUES (?, ?, ?, 'manual', 'running', ?)
            """,
            (run_id, source_id, operator_id, retrieved_at),
        )

        for item in payload.get("stopPlaces", []):
            external_id = str(item["externalId"])
            entity_id = str(
                item.get("id")
                or f"kp_{stable_entity_id(operator_id, source_id, 'place', external_id)}"
            )
            stop_place_ids[external_id] = entity_id
            state = _state(item.get("publicationState"))
            conn.execute(
                """
                INSERT INTO ktel_stop_places(
                    id, operator_id, name, name_el, source_id, external_id,
                    default_stop_id, web_active, publication_state,
                    content_hash, first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(operator_id, source_id, external_id) DO UPDATE SET
                    name=excluded.name,
                    name_el=excluded.name_el,
                    default_stop_id=excluded.default_stop_id,
                    web_active=excluded.web_active,
                    publication_state=excluded.publication_state,
                    content_hash=excluded.content_hash,
                    last_seen_at=excluded.last_seen_at
                """,
                (
                    entity_id,
                    operator_id,
                    str(item["name"]),
                    item.get("nameEl"),
                    source_id,
                    external_id,
                    item.get("defaultStopExternalId"),
                    1 if item.get("webActive") else 0,
                    state,
                    content_hash(item),
                    retrieved_at,
                    retrieved_at,
                ),
            )
            _record_source_row(
                conn, run_id, source_id, operator_id, "stop_place",
                external_id, item, retrieved_at
            )
            counters["stopPlaces"] += 1

        for item in payload.get("stops", []):
            external_id = str(item["externalId"])
            entity_id = str(
                item.get("id")
                or f"ks_{stable_entity_id(operator_id, source_id, 'stop', external_id)}"
            )
            stop_ids[external_id] = entity_id
            coord_status = coordinate_status(
                item.get("latitude"), item.get("longitude")
            )
            quarantine = coord_status not in {"valid", "missing"}
            state = _state(
                item.get("publicationState"), quarantine=quarantine
            )
            if quarantine:
                counters["quarantined"] += 1
                _record_issue(
                    conn,
                    run_id,
                    operator_id,
                    "stop",
                    external_id,
                    f"coordinate_{coord_status}",
                    {"latitude": item.get("latitude"), "longitude": item.get("longitude")},
                )
            stop_place_id = None
            if item.get("stopPlaceExternalId") is not None:
                stop_place_id = stop_place_ids.get(
                    str(item["stopPlaceExternalId"])
                )
            conn.execute(
                """
                INSERT INTO ktel_stops(
                    id, operator_id, stop_place_id, name, name_el, address,
                    phone, latitude, longitude, coordinate_status, web_active,
                    publication_state, source_id, external_id, content_hash,
                    first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(operator_id, source_id, external_id) DO UPDATE SET
                    stop_place_id=excluded.stop_place_id,
                    name=excluded.name,
                    name_el=excluded.name_el,
                    address=excluded.address,
                    phone=excluded.phone,
                    latitude=excluded.latitude,
                    longitude=excluded.longitude,
                    coordinate_status=excluded.coordinate_status,
                    web_active=excluded.web_active,
                    publication_state=excluded.publication_state,
                    content_hash=excluded.content_hash,
                    last_seen_at=excluded.last_seen_at
                """,
                (
                    entity_id,
                    operator_id,
                    stop_place_id,
                    str(item["name"]),
                    item.get("nameEl"),
                    item.get("address"),
                    item.get("phone"),
                    item.get("latitude"),
                    item.get("longitude"),
                    coord_status,
                    1 if item.get("webActive") else 0,
                    state,
                    source_id,
                    external_id,
                    content_hash(item),
                    retrieved_at,
                    retrieved_at,
                ),
            )
            _record_source_row(
                conn, run_id, source_id, operator_id, "stop",
                external_id, item, retrieved_at
            )
            counters["stops"] += 1

        for item in payload.get("lines", []):
            external_id = str(item["externalId"])
            entity_id = str(
                item.get("id")
                or f"kl_{stable_entity_id(operator_id, source_id, 'line', external_id)}"
            )
            line_ids[external_id] = entity_id
            conn.execute(
                """
                INSERT INTO ktel_lines(
                    id, operator_id, public_code, name, name_el, source_id,
                    external_id, data_status, publication_state,
                    first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(operator_id, source_id, external_id) DO UPDATE SET
                    public_code=excluded.public_code,
                    name=excluded.name,
                    name_el=excluded.name_el,
                    data_status=excluded.data_status,
                    publication_state=excluded.publication_state,
                    last_seen_at=excluded.last_seen_at
                """,
                (
                    entity_id,
                    operator_id,
                    item.get("publicCode"),
                    str(item["name"]),
                    item.get("nameEl"),
                    source_id,
                    external_id,
                    item.get("dataStatus", "observed"),
                    _state(item.get("publicationState")),
                    retrieved_at,
                    retrieved_at,
                ),
            )
            _record_source_row(
                conn, run_id, source_id, operator_id, "line",
                external_id, item, retrieved_at
            )
            counters["lines"] += 1

        for item in payload.get("journeyPatterns", []):
            external_id = str(item["externalId"])
            entity_id = str(
                item.get("id")
                or f"kjp_{stable_entity_id(operator_id, source_id, 'pattern', external_id)}"
            )
            pattern_ids[external_id] = entity_id
            line_id = line_ids.get(str(item.get("lineExternalId")))
            geometry_status = item.get("geometryStatus", "ordered_stops_only")
            conn.execute(
                """
                INSERT INTO ktel_journey_patterns(
                    id, line_id, operator_id, name, direction, source_id,
                    external_id, geometry_status, geometry_geojson,
                    publication_state, first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(operator_id, source_id, external_id) DO UPDATE SET
                    line_id=excluded.line_id,
                    name=excluded.name,
                    direction=excluded.direction,
                    geometry_status=excluded.geometry_status,
                    geometry_geojson=excluded.geometry_geojson,
                    publication_state=excluded.publication_state,
                    last_seen_at=excluded.last_seen_at
                """,
                (
                    entity_id,
                    line_id,
                    operator_id,
                    str(item["name"]),
                    item.get("direction"),
                    source_id,
                    external_id,
                    geometry_status,
                    json.dumps(item.get("geometryGeoJson"), ensure_ascii=False)
                    if item.get("geometryGeoJson") is not None else None,
                    _state(item.get("publicationState")),
                    retrieved_at,
                    retrieved_at,
                ),
            )
            conn.execute(
                "DELETE FROM ktel_pattern_stops WHERE pattern_id=?",
                (entity_id,),
            )
            for sequence, stop in enumerate(item.get("stops", []), start=1):
                stop_id = stop_ids.get(str(stop["stopExternalId"]))
                if not stop_id:
                    _record_issue(
                        conn, run_id, operator_id, "journey_pattern",
                        external_id, "unknown_stop_reference", stop
                    )
                    counters["quarantined"] += 1
                    continue
                conn.execute(
                    """
                    INSERT INTO ktel_pattern_stops(
                        pattern_id, stop_id, stop_sequence,
                        pickup_type, dropoff_type
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    (
                        entity_id,
                        stop_id,
                        int(stop.get("sequence", sequence)),
                        stop.get("pickupType", "unknown"),
                        stop.get("dropoffType", "unknown"),
                    ),
                )
            _record_source_row(
                conn, run_id, source_id, operator_id, "journey_pattern",
                external_id, item, retrieved_at
            )
            counters["journeyPatterns"] += 1

        for item in payload.get("trips", []):
            external_id = str(item["externalId"])
            service_date = item.get("serviceDate")
            entity_id = str(
                item.get("id")
                or f"kt_{stable_entity_id(operator_id, source_id, 'trip', external_id, service_date)}"
            )
            line_id = line_ids.get(str(item.get("lineExternalId")))
            pattern_id = pattern_ids.get(str(item.get("patternExternalId")))
            conn.execute(
                """
                INSERT INTO ktel_trips(
                    id, operator_id, line_id, pattern_id, service_date,
                    departure_at, approximate_arrival_at, source_id,
                    external_id, commercial_state, schedule_scope,
                    publication_state, fare_amount, fare_currency, booking_url,
                    observed_at, expires_at, content_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(operator_id, source_id, external_id, service_date)
                DO UPDATE SET
                    line_id=excluded.line_id,
                    pattern_id=excluded.pattern_id,
                    departure_at=excluded.departure_at,
                    approximate_arrival_at=excluded.approximate_arrival_at,
                    commercial_state=excluded.commercial_state,
                    schedule_scope=excluded.schedule_scope,
                    publication_state=excluded.publication_state,
                    fare_amount=excluded.fare_amount,
                    fare_currency=excluded.fare_currency,
                    booking_url=excluded.booking_url,
                    observed_at=excluded.observed_at,
                    expires_at=excluded.expires_at,
                    content_hash=excluded.content_hash
                """,
                (
                    entity_id,
                    operator_id,
                    line_id,
                    pattern_id,
                    service_date,
                    str(item["departureAt"]),
                    item.get("approximateArrivalAt"),
                    source_id,
                    external_id,
                    item.get("commercialState", "unknown"),
                    item.get("scheduleScope", "date_specific_bookable"),
                    _state(item.get("publicationState")),
                    item.get("fareAmount"),
                    item.get("fareCurrency"),
                    item.get("bookingUrl"),
                    item.get("observedAt", retrieved_at),
                    item.get("expiresAt"),
                    content_hash(item),
                ),
            )
            conn.execute("DELETE FROM ktel_stop_times WHERE trip_id=?", (entity_id,))
            for sequence, stop_time in enumerate(item.get("stopTimes", []), start=1):
                stop_id = stop_ids.get(str(stop_time["stopExternalId"]))
                if not stop_id:
                    _record_issue(
                        conn, run_id, operator_id, "trip",
                        external_id, "unknown_stop_reference", stop_time
                    )
                    counters["quarantined"] += 1
                    continue
                conn.execute(
                    """
                    INSERT INTO ktel_stop_times(
                        trip_id, stop_id, stop_sequence, arrival_at,
                        departure_at, time_status
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (
                        entity_id,
                        stop_id,
                        int(stop_time.get("sequence", sequence)),
                        stop_time.get("arrivalAt"),
                        stop_time.get("departureAt"),
                        stop_time.get("timeStatus", "unknown"),
                    ),
                )
            _record_source_row(
                conn, run_id, source_id, operator_id, "trip",
                external_id, item, retrieved_at
            )
            counters["trips"] += 1

        records_seen = sum(
            counters[key] for key in (
                "stopPlaces", "stops", "lines", "journeyPatterns", "trips"
            )
        )
        conn.execute(
            """
            UPDATE ktel_import_runs
            SET status=?, finished_at=?, records_seen=?, records_written=?,
                records_quarantined=?
            WHERE id=?
            """,
            (
                "partial" if counters["quarantined"] else "succeeded",
                _now_iso(),
                records_seen,
                records_seen,
                counters["quarantined"],
                run_id,
            ),
        )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise
    return {"importRunId": run_id, **counters}


def review_entity(
    conn: sqlite3.Connection,
    *,
    entity_kind: str,
    entity_id: str,
    action: str,
    reviewer: str,
    reason: str | None = None,
) -> None:
    table = REVIEWABLE_TABLES.get(entity_kind)
    if not table:
        raise ValueError(f"unsupported entity kind: {entity_kind}")
    state_for_action = {
        "verify": "verified",
        "publish": "published",
        "quarantine": "quarantined",
        "reject": "withdrawn",
        "withdraw": "withdrawn",
        "restore": "candidate",
    }
    new_state = state_for_action.get(action)
    if not new_state:
        raise ValueError(f"unsupported review action: {action}")
    hash_tables = {"ktel_stop_places", "ktel_stops", "ktel_trips"}
    select_column = "content_hash" if table in hash_tables else "NULL AS content_hash"
    row = conn.execute(
        f"SELECT {select_column} FROM {table} WHERE id=?", (entity_id,)
    ).fetchone()
    if not row:
        raise ValueError(f"unknown {entity_kind}: {entity_id}")
    if entity_kind == "stop":
        stop = conn.execute(
            "SELECT coordinate_status FROM ktel_stops WHERE id=?", (entity_id,)
        ).fetchone()
        if action == "publish" and stop["coordinate_status"] not in {
            "valid", "missing"
        }:
            raise ValueError("cannot publish a stop with quarantined coordinates")
    conn.execute("BEGIN")
    try:
        conn.execute(
            f"UPDATE {table} SET publication_state=? WHERE id=?",
            (new_state, entity_id),
        )
        conn.execute(
            """
            INSERT INTO ktel_review_events(
                entity_kind, entity_id, action, reviewer, reason,
                before_hash, after_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                entity_kind,
                entity_id,
                action,
                reviewer,
                reason,
                row["content_hash"],
                row["content_hash"],
            ),
        )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise


def _record_source_row(
    conn: sqlite3.Connection,
    run_id: str,
    source_id: str,
    operator_id: str,
    record_kind: str,
    external_id: str,
    item: dict[str, Any],
    retrieved_at: str,
) -> None:
    digest = content_hash(item)
    record_id = f"ksr_{stable_entity_id(source_id, record_kind, external_id, digest)}"
    conn.execute(
        """
        INSERT OR IGNORE INTO ktel_source_records(
            id, source_id, operator_id, import_run_id, record_kind,
            external_id, normalized_json, content_hash, retrieved_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            record_id,
            source_id,
            operator_id,
            run_id,
            record_kind,
            external_id,
            json.dumps(item, ensure_ascii=False, sort_keys=True),
            digest,
            retrieved_at,
        ),
    )


def _record_issue(
    conn: sqlite3.Connection,
    run_id: str,
    operator_id: str,
    entity_kind: str,
    external_id: str,
    issue_code: str,
    details: dict[str, Any],
) -> None:
    conn.execute(
        """
        INSERT INTO ktel_import_issues(
            import_run_id, operator_id, entity_kind, external_id,
            issue_code, severity, details_json
        ) VALUES (?, ?, ?, ?, ?, 'warning', ?)
        """,
        (
            run_id,
            operator_id,
            entity_kind,
            external_id,
            issue_code,
            json.dumps(details, ensure_ascii=False, sort_keys=True),
        ),
    )
