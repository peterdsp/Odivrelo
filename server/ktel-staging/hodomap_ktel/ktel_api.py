"""Read-only national KTEL API queries and snapshot builders."""
from __future__ import annotations

import sqlite3
from datetime import datetime, timezone
from typing import Any

from .ktel_registry import coverage_summary, operator_rows, source_rows


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def release_metadata(conn: sqlite3.Connection) -> dict[str, str | None]:
    row = conn.execute(
        """
        SELECT id, published_at FROM ktel_publication_releases
        WHERE status='published'
        ORDER BY published_at DESC, created_at DESC
        LIMIT 1
        """
    ).fetchone()
    return {
        "releaseId": row["id"] if row else None,
        "publishedAt": row["published_at"] if row else None,
    }


def release_id(conn: sqlite3.Connection) -> str | None:
    return release_metadata(conn)["releaseId"]


def release_updated_at(conn: sqlite3.Connection) -> str:
    return str(release_metadata(conn)["publishedAt"] or _now_iso())


def registry_payload(conn: sqlite3.Connection) -> dict[str, Any]:
    return {
        "releaseId": release_id(conn),
        "updatedAt": release_updated_at(conn),
        "scope": "POAYS intercity registry plus verified non-federation tenants",
        "operators": operator_rows(conn, official_only=False),
    }


def coverage_payload(conn: sqlite3.Connection) -> dict[str, Any]:
    return {
        "releaseId": release_id(conn),
        "updatedAt": release_updated_at(conn),
        **coverage_summary(conn),
    }


def sources_payload(conn: sqlite3.Connection) -> dict[str, Any]:
    return {
        "releaseId": release_id(conn),
        "updatedAt": release_updated_at(conn),
        "sources": source_rows(conn),
    }


def stops_payload(
    conn: sqlite3.Connection,
    *,
    query: str | None = None,
    operator_id: str | None = None,
    coordinate_status: str | None = None,
    web_active: bool | None = None,
    limit: int = 100,
    offset: int = 0,
) -> dict[str, Any]:
    clauses: list[str] = ["s.publication_state = 'published'"]
    params: list[Any] = []
    if query:
        clauses.append("(s.name LIKE ? OR COALESCE(s.name_el, '') LIKE ?)")
        like = f"%{query.strip()}%"
        params.extend((like, like))
    if operator_id:
        clauses.append("s.operator_id = ?")
        params.append(operator_id)
    if coordinate_status:
        clauses.append("s.coordinate_status = ?")
        params.append(coordinate_status)
    if web_active is not None:
        clauses.append("s.web_active = ?")
        params.append(1 if web_active else 0)
    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    limit = max(1, min(int(limit), 500))
    offset = max(0, int(offset))
    count = conn.execute(
        f"SELECT COUNT(*) AS n FROM ktel_stops s {where}", params
    ).fetchone()["n"]
    rows = conn.execute(
        f"""
        SELECT s.id, s.operator_id, o.name_en AS operator_name,
               s.stop_place_id, s.name, s.name_el, s.address, s.phone,
               s.latitude, s.longitude, s.coordinate_status, s.web_active,
               s.source_id, s.external_id, s.first_seen_at, s.last_seen_at
        FROM ktel_stops s
        JOIN ktel_operators o ON o.id = s.operator_id
        {where}
        ORDER BY s.name, s.id
        LIMIT ? OFFSET ?
        """,
        [*params, limit, offset],
    ).fetchall()
    return {
        "releaseId": release_id(conn),
        "updatedAt": release_updated_at(conn),
        "total": count,
        "limit": limit,
        "offset": offset,
        "stops": [
            {
                "id": row["id"],
                "operatorId": row["operator_id"],
                "operatorName": row["operator_name"],
                "stopPlaceId": row["stop_place_id"],
                "name": row["name"],
                "nameEl": row["name_el"],
                "address": row["address"],
                "phone": row["phone"],
                "latitude": row["latitude"],
                "longitude": row["longitude"],
                "coordinateStatus": row["coordinate_status"],
                "webActive": bool(row["web_active"]),
                "sourceId": row["source_id"],
                "externalId": row["external_id"],
                "firstSeenAt": row["first_seen_at"],
                "lastSeenAt": row["last_seen_at"],
            }
            for row in rows
        ],
    }


