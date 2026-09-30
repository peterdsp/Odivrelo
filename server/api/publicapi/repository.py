"""Every public read query, shaped straight into v1 contract objects.

All queries run against the compiled read-only public database. Each one repeats
the two gates the compiler already applied -- ``publication_state='published'``
and ``rights_status='allowed'`` on the owning source -- so a regression in the
compiler cannot turn into a rights leak in a public response. The duplication is
deliberate and is covered by tests.
"""
from __future__ import annotations

import json
import sqlite3
from datetime import date as date_cls
from typing import Any, Iterable, Sequence

from . import _staging  # noqa: F401  (installs the staging import path)
from . import attributes as attrs  # noqa: E402
from .brand import BRAND  # noqa: E402
from . import copy
from .errors import invalid_request, not_found  # noqa: E402
from .timeutil import (  # noqa: E402
    ATHENS,
    GtfsExportError,
    crosses_midnight,
    freshness,
    iso,
    now_utc,
    parse_instant,
    project_onto_service_date,
    weekday_column,
)
from poravia_ktel import ktel_api  # noqa: E402
from poravia_ktel.ktel_registry import normalize_stop_name, source_rows  # noqa: E402

PUBLISHED = "published"
ALLOWED = "allowed"

#: Human wording for a geometry confidence value, for the contract's
#: ``geometry.method`` field. The confidence itself stays machine readable.
GEOMETRY_METHOD = {
    "unverified": "no reviewed geometry; nothing is drawn",
    "ordered_stops_only": "straight segments between ordered published stops",
    "osm_candidate": "OpenStreetMap candidate alignment, awaiting review",
    "reviewed": "reviewed alignment confirmed against the operator",
    "rejected": "rejected alignment; not served",
}

#: A journey is reported as reviewed only when its schedule came from a source
#: of record. A booking observation is a candidate, however recent it is.
REVIEWED_SCOPES = frozenset({"operator_published", "nap_published"})


def register_functions(connection: sqlite3.Connection) -> None:
    """Expose accent-folding to SQL so search can page in the database."""
    connection.create_function(
        "norm_name", 1, lambda value: normalize_stop_name(value or ""), deterministic=True
    )


# --------------------------------------------------------------------------- #
# Release envelope
# --------------------------------------------------------------------------- #

def release_metadata(connection: sqlite3.Connection) -> dict[str, str | None]:
    return ktel_api.release_metadata(connection)


def envelope(connection: sqlite3.Connection, data_mode: str) -> dict[str, Any]:
    metadata = release_metadata(connection)
    return {
        "contractVersion": BRAND.contract_version,
        "releaseId": metadata["releaseId"],
        "publishedAt": metadata["publishedAt"],
        "dataMode": data_mode,
    }


# --------------------------------------------------------------------------- #
# Shared row helpers
# --------------------------------------------------------------------------- #

def _row_names(row: sqlite3.Row, attributes: dict[str, Any]) -> dict[str, str]:
    keys = row.keys()
    return attrs.names(
        attributes,
        name_en=row["name"] if "name" in keys else None,
        name_el=row["name_el"] if "name_el" in keys else None,
    )


def _operator_names(row: sqlite3.Row, attributes: dict[str, Any]) -> dict[str, str]:
    return attrs.names(
        attributes, name_en=row["name_en"], name_el=row["name_el"]
    )


def _attributes(row: sqlite3.Row, column: str = "public_attributes") -> dict[str, Any]:
    return attrs.parse(row[column] if column in row.keys() else None)


def _placeholders(values: Sequence[Any]) -> str:
    return ",".join("?" for _ in values)


# --------------------------------------------------------------------------- #
# Places
# --------------------------------------------------------------------------- #

_STOP_SELECT = """
    SELECT s.id, s.operator_id, s.stop_place_id, s.name, s.name_el, s.address,
           s.phone, s.latitude, s.longitude, s.coordinate_status,
           s.publication_state, s.source_id, s.external_id, s.first_seen_at,
           s.last_seen_at, s.public_attributes
    FROM ktel_stops s
    JOIN ktel_sources src ON src.id = s.source_id
    WHERE s.publication_state = ? AND src.rights_status = ?
"""

_PLACE_SELECT = """
    SELECT p.id, p.operator_id, p.name, p.name_el, p.publication_state,
           p.source_id, p.external_id, p.first_seen_at, p.last_seen_at,
           p.public_attributes
    FROM ktel_stop_places p
    JOIN ktel_sources src ON src.id = p.source_id
    WHERE p.publication_state = ? AND src.rights_status = ?
"""


def _stop_place_geometry(connection: sqlite3.Connection) -> dict[str, dict[str, Any]]:
    """Centroid and boarding-point count per terminal, from its published stops."""
    rows = connection.execute(
        """
        SELECT s.stop_place_id AS place_id,
               COUNT(*) AS boarding_points,
               AVG(s.latitude) AS latitude,
               AVG(s.longitude) AS longitude,
               SUM(CASE WHEN s.coordinate_status = 'valid' THEN 1 ELSE 0 END)
                 AS located
        FROM ktel_stops s
        JOIN ktel_sources src ON src.id = s.source_id
        WHERE s.publication_state = ? AND src.rights_status = ?
          AND s.stop_place_id IS NOT NULL
        GROUP BY s.stop_place_id
        """,
        (PUBLISHED, ALLOWED),
    ).fetchall()
    return {
        row["place_id"]: {
            "boardingPointCount": row["boarding_points"],
            "latitude": row["latitude"] if row["located"] else None,
            "longitude": row["longitude"] if row["located"] else None,
            "coordinateStatus": "valid" if row["located"] else "missing",
        }
        for row in rows
    }