def routes_payload(
    conn: sqlite3.Connection,
    *,
    operator_id: str | None = None,
    query: str | None = None,
    limit: int = 100,
    offset: int = 0,
) -> dict[str, Any]:
    clauses: list[str] = ["p.publication_state = 'published'"]
    params: list[Any] = []
    if operator_id:
        clauses.append("p.operator_id = ?")
        params.append(operator_id)
    if query:
        clauses.append(
            "(p.name LIKE ? OR COALESCE(l.name, '') LIKE ? "
            "OR COALESCE(l.public_code, '') LIKE ?)"
        )
        like = f"%{query.strip()}%"
        params.extend((like, like, like))
    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    limit = max(1, min(int(limit), 500))
    offset = max(0, int(offset))
    count = conn.execute(
        f"SELECT COUNT(*) AS n FROM ktel_journey_patterns p "
        f"LEFT JOIN ktel_lines l ON l.id=p.line_id {where}",
        params,
    ).fetchone()["n"]
    rows = conn.execute(
        f"""
        SELECT p.id, p.operator_id, o.name_en AS operator_name,
               p.line_id, l.public_code, l.name AS line_name,
               p.name AS pattern_name, p.direction, p.source_id,
               p.external_id, p.geometry_status, p.geometry_geojson,
               p.first_seen_at, p.last_seen_at,
               COUNT(ps.stop_id) AS stop_count
        FROM ktel_journey_patterns p
        JOIN ktel_operators o ON o.id=p.operator_id
        LEFT JOIN ktel_lines l ON l.id=p.line_id
        LEFT JOIN ktel_pattern_stops ps ON ps.pattern_id=p.id
        {where}
        GROUP BY p.id
        ORDER BY o.federation_number, l.name, p.name
        LIMIT ? OFFSET ?
        """,
        [*params, limit, offset],
    ).fetchall()
    return {
        "releaseId": release_id(conn),
        "updatedAt": release_updated_at(conn),
        "total": count,
        "limit": limit,
        "offset": offset,
        "routes": [
            {
                "patternId": row["id"],
                "operatorId": row["operator_id"],
                "operatorName": row["operator_name"],
                "lineId": row["line_id"],
                "lineCode": row["public_code"],
                "lineName": row["line_name"],
                "patternName": row["pattern_name"],
                "direction": row["direction"],
                "stopCount": row["stop_count"],
                "geometryStatus": row["geometry_status"],
                "geometryGeoJson": row["geometry_geojson"],
                "sourceId": row["source_id"],
                "externalId": row["external_id"],
                "firstSeenAt": row["first_seen_at"],
                "lastSeenAt": row["last_seen_at"],
            }
            for row in rows
        ],
    }


def route_detail_payload(
    conn: sqlite3.Connection,
    pattern_id: str,
) -> dict[str, Any] | None:
    route = conn.execute(
        """
        SELECT p.id, p.operator_id, o.name_en AS operator_name,
               p.line_id, l.public_code, l.name AS line_name,
               p.name AS pattern_name, p.direction, p.source_id,
               p.external_id, p.geometry_status, p.geometry_geojson,
               p.first_seen_at, p.last_seen_at
        FROM ktel_journey_patterns p
        JOIN ktel_operators o ON o.id=p.operator_id
        LEFT JOIN ktel_lines l ON l.id=p.line_id
        WHERE p.id=? AND p.publication_state='published'
        """,
        (pattern_id,),
    ).fetchone()
    if not route:
        return None
    stop_rows = conn.execute(
        """
        SELECT ps.stop_sequence, ps.pickup_type, ps.dropoff_type,
               s.id, s.name, s.name_el, s.latitude, s.longitude,
               s.coordinate_status
        FROM ktel_pattern_stops ps
        JOIN ktel_stops s ON s.id=ps.stop_id
        WHERE ps.pattern_id=?
        ORDER BY ps.stop_sequence
        """,
        (pattern_id,),
    ).fetchall()
    return {
        "releaseId": release_id(conn),
        "updatedAt": release_updated_at(conn),
        "route": {
            "patternId": route["id"],
            "operatorId": route["operator_id"],
            "operatorName": route["operator_name"],
            "lineId": route["line_id"],
            "lineCode": route["public_code"],
            "lineName": route["line_name"],
            "patternName": route["pattern_name"],
            "direction": route["direction"],
            "geometryStatus": route["geometry_status"],
            "geometryGeoJson": route["geometry_geojson"],
            "sourceId": route["source_id"],
            "externalId": route["external_id"],
            "firstSeenAt": route["first_seen_at"],
            "lastSeenAt": route["last_seen_at"],
            "stops": [
                {
                    "sequence": row["stop_sequence"],
                    "id": row["id"],
                    "name": row["name"],
                    "nameEl": row["name_el"],
                    "latitude": row["latitude"],
                    "longitude": row["longitude"],
                    "coordinateStatus": row["coordinate_status"],
                    "pickupType": row["pickup_type"],
                    "dropoffType": row["dropoff_type"],
                }
                for row in stop_rows
            ],
        },
    }


def trips_payload(
    conn: sqlite3.Connection,
    *,
    service_date: str,
    origin_stop_id: str | None = None,
    destination_stop_id: str | None = None,
    operator_id: str | None = None,
    limit: int = 100,
) -> dict[str, Any]:
    clauses = ["t.service_date = ?", "t.publication_state = 'published'"]
    join_params: list[Any] = []
    where_params: list[Any] = [service_date]
    joins: list[str] = []
    if origin_stop_id:
        joins.append(
            "JOIN ktel_stop_times origin_st ON origin_st.trip_id=t.id "
            "AND origin_st.stop_id=?"
        )
        join_params.append(origin_stop_id)
    if destination_stop_id:
        joins.append(
            "JOIN ktel_stop_times destination_st ON destination_st.trip_id=t.id "
            "AND destination_st.stop_id=?"
        )
        join_params.append(destination_stop_id)
        if origin_stop_id:
            clauses.append("origin_st.stop_sequence < destination_st.stop_sequence")
    if operator_id:
        clauses.append("t.operator_id = ?")
        where_params.append(operator_id)
    limit = max(1, min(int(limit), 200))
    rows = conn.execute(
        f"""
        SELECT DISTINCT t.id, t.operator_id, o.name_en AS operator_name,
               t.line_id, t.pattern_id, p.name AS pattern_name,
               t.service_date, t.departure_at, t.approximate_arrival_at,
               t.source_id, t.external_id, t.commercial_state,
               t.schedule_scope, t.fare_amount, t.fare_currency,
               t.booking_url, t.observed_at, t.expires_at
        FROM ktel_trips t
        JOIN ktel_operators o ON o.id=t.operator_id
        LEFT JOIN ktel_journey_patterns p ON p.id=t.pattern_id
        {' '.join(joins)}
        WHERE {' AND '.join(clauses)}
        ORDER BY t.departure_at, t.operator_id, t.id
        LIMIT ?
        """,
        [*join_params, *where_params, limit],
    ).fetchall()
    return {
        "releaseId": release_id(conn),
        "updatedAt": release_updated_at(conn),
        "serviceDate": service_date,
        "originStopId": origin_stop_id,
        "destinationStopId": destination_stop_id,
        "completeness": "partial",
        "resultStatus": "observed" if rows else "unknown",
        "interpretation": (
            "Results may include date-specific online booking observations. "
            "An absent result does not prove that no service operates."
        ),
        "trips": [
            {
                "id": row["id"],
                "operatorId": row["operator_id"],
                "operatorName": row["operator_name"],
                "lineId": row["line_id"],
                "patternId": row["pattern_id"],
                "patternName": row["pattern_name"],
                "serviceDate": row["service_date"],
                "departureAt": row["departure_at"],
                "approximateArrivalAt": row["approximate_arrival_at"],
                "sourceId": row["source_id"],
                "externalId": row["external_id"],
                "commercialState": row["commercial_state"],
                "scheduleScope": row["schedule_scope"],
                "fareAmount": row["fare_amount"],
                "fareCurrency": row["fare_currency"],
                "bookingUrl": row["booking_url"],
                "observedAt": row["observed_at"],
                "expiresAt": row["expires_at"],
            }
            for row in rows
        ],
    }