def _served_stop_ids(connection: sqlite3.Connection) -> set[str]:
    return {
        row["stop_id"]
        for row in connection.execute(
            """
            SELECT DISTINCT st.stop_id
            FROM ktel_stop_times st
            JOIN ktel_trips t ON t.id = st.trip_id
            JOIN ktel_sources src ON src.id = t.source_id
            WHERE t.publication_state = ? AND src.rights_status = ?
            """,
            (PUBLISHED, ALLOWED),
        ).fetchall()
    }


def _coverage_state(served: bool, data_mode: str) -> str:
    if data_mode == "demo":
        return "demo"
    return "covered" if served else "not_covered"


def search_places(
    connection: sqlite3.Connection,
    *,
    query: str | None,
    limit: int,
    offset: int,
    data_mode: str,
) -> dict[str, Any]:
    """Case- and accent-insensitive place disambiguation across ``el`` and ``en``."""
    register_functions(connection)
    needle = f"%{normalize_stop_name(query)}%" if query and query.strip() else None

    place_clause = ""
    stop_clause = ""
    place_params: list[Any] = [PUBLISHED, ALLOWED]
    stop_params: list[Any] = [PUBLISHED, ALLOWED]
    if needle:
        place_clause = (
            " AND (norm_name(p.name) LIKE ? OR norm_name(COALESCE(p.name_el,'')) LIKE ?)"
        )
        place_params.extend((needle, needle))
        stop_clause = (
            " AND (norm_name(s.name) LIKE ? OR norm_name(COALESCE(s.name_el,'')) LIKE ?)"
        )
        stop_params.extend((needle, needle))

    geometry = _stop_place_geometry(connection)
    served = _served_stop_ids(connection)

    entries: list[dict[str, Any]] = []
    for row in connection.execute(_PLACE_SELECT + place_clause, place_params):
        attributes = _attributes(row)
        shape = geometry.get(row["id"], {})
        entries.append(
            {
                "id": row["id"],
                "kind": "stop_place",
                "name": _row_names(row, attributes),
                "parentId": None,
                "municipality": attrs.municipality(attributes),
                "latitude": shape.get("latitude"),
                "longitude": shape.get("longitude"),
                "coordinateStatus": shape.get("coordinateStatus", "missing"),
                "boardingPointCount": shape.get("boardingPointCount", 0),
                "operatorIds": [row["operator_id"]],
                "coverage": _coverage_state(
                    bool(shape.get("boardingPointCount")), data_mode
                ),
                "_sort": normalize_stop_name(row["name"]),
            }
        )
    for row in connection.execute(_STOP_SELECT + stop_clause, stop_params):
        attributes = _attributes(row)
        entries.append(
            {
                "id": row["id"],
                "kind": "stop",
                "name": _row_names(row, attributes),
                "parentId": row["stop_place_id"],
                "municipality": attrs.municipality(attributes),
                "latitude": row["latitude"],
                "longitude": row["longitude"],
                "coordinateStatus": row["coordinate_status"],
                "boardingPointCount": 0,
                "operatorIds": [row["operator_id"]],
                "coverage": _coverage_state(row["id"] in served, data_mode),
                "_sort": normalize_stop_name(row["name"]),
            }
        )

    entries.sort(key=lambda item: (item["_sort"], item["kind"], item["id"]))
    total = len(entries)
    page = entries[offset : offset + limit]
    for item in page:
        item.pop("_sort", None)
    for item in entries:
        item.pop("_sort", None)
    return {"places": page, "total": total}


def resolve_place(
    connection: sqlite3.Connection, place_id: str
) -> tuple[str, list[str]]:
    """Map a place id of either kind onto the stop ids it stands for."""
    place = connection.execute(
        _PLACE_SELECT + " AND p.id = ?", (PUBLISHED, ALLOWED, place_id)
    ).fetchone()
    if place:
        children = [
            row["id"]
            for row in connection.execute(
                _STOP_SELECT + " AND s.stop_place_id = ? ORDER BY s.id",
                (PUBLISHED, ALLOWED, place_id),
            ).fetchall()
        ]
        return "stop_place", children
    stop = connection.execute(
        _STOP_SELECT + " AND s.id = ?", (PUBLISHED, ALLOWED, place_id)
    ).fetchone()
    if stop:
        return "stop", [stop["id"]]
    raise not_found(f"no published place with id {place_id}", "id")


def stop_row(connection: sqlite3.Connection, stop_id: str) -> sqlite3.Row:
    row = connection.execute(
        _STOP_SELECT + " AND s.id = ?", (PUBLISHED, ALLOWED, stop_id)
    ).fetchone()
    if not row:
        raise not_found(f"no published stop with id {stop_id}", "id")
    return row


# --------------------------------------------------------------------------- #
# Journeys
# --------------------------------------------------------------------------- #

def _calendar_clause(service_date: str) -> str:
    """SQL that decides whether a trip runs on ``service_date``.

    A date-specific trip matches its own service date. A calendar-backed trip
    matches when an ``added`` exception names the date, or when the weekday is
    in the mask, the date is inside the validity window, and no ``removed``
    exception names it.
    """
    weekday = weekday_column(service_date)
    return f"""
        AND (
            (t.calendar_id IS NULL AND t.service_date = :service_date)
            OR (t.calendar_id IS NOT NULL AND EXISTS (
                SELECT 1 FROM ktel_service_calendars c
                WHERE c.id = t.calendar_id
                  AND c.publication_state = :published
                  AND (
                    EXISTS (
                      SELECT 1 FROM ktel_calendar_exceptions x
                      WHERE x.calendar_id = c.id
                        AND x.service_date = :service_date
                        AND x.exception_type = 'added'
                    )
                    OR (
                      c.{weekday} = 1
                      AND (c.valid_from IS NULL OR c.valid_from <= :service_date)
                      AND (c.valid_until IS NULL OR c.valid_until >= :service_date)
                      AND NOT EXISTS (
                        SELECT 1 FROM ktel_calendar_exceptions x
                        WHERE x.calendar_id = c.id
                          AND x.service_date = :service_date
                          AND x.exception_type = 'removed'
                      )
                    )
                  )
            ))
        )
    """


_TRIP_COLUMNS = """
    t.id, t.operator_id, t.line_id, t.pattern_id, t.calendar_id,
    t.service_date, t.departure_at, t.approximate_arrival_at, t.source_id,
    t.external_id, t.commercial_state, t.schedule_scope, t.publication_state,
    t.fare_amount, t.fare_currency, t.booking_url, t.observed_at,
    t.expires_at, t.public_attributes
"""


def _template_date(trip: sqlite3.Row) -> str:
    if trip["service_date"]:
        return str(trip["service_date"])
    return parse_instant(trip["departure_at"]).date().isoformat()


def _operator_index(connection: sqlite3.Connection) -> dict[str, dict[str, Any]]:
    rows = connection.execute(
        "SELECT id, federation_number, slug, name_en, name_el, "
        "official_site_url, directory_url, registry_verified_at, "
        "public_attributes FROM ktel_operators"
    ).fetchall()
    index: dict[str, dict[str, Any]] = {}
    for row in rows:
        attributes = _attributes(row)
        logo = attributes.get("logoAvailable")
        index[row["id"]] = {
            "id": row["id"],
            "name": _operator_names(row, attributes),
            "logoAvailable": bool(logo) if isinstance(logo, bool) else False,
            "federationNumber": row["federation_number"],
            "officialSiteUrl": row["official_site_url"],
            "directoryUrl": row["directory_url"],
            "verifiedAt": row["registry_verified_at"],
            "contact": attrs.contact(attributes),
        }
    return index


def _stop_index(connection: sqlite3.Connection) -> dict[str, sqlite3.Row]:
    return {
        row["id"]: row
        for row in connection.execute(_STOP_SELECT, (PUBLISHED, ALLOWED)).fetchall()
    }


def _stop_names(row: sqlite3.Row | None) -> dict[str, str]:
    if row is None:
        return {language: "" for language in BRAND.languages}
    return _row_names(row, _attributes(row))


def _stop_times(
    connection: sqlite3.Connection, trip_ids: Sequence[str]
) -> dict[str, list[sqlite3.Row]]:
    if not trip_ids:
        return {}
    rows = connection.execute(
        "SELECT trip_id, stop_id, stop_sequence, arrival_at, departure_at, "
        f"time_status FROM ktel_stop_times WHERE trip_id IN ({_placeholders(trip_ids)}) "
        "ORDER BY trip_id, stop_sequence",
        list(trip_ids),
    ).fetchall()
    grouped: dict[str, list[sqlite3.Row]] = {}
    for row in rows:
        grouped.setdefault(row["trip_id"], []).append(row)
    return grouped


def _pattern_rules(
    connection: sqlite3.Connection, pattern_ids: Iterable[str]
) -> dict[tuple[str, str], sqlite3.Row]:
    ids = [pattern_id for pattern_id in set(pattern_ids) if pattern_id]
    if not ids:
        return {}
    rows = connection.execute(
        "SELECT pattern_id, stop_id, pickup_type, dropoff_type "
        f"FROM ktel_pattern_stops WHERE pattern_id IN ({_placeholders(ids)})",
        ids,
    ).fetchall()
    return {(row["pattern_id"], row["stop_id"]): row for row in rows}


def _summary(
    trip: sqlite3.Row,
    *,
    service_date: str,
    times: list[sqlite3.Row],
    origin_index: int,
    destination_index: int,
    operators: dict[str, dict[str, Any]],
    stops: dict[str, sqlite3.Row],
    now: Any = None,
) -> dict[str, Any]:
    template = _template_date(trip)
    attributes = _attributes(trip)
    origin = times[origin_index]
    destination = times[destination_index]

    departure_source = origin["departure_at"] or origin["arrival_at"]
    arrival_source = destination["arrival_at"] or destination["departure_at"]
    departure = project_onto_service_date(departure_source, template, service_date)
    arrival = (
        project_onto_service_date(arrival_source, template, service_date)
        if arrival_source
        else None
    )
    operator = operators.get(
        trip["operator_id"],
        {
            "id": trip["operator_id"],
            "name": {language: trip["operator_id"] for language in BRAND.languages},
            "logoAvailable": False,
        },
    )
    duration = (
        int((arrival - departure).total_seconds() // 60) if arrival else None
    )
    fare = None
    if trip["fare_amount"] is not None:
        fare = {
            "amount": float(trip["fare_amount"]),
            "currency": trip["fare_currency"] or "EUR",
            "isIndicative": attrs.fare_is_indicative(attributes),
        }
    return {
        "id": trip["id"],
        "operator": {
            "id": operator["id"],
            "name": operator["name"],
            "logoAvailable": operator["logoAvailable"],
        },
        "departure": {
            "at": iso(departure),
            "stopId": origin["stop_id"],
            "stopName": _stop_names(stops.get(origin["stop_id"])),
            "quality": origin["time_status"],
        },
        "arrival": {
            "at": iso(arrival) if arrival else None,
            "stopId": destination["stop_id"],
            "stopName": _stop_names(stops.get(destination["stop_id"])),
            "quality": destination["time_status"],
        },
        "durationMinutes": duration,
        "intermediateStopCount": max(0, destination_index - origin_index - 1),
        "serviceDate": service_date,
        "crossesMidnight": crosses_midnight(departure, arrival),
        "positionQuality": attrs.position_quality(attributes),
        "fare": fare,
        "freshness": freshness(trip["observed_at"], now=now),
        "confidence": (
            "reviewed" if trip["schedule_scope"] in REVIEWED_SCOPES else "candidate"
        ),
        "purchase": _purchase_summary(attributes, trip["booking_url"]),
    }


def _purchase_summary(attributes: dict[str, Any], booking_url: str | None) -> dict[str, Any]:
    full = attrs.purchase(attributes, booking_url=booking_url)
    return {"kind": full["kind"], "url": full["url"], "label": full["label"]}


def _accessible(stop: sqlite3.Row | None) -> bool:
    if stop is None:
        return False
    boarding = attrs.boarding_point(_attributes(stop))
    return bool(boarding["stepFree"]) and bool(boarding["reviewedAt"])


def search_journeys(
    connection: sqlite3.Connection,
    *,
    origin_id: str,
    destination_id: str,
    service_date: str,
    accessible: bool,
    limit: int,
    offset: int,
    data_mode: str,
    now: Any = None,
) -> dict[str, Any]:
    if origin_id == destination_id:
        return {
            "query": {
                "originId": origin_id,
                "destinationId": destination_id,
                "date": service_date,
            },
            "coverage": "demo" if data_mode == "demo" else "not_covered",
            "results": [],
            "unavailableReason": "origin_equals_destination",
        }

    _, origin_stops = resolve_place(connection, origin_id)
    _, destination_stops = resolve_place(connection, destination_id)
    query_block = {
        "originId": origin_id,
        "destinationId": destination_id,
        "date": service_date,
    }
    if not origin_stops or not destination_stops:
        return {
            "query": query_block,
            "coverage": "demo" if data_mode == "demo" else "not_covered",
            "results": [],
            "unavailableReason": "outside_coverage",
        }

    parameters: dict[str, Any] = {
        "service_date": service_date,
        "published": PUBLISHED,
        "allowed": ALLOWED,
    }
    origin_names = []
    for index, stop_id in enumerate(origin_stops):
        key = f"origin_{index}"
        parameters[key] = stop_id
        origin_names.append(f":{key}")
    destination_names = []
    for index, stop_id in enumerate(destination_stops):
        key = f"destination_{index}"
        parameters[key] = stop_id
        destination_names.append(f":{key}")

    sql = f"""
        SELECT DISTINCT {_TRIP_COLUMNS}
        FROM ktel_trips t
        JOIN ktel_sources src ON src.id = t.source_id
        JOIN ktel_stop_times o
          ON o.trip_id = t.id AND o.stop_id IN ({','.join(origin_names)})
        JOIN ktel_stop_times d
          ON d.trip_id = t.id AND d.stop_id IN ({','.join(destination_names)})
         AND d.stop_sequence > o.stop_sequence
        WHERE t.publication_state = :published
          AND src.rights_status = :allowed
        {_calendar_clause(service_date)}
        ORDER BY t.departure_at, t.id
    """
    trips = connection.execute(sql, parameters).fetchall()
    if not trips:
        return {
            "query": query_block,
            "coverage": "demo" if data_mode == "demo" else "not_covered",
            "results": [],
            "unavailableReason": "no_service_on_date",
        }

    operators = _operator_index(connection)
    stops = _stop_index(connection)
    times = _stop_times(connection, [trip["id"] for trip in trips])
    origin_set = set(origin_stops)
    destination_set = set(destination_stops)

    results: list[dict[str, Any]] = []
    for trip in trips:
        sequence = times.get(trip["id"], [])
        pair = _first_pair(sequence, origin_set, destination_set)
        if pair is None:
            continue
        origin_index, destination_index = pair
        if accessible and not _accessible(
            stops.get(sequence[origin_index]["stop_id"])
        ):
            continue
        try:
            results.append(
                _summary(
                    trip,
                    service_date=service_date,
                    times=sequence,
                    origin_index=origin_index,
                    destination_index=destination_index,
                    operators=operators,
                    stops=stops,
                    now=now,
                )
            )
        except GtfsExportError:
            # A stop time that precedes its own service day is a release defect.
            # Drop the journey rather than publish a negative duration.
            continue

    results.sort(key=lambda item: (item["departure"]["at"], item["id"]))
    page = results[offset : offset + limit]
    reason = None
    if not page:
        reason = "no_service_on_date"
    return {
        "query": query_block,
        "coverage": "demo" if data_mode == "demo" else ("covered" if page else "partial"),
        "results": page,
        "unavailableReason": reason,
    }


def _first_pair(
    sequence: list[sqlite3.Row],
    origin_stops: set[str],
    destination_stops: set[str],
) -> tuple[int, int] | None:
    """Earliest boardable origin and the first alightable destination after it."""
    for origin_index, row in enumerate(sequence):
        if row["stop_id"] not in origin_stops:
            continue
        for destination_index in range(origin_index + 1, len(sequence)):
            if sequence[destination_index]["stop_id"] in destination_stops:
                return origin_index, destination_index
    return None


def trip_row(
    connection: sqlite3.Connection, trip_id: str, service_date: str | None
) -> tuple[sqlite3.Row, str]:
    """Fetch a published journey and the service date it is being viewed on."""
    trip = connection.execute(
        f"""
        SELECT {_TRIP_COLUMNS}
        FROM ktel_trips t
        JOIN ktel_sources src ON src.id = t.source_id
        WHERE t.id = ? AND t.publication_state = ? AND src.rights_status = ?
        """,
        (trip_id, PUBLISHED, ALLOWED),
    ).fetchone()
    if not trip:
        raise not_found(f"no published journey with id {trip_id}", "id")
    resolved = service_date or _template_date(trip)
    runs = connection.execute(
        f"""
        SELECT 1 FROM ktel_trips t
        WHERE t.id = :trip_id AND t.publication_state = :published
        {_calendar_clause(resolved)}
        """,
        {"trip_id": trip_id, "published": PUBLISHED, "service_date": resolved},
    ).fetchone()
    if not runs:
        raise not_found(
            f"journey {trip_id} does not run on {resolved}", "date"
        )
    return trip, resolved


def journey_detail(
    connection: sqlite3.Connection,
    *,
    trip_id: str,
    service_date: str | None,
    data_mode: str,
    now: Any = None,
) -> dict[str, Any]:
    trip, resolved = trip_row(connection, trip_id, service_date)
    template = _template_date(trip)
    attributes = _attributes(trip)
    operators = _operator_index(connection)
    stops = _stop_index(connection)
    sequence = _stop_times(connection, [trip["id"]]).get(trip["id"], [])
    if not sequence:
        raise not_found(f"journey {trip_id} has no published stop times", "id")

    rules = _pattern_rules(connection, [trip["pattern_id"]])
    summary = _summary(
        trip,
        service_date=resolved,
        times=sequence,
        origin_index=0,
        destination_index=len(sequence) - 1,
        operators=operators,
        stops=stops,
        now=now,
    )

    stop_list: list[dict[str, Any]] = []
    for row in sequence:
        arrival = row["arrival_at"]
        departure = row["departure_at"]
        rule = rules.get((trip["pattern_id"], row["stop_id"]))
        stop_list.append(
            {
                "stopId": row["stop_id"],
                "sequence": row["stop_sequence"],
                "name": _stop_names(stops.get(row["stop_id"])),
                "arrivalAt": (
                    iso(project_onto_service_date(arrival, template, resolved))
                    if arrival
                    else None
                ),
                "departureAt": (
                    iso(project_onto_service_date(departure, template, resolved))
                    if departure
                    else None
                ),
                "timeQuality": row["time_status"],
                "pickup": rule["pickup_type"] if rule else "unknown",
                "dropoff": rule["dropoff_type"] if rule else "unknown",
            }
        )

    boarding_stop = stops.get(sequence[0]["stop_id"])
    boarding = _boarding_point(connection, boarding_stop)
    journey = dict(summary)
    journey["boardingPoint"] = boarding
    journey["stops"] = stop_list
    journey["geometry"] = _geometry(connection, trip["pattern_id"])
    journey["restrictions"] = attrs.restrictions(attributes)
    journey["provenance"] = _provenance(connection, trip["source_id"], trip["observed_at"])
    journey["purchase"] = attrs.purchase(attributes, booking_url=trip["booking_url"])
    journey["correctionUrl"] = BRAND.correction_url("journey", trip["id"])
    return {"journey": journey}


def _boarding_point(
    connection: sqlite3.Connection, stop: sqlite3.Row | None
) -> dict[str, Any] | None:
    if stop is None:
        return None
    attributes = _attributes(stop)
    boarding = attrs.boarding_point(attributes)
    terminal_name = None
    if stop["stop_place_id"]:
        place = connection.execute(
            _PLACE_SELECT + " AND p.id = ?",
            (PUBLISHED, ALLOWED, stop["stop_place_id"]),
        ).fetchone()
        if place:
            terminal_name = _row_names(place, _attributes(place))
    return {
        "stopId": stop["id"],
        "name": _row_names(stop, attributes),
        "terminalName": terminal_name,
        "bay": boarding["bay"],
        "latitude": stop["latitude"],
        "longitude": stop["longitude"],
        "instructions": boarding["instructions"],
        "reviewState": stop["publication_state"],
        "reviewedAt": boarding["reviewedAt"] or stop["last_seen_at"],
        "stepFree": boarding["stepFree"],
    }


def _geometry(
    connection: sqlite3.Connection, pattern_id: str | None
) -> dict[str, Any] | None:
    if not pattern_id:
        return None
    row = connection.execute(
        """
        SELECT p.geometry_status, p.geometry_geojson, src.label AS source_label
        FROM ktel_journey_patterns p
        JOIN ktel_sources src ON src.id = p.source_id
        WHERE p.id = ? AND p.publication_state = ? AND src.rights_status = ?
        """,
        (pattern_id, PUBLISHED, ALLOWED),
    ).fetchone()
    if not row or not row["geometry_geojson"]:
        return None
    try:
        geojson = json.loads(row["geometry_geojson"])
    except (TypeError, ValueError):
        return None
    if not isinstance(geojson, dict) or geojson.get("type") != "LineString":
        return None
    confidence = row["geometry_status"]
    if confidence in {"unverified", "rejected"}:
        return None
    return {
        "type": "LineString",
        "coordinates": geojson.get("coordinates", []),
        "confidence": confidence,
        "method": GEOMETRY_METHOD.get(confidence, confidence),
        "attribution": row["source_label"],
    }


def _provenance(
    connection: sqlite3.Connection, source_id: str, retrieved_at: str | None
) -> list[dict[str, Any]]:
    row = connection.execute(
        "SELECT id, label, base_url, rights_status, license_id, terms_status "
        "FROM ktel_sources WHERE id = ?",
        (source_id,),
    ).fetchone()
    if not row:
        return []
    return [
        {
            "sourceId": row["id"],
            "sourceName": row["label"],
            "sourceUrl": row["base_url"],
            "retrievedAt": retrieved_at,
            "rightsStatus": row["rights_status"],
            "licence": row["license_id"] or row["terms_status"],
        }
    ]


# --------------------------------------------------------------------------- #
# Operators
# --------------------------------------------------------------------------- #

def _operator_counts(connection: sqlite3.Connection) -> dict[str, dict[str, int]]:
    counts: dict[str, dict[str, int]] = {}
    for key, sql in (
        (
            "stopCount",
            "SELECT s.operator_id AS operator_id, COUNT(*) AS n FROM ktel_stops s "
            "JOIN ktel_sources src ON src.id = s.source_id "
            "WHERE s.publication_state = ? AND src.rights_status = ? "
            "GROUP BY s.operator_id",
        ),
        (
            "routeCount",
            "SELECT p.operator_id AS operator_id, COUNT(*) AS n "
            "FROM ktel_journey_patterns p "
            "JOIN ktel_sources src ON src.id = p.source_id "
            "WHERE p.publication_state = ? AND src.rights_status = ? "
            "GROUP BY p.operator_id",
        ),
        (
            "journeyCount",
            "SELECT t.operator_id AS operator_id, COUNT(*) AS n FROM ktel_trips t "
            "JOIN ktel_sources src ON src.id = t.source_id "
            "WHERE t.publication_state = ? AND src.rights_status = ? "
            "GROUP BY t.operator_id",
        ),
    ):
        for row in connection.execute(sql, (PUBLISHED, ALLOWED)).fetchall():
            counts.setdefault(row["operator_id"], {})[key] = row["n"]
    return counts


def _operator_sources(
    connection: sqlite3.Connection, operator_id: str
) -> list[dict[str, Any]]:
    rows = connection.execute(
        """
        SELECT DISTINCT src.id, src.label, src.base_url, src.rights_status,
               src.license_id, src.terms_status
        FROM ktel_sources src
        WHERE src.id IN (
            SELECT source_id FROM ktel_stops
            WHERE operator_id = :operator_id AND publication_state = :published
            UNION
            SELECT source_id FROM ktel_trips
            WHERE operator_id = :operator_id AND publication_state = :published
            UNION
            SELECT source_id FROM ktel_stop_places
            WHERE operator_id = :operator_id AND publication_state = :published
        )
        AND src.rights_status = :allowed
        ORDER BY src.id
        """,
        {"operator_id": operator_id, "published": PUBLISHED, "allowed": ALLOWED},
    ).fetchall()
    return [
        {
            "sourceId": row["id"],
            "sourceName": row["label"],
            "sourceUrl": row["base_url"],
            "retrievedAt": None,
            "rightsStatus": row["rights_status"],
            "licence": row["license_id"] or row["terms_status"],
        }
        for row in rows
    ]


def _operator_object(
    connection: sqlite3.Connection,
    operator: dict[str, Any],
    counts: dict[str, int],
    data_mode: str,
) -> dict[str, Any]:
    return {
        "id": operator["id"],
        "name": operator["name"],
        "logoAvailable": operator["logoAvailable"],
        "federationNumber": operator["federationNumber"],
        "officialSiteUrl": operator["officialSiteUrl"],
        "directoryUrl": operator["directoryUrl"],
        "contact": operator["contact"],
        "coverage": {
            "state": "demo" if data_mode == "demo" else "covered",
            "routeCount": counts.get("routeCount", 0),
            "stopCount": counts.get("stopCount", 0),
        },
        "sources": _operator_sources(connection, operator["id"]),
        "verifiedAt": operator["verifiedAt"],
        "correctionUrl": BRAND.correction_url("operator", operator["id"]),
    }


def list_operators(
    connection: sqlite3.Connection, *, limit: int, offset: int, data_mode: str
) -> dict[str, Any]:
    """Only operators that actually carry published, rights-cleared content.

    The registry holds every federation member. Listing all of them here would
    advertise coverage that does not exist, which is the failure mode the whole
    review pipeline exists to prevent.
    """
    operators = _operator_index(connection)
    counts = _operator_counts(connection)
    present = sorted(
        (operator_id for operator_id in counts if operator_id in operators),
        key=lambda operator_id: operators[operator_id]["name"]["en"],
    )
    page = present[offset : offset + limit]
    return {
        "operators": [
            _operator_object(connection, operators[operator_id], counts[operator_id], data_mode)
            for operator_id in page
        ],
        "total": len(present),
    }


def operator_detail(
    connection: sqlite3.Connection, operator_id: str, *, data_mode: str
) -> dict[str, Any]:
    operators = _operator_index(connection)
    counts = _operator_counts(connection)
    if operator_id not in operators or operator_id not in counts:
        raise not_found(f"no operator with published content for id {operator_id}", "id")
    return {
        "operator": _operator_object(
            connection, operators[operator_id], counts[operator_id], data_mode
        )
    }


# --------------------------------------------------------------------------- #
# Stops
# --------------------------------------------------------------------------- #

def stop_detail(
    connection: sqlite3.Connection,
    *,
    stop_id: str,
    service_date: str,
    data_mode: str,
    limit: int,
    now: Any = None,
) -> dict[str, Any]:
    stop = stop_row(connection, stop_id)
    attributes = _attributes(stop)
    operators = _operator_index(connection)
    stops = _stop_index(connection)

    terminal = None
    boarding_points: list[dict[str, Any]] = []
    if stop["stop_place_id"]:
        place = connection.execute(
            _PLACE_SELECT + " AND p.id = ?",
            (PUBLISHED, ALLOWED, stop["stop_place_id"]),
        ).fetchone()
        if place:
            terminal = {
                "id": place["id"],
                "name": _row_names(place, _attributes(place)),
            }
        for sibling in connection.execute(
            _STOP_SELECT + " AND s.stop_place_id = ? ORDER BY s.name, s.id",
            (PUBLISHED, ALLOWED, stop["stop_place_id"]),
        ).fetchall():
            sibling_attributes = _attributes(sibling)
            boarding = attrs.boarding_point(sibling_attributes)
            boarding_points.append(
                {
                    "stopId": sibling["id"],
                    "name": _row_names(sibling, sibling_attributes),
                    "bay": boarding["bay"],
                    "stepFree": boarding["stepFree"],
                    "reviewedAt": boarding["reviewedAt"] or sibling["last_seen_at"],
                    "latitude": sibling["latitude"],
                    "longitude": sibling["longitude"],
                }
            )

    serving_operators = sorted(
        {
            row["operator_id"]
            for row in connection.execute(
                """
                SELECT DISTINCT t.operator_id
                FROM ktel_trips t
                JOIN ktel_stop_times st ON st.trip_id = t.id
                JOIN ktel_sources src ON src.id = t.source_id
                WHERE st.stop_id = ? AND t.publication_state = ?
                  AND src.rights_status = ?
                """,
                (stop_id, PUBLISHED, ALLOWED),
            ).fetchall()
        }
    )

    departures = _next_departures(
        connection,
        stop_id=stop_id,
        service_date=service_date,
        operators=operators,
        stops=stops,
        limit=limit,
        now=now,
    )
    boarding = attrs.boarding_point(attributes)
    return {
        "stop": {
            "id": stop["id"],
            "kind": "stop",
            "name": _row_names(stop, attributes),
            "municipality": attrs.municipality(attributes),
            "latitude": stop["latitude"],
            "longitude": stop["longitude"],
            "coordinateStatus": stop["coordinate_status"],
            "address": stop["address"],
            "phone": stop["phone"],
            "bay": boarding["bay"],
            "stepFree": boarding["stepFree"],
            "instructions": boarding["instructions"],
            "terminal": terminal,
            "boardingPoints": boarding_points,
            "operatorIds": serving_operators,
            "operators": [
                {
                    "id": operator_id,
                    "name": operators[operator_id]["name"],
                    "logoAvailable": operators[operator_id]["logoAvailable"],
                }
                for operator_id in serving_operators
                if operator_id in operators
            ],
            "coverage": _coverage_state(bool(serving_operators), data_mode),
            "serviceDate": service_date,
            "departures": departures,
            "provenance": _provenance(
                connection, stop["source_id"], stop["last_seen_at"]
            ),
            "correctionUrl": BRAND.correction_url("stop", stop["id"]),
        }
    }


def _next_departures(
    connection: sqlite3.Connection,
    *,
    stop_id: str,
    service_date: str,
    operators: dict[str, dict[str, Any]],
    stops: dict[str, sqlite3.Row],
    limit: int,
    now: Any = None,
) -> list[dict[str, Any]]:
    trips = connection.execute(
        f"""
        SELECT DISTINCT {_TRIP_COLUMNS}
        FROM ktel_trips t
        JOIN ktel_sources src ON src.id = t.source_id
        JOIN ktel_stop_times st ON st.trip_id = t.id AND st.stop_id = :stop_id
        WHERE t.publication_state = :published AND src.rights_status = :allowed
        {_calendar_clause(service_date)}
        ORDER BY t.departure_at, t.id
        """,
        {
            "stop_id": stop_id,
            "published": PUBLISHED,
            "allowed": ALLOWED,
            "service_date": service_date,
        },
    ).fetchall()
    times = _stop_times(connection, [trip["id"] for trip in trips])
    departures: list[dict[str, Any]] = []
    for trip in trips:
        sequence = times.get(trip["id"], [])
        index = next(
            (
                position
                for position, row in enumerate(sequence)
                if row["stop_id"] == stop_id
            ),
            None,
        )
        if index is None or index == len(sequence) - 1:
            continue
        try:
            summary = _summary(
                trip,
                service_date=service_date,
                times=sequence,
                origin_index=index,
                destination_index=len(sequence) - 1,
                operators=operators,
                stops=stops,
                now=now,
            )
        except GtfsExportError:
            continue
        departures.append(summary)
    departures.sort(key=lambda item: (item["departure"]["at"], item["id"]))
    return departures[:limit]


# --------------------------------------------------------------------------- #
# Coverage and sources
# --------------------------------------------------------------------------- #

def coverage(
    connection: sqlite3.Connection, *, data_mode: str, now: Any = None
) -> dict[str, Any]:
    counts = _operator_counts(connection)
    operators = _operator_index(connection)
    span = connection.execute(
        """
        SELECT MIN(t.service_date) AS first_date, MAX(t.service_date) AS last_date,
               MAX(t.observed_at) AS observed_at, COUNT(*) AS journeys
        FROM ktel_trips t
        JOIN ktel_sources src ON src.id = t.source_id
        WHERE t.publication_state = ? AND src.rights_status = ?
        """,
        (PUBLISHED, ALLOWED),
    ).fetchone()
    stop_total = sum(item.get("stopCount", 0) for item in counts.values())
    route_total = sum(item.get("routeCount", 0) for item in counts.values())
    state = "demo" if data_mode == "demo" else ("covered" if counts else "not_covered")
    absence = ktel_api.coverage_payload(connection).get("journeyAbsenceSemantics", {})
    # Localised, because a traveller reads this sentence. See publicapi/copy.py.
    note = copy.coverage_note(data_mode)
    return {
        "coverage": {
            "state": state,
            "operatorCount": len(counts),
            "corridorCount": route_total,
            "stopCount": stop_total,
            "journeyCount": span["journeys"] or 0,
            "serviceDates": {"from": span["first_date"], "to": span["last_date"]},
            "note": note,
            "notCovered": _not_covered(data_mode),
            "freshness": freshness(span["observed_at"], now=now),
            "absenceSemantics": absence,
            "operators": [
                {
                    "operatorId": operator_id,
                    "name": operators.get(operator_id, {}).get(
                        "name", {language: operator_id for language in BRAND.languages}
                    ),
                    "state": state,
                    "routeCount": item.get("routeCount", 0),
                    "stopCount": item.get("stopCount", 0),
                    "journeyCount": item.get("journeyCount", 0),
                }
                for operator_id, item in sorted(counts.items())
            ],
        }
    }


def _not_covered(data_mode: str) -> list[dict[str, str]]:
    return copy.not_covered(data_mode)


def sources(
    connection: sqlite3.Connection, *, limit: int, offset: int
) -> dict[str, Any]:
    rows = source_rows(connection)
    page = rows[offset : offset + limit]
    return {
        "sources": [
            {
                "id": row["id"],
                "name": row["label"],
                "url": row["baseUrl"],
                "sourceKind": row["sourceKind"],
                "authorityLevel": row["authorityLevel"],
                "rightsStatus": row["rightsStatus"],
                "licence": row["licenseId"] or row["termsStatus"],
                "termsUrl": row["termsUrl"],
                "termsStatus": row["termsStatus"],
                "refreshPolicy": row["refreshPolicy"],
                "legalReviewedAt": row["legalReviewedAt"],
                "enabled": row["enabled"],
            }
            for row in page
        ],
        "total": len(rows),
    }


def attribution(connection: sqlite3.Connection) -> list[dict[str, Any]]:
    return [
        {
            "name": row["label"],
            "url": row["baseUrl"],
            "licence": row["licenseId"] or row["termsStatus"],
        }
        for row in source_rows(connection)
        if row["rightsStatus"] == ALLOWED
    ]


def meta(
    connection: sqlite3.Connection,
    *,
    data_mode: str,
    commit: str,
    built_at: str,
) -> dict[str, Any]:
    """The ``/v1/meta`` body, shared by the endpoint and the offline pack.

    Both must be byte identical, so there is one function rather than two that
    look alike.
    """
    headline = coverage(connection, data_mode=data_mode)["coverage"]
    return {
        "product": {
            "name": BRAND.name,
            "version": BRAND.version,
            "commit": commit,
            "builtAt": built_at,
        },
        "languages": list(BRAND.languages),
        "coverage": {
            "state": headline["state"],
            "operatorCount": headline["operatorCount"],
            "corridorCount": headline["corridorCount"],
            "note": headline["note"],
        },
        "offlineManifestUrl": "/v1/offline/manifest",
        "gtfsUrl": "/v1/gtfs",
        "attribution": attribution(connection),
    }


# --------------------------------------------------------------------------- #
# Whole-release enumeration, for the offline packs
# --------------------------------------------------------------------------- #

#: Upper bound on rows in one pack. Packs are split by logical payload rather
#: than paged, so this is a guard against an unbounded release, not a window.
PACK_ROW_LIMIT = 50_000


def all_operator_ids(connection: sqlite3.Connection) -> list[str]:
    operators = _operator_index(connection)
    counts = _operator_counts(connection)
    return sorted(
        operator_id for operator_id in counts if operator_id in operators
    )


def all_stop_ids(connection: sqlite3.Connection) -> list[str]:
    return sorted(
        row["id"]
        for row in connection.execute(_STOP_SELECT, (PUBLISHED, ALLOWED)).fetchall()
    )


def pack_service_dates(connection: sqlite3.Connection) -> list[str]:
    """Service dates this release materialises a journey pack for.

    A date-specific journey contributes its own service date. A calendar-backed
    journey contributes the template date it is stored against, plus any date an
    ``added`` exception names explicitly. Further recurrences of a weekday
    calendar are resolved on request by the API and are deliberately not
    materialised, because a year of packs per calendar is not a release.
    """
    dates = {
        row["service_date"]
        for row in connection.execute(
            "SELECT DISTINCT t.service_date AS service_date FROM ktel_trips t "
            "JOIN ktel_sources src ON src.id = t.source_id "
            "WHERE t.publication_state = ? AND src.rights_status = ? "
            "AND t.service_date IS NOT NULL",
            (PUBLISHED, ALLOWED),
        ).fetchall()
    }
    dates |= {
        row["service_date"]
        for row in connection.execute(
            "SELECT DISTINCT x.service_date AS service_date "
            "FROM ktel_calendar_exceptions x "
            "JOIN ktel_service_calendars c ON c.id = x.calendar_id "
            "JOIN ktel_trips t ON t.calendar_id = c.id "
            "JOIN ktel_sources src ON src.id = t.source_id "
            "WHERE x.exception_type = 'added' "
            "AND c.publication_state = ? AND t.publication_state = ? "
            "AND src.rights_status = ?",
            (PUBLISHED, PUBLISHED, ALLOWED),
        ).fetchall()
    }
    return sorted(date for date in dates if date)


def journey_ids_on_date(
    connection: sqlite3.Connection, service_date: str
) -> list[str]:
    """Every published journey that runs on ``service_date``."""
    rows = connection.execute(
        f"""
        SELECT t.id AS id
        FROM ktel_trips t
        JOIN ktel_sources src ON src.id = t.source_id
        WHERE t.publication_state = :published AND src.rights_status = :allowed
        {_calendar_clause(service_date)}
        ORDER BY t.departure_at, t.id
        """,
        {
            "published": PUBLISHED,
            "allowed": ALLOWED,
            "service_date": service_date,
        },
    ).fetchall()
    return [row["id"] for row in rows]


def journey_summary(
    connection: sqlite3.Connection,
    *,
    trip_id: str,
    service_date: str,
    now: Any = None,
) -> dict[str, Any] | None:
    """One journey end to end, in the same shape a search result carries.

    ``/v1/journeys`` reports a leg between two requested places. A pack has no
    requested pair, so the whole journey is reported: its first stop to its last.
    Searching that exact pair through the endpoint returns this same object.
    """
    trip, resolved = trip_row(connection, trip_id, service_date)
    sequence = _stop_times(connection, [trip["id"]]).get(trip["id"], [])
    if len(sequence) < 2:
        return None
    try:
        return _summary(
            trip,
            service_date=resolved,
            times=sequence,
            origin_index=0,
            destination_index=len(sequence) - 1,
            operators=_operator_index(connection),
            stops=_stop_index(connection),
            now=now,
        )
    except GtfsExportError:
        return None


def today_in_athens() -> str:
    return now_utc().astimezone(ATHENS).date().isoformat()


def require_service_date(value: str | None) -> str:
    if value is None:
        return today_in_athens()
    try:
        parsed = date_cls.fromisoformat(value)
    except (TypeError, ValueError) as error:
        raise invalid_request(
            f"{value!r} is not a service date in YYYY-MM-DD form", "date"
        ) from error
    if parsed.isoformat() != value:
        raise invalid_request(
            f"{value!r} is not a service date in YYYY-MM-DD form", "date"
        )
    return value
