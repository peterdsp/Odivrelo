"""Deterministic GTFS export from the compiled read-only public database.

The exporter never reads the ingestion database. Only rows that the publication
gate already copied into the public artifact can reach a feed, so rights and
review state are enforced upstream in :mod:`ktel_publish`.

Service-date semantics follow the GTFS specification: a stop time is expressed
as an offset from *noon minus twelve hours* on the service date in
``Europe/Athens``. That definition is what makes daylight-saving transitions and
past-midnight departures (``25:10:00``) come out correct, so it is implemented
once here and reused by every client rather than re-derived per platform.
"""
from __future__ import annotations

import csv
import io
import sqlite3
import zipfile
from datetime import datetime, date, timedelta
from typing import Any, Iterable, Sequence
from zoneinfo import ZoneInfo

from . import branding

ATHENS = ZoneInfo("Europe/Athens")

#: Fixed member order so a zip built twice from the same release is identical.
FEED_FILES = (
    "agency.txt",
    "stops.txt",
    "routes.txt",
    "trips.txt",
    "stop_times.txt",
    "calendar.txt",
    "calendar_dates.txt",
    "feed_info.txt",
    "attributions.txt",
)


class GtfsExportError(ValueError):
    """Raised when the compiled release cannot produce a valid feed."""


def parse_instant(value: str) -> datetime:
    """Parse an ISO-8601 instant and express it in ``Europe/Athens``."""
    text = value.strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    parsed = datetime.fromisoformat(text)
    if parsed.tzinfo is None:
        # A naive timestamp in this dataset is a local Greek wall clock.
        parsed = parsed.replace(tzinfo=ATHENS)
    return parsed.astimezone(ATHENS)


def service_day_origin(service_date: str) -> datetime:
    """Return noon-minus-twelve-hours on ``service_date`` in Athens."""
    day = date.fromisoformat(service_date)
    noon = datetime(day.year, day.month, day.day, 12, 0, 0, tzinfo=ATHENS)
    return noon - timedelta(hours=12)


def gtfs_time(value: str | None, service_date: str) -> str:
    """Format an instant as a GTFS time, allowing hours at or beyond 24."""
    if not value:
        return ""
    seconds = int(
        (parse_instant(value) - service_day_origin(service_date)).total_seconds()
    )
    if seconds < 0:
        raise GtfsExportError(
            f"stop time {value} precedes service date {service_date}"
        )
    hours, remainder = divmod(seconds, 3600)
    minutes, secs = divmod(remainder, 60)
    return f"{hours:02d}:{minutes:02d}:{secs:02d}"


def gtfs_date(value: str | None) -> str:
    if not value:
        return ""
    return date.fromisoformat(value).strftime("%Y%m%d")


def _rows(conn: sqlite3.Connection, sql: str) -> list[dict[str, Any]]:
    return [dict(row) for row in conn.execute(sql).fetchall()]


def _csv(header: Sequence[str], rows: Iterable[Sequence[Any]]) -> bytes:
    buffer = io.StringIO(newline="")
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(header)
    for row in rows:
        writer.writerow(["" if cell is None else cell for cell in row])
    return buffer.getvalue().encode("utf-8")


def _pickup_code(value: str | None) -> int:
    return {"allowed": 0, "not_allowed": 1, "request": 3}.get(value or "", 0)


def build_feed(conn: sqlite3.Connection, *, release_id: str) -> dict[str, bytes]:
    """Build every GTFS member from an open read-only public connection."""
    operators = _rows(
        conn,
        "SELECT id, name_en, name_el, official_site_url, directory_url "
        "FROM ktel_operators ORDER BY id",
    )
    stops = _rows(
        conn,
        "SELECT id, name, name_el, latitude, longitude, stop_place_id, "
        "coordinate_status FROM ktel_stops "
        "WHERE publication_state='published' ORDER BY id",
    )
    lines = _rows(
        conn,
        "SELECT id, operator_id, public_code, name FROM ktel_lines "
        "WHERE publication_state='published' ORDER BY id",
    )
    calendars = _rows(
        conn,
        "SELECT * FROM ktel_service_calendars "
        "WHERE publication_state='published' ORDER BY id",
    )
    exceptions = _rows(
        conn,
        "SELECT * FROM ktel_calendar_exceptions ORDER BY calendar_id, service_date",
    )
    trips = _rows(
        conn,
        "SELECT * FROM ktel_trips WHERE publication_state='published' ORDER BY id",
    )
    stop_times = _rows(
        conn,
        "SELECT st.*, t.service_date, t.line_id FROM ktel_stop_times st "
        "JOIN ktel_trips t ON t.id = st.trip_id "
        "WHERE t.publication_state='published' "
        "ORDER BY st.trip_id, st.stop_sequence",
    )
    pattern_stops = {
        (row["pattern_id"], row["stop_id"]): row
        for row in _rows(
            conn,
            "SELECT pattern_id, stop_id, pickup_type, dropoff_type "
            "FROM ktel_pattern_stops",
        )
    }

    published_stop_ids = {row["id"] for row in stops}
    line_ids = {row["id"] for row in lines}
    calendar_ids = {row["id"] for row in calendars}

    # A GTFS feed must be referentially whole. Anything the publication gate
    # left dangling is a release defect, not something to paper over silently.
    exported_trips = []
    synthetic_calendars: dict[str, str] = {}
    for trip in trips:
        if trip["line_id"] not in line_ids:
            raise GtfsExportError(
                f"trip {trip['id']} references unpublished route {trip['line_id']}"
            )
        service_date = trip["service_date"]
        calendar_id = trip["calendar_id"]
        if calendar_id and calendar_id not in calendar_ids:
            raise GtfsExportError(
                f"trip {trip['id']} references unpublished calendar {calendar_id}"
            )
        if not calendar_id:
            if not service_date:
                raise GtfsExportError(
                    f"trip {trip['id']} has neither a calendar nor a service date"
                )
            # Date-specific bookable observations are exactly one service day.
            calendar_id = f"sd_{service_date.replace('-', '')}"
            synthetic_calendars[calendar_id] = service_date
        exported_trips.append({**trip, "gtfs_service_id": calendar_id})

    trip_service_date = {
        trip["id"]: trip["service_date"] for trip in exported_trips
    }
    trip_pattern = {trip["id"]: trip["pattern_id"] for trip in exported_trips}

    exported_stop_times = []
    for row in stop_times:
        if row["trip_id"] not in trip_service_date:
            continue
        if row["stop_id"] not in published_stop_ids:
            raise GtfsExportError(
                f"trip {row['trip_id']} stops at unpublished stop {row['stop_id']}"
            )
        exported_stop_times.append(row)

    members: dict[str, bytes] = {}

    members["agency.txt"] = _csv(
        ("agency_id", "agency_name", "agency_url", "agency_timezone", "agency_lang"),
        [
            (
                operator["id"],
                operator["name_en"],
                operator["official_site_url"]
                or operator["directory_url"]
                or branding.PRODUCT_URL,
                "Europe/Athens",
                "el",
            )
            for operator in operators
            if operator["id"] in {line["operator_id"] for line in lines}
        ],
    )

    members["stops.txt"] = _csv(
        (
            "stop_id",
            "stop_name",
            "stop_lat",
            "stop_lon",
            "location_type",
            "parent_station",
        ),
        [
            (
                stop["id"],
                stop["name"],
                stop["latitude"],
                stop["longitude"],
                0,
                stop["stop_place_id"],
            )
            for stop in stops
        ],
    )

    members["routes.txt"] = _csv(
        (
            "route_id",
            "agency_id",
            "route_short_name",
            "route_long_name",
            "route_type",
        ),
        [
            (
                line["id"],
                line["operator_id"],
                line["public_code"] or "",
                line["name"],
                3,  # GTFS route_type 3 is bus.
            )
            for line in lines
        ],
    )

    members["trips.txt"] = _csv(
        ("route_id", "service_id", "trip_id", "trip_headsign", "shape_id"),
        [
            (
                trip["line_id"],
                trip["gtfs_service_id"],
                trip["id"],
                "",
                trip["pattern_id"] or "",
            )
            for trip in exported_trips
        ],
    )

    members["stop_times.txt"] = _csv(
        (
            "trip_id",
            "arrival_time",
            "departure_time",
            "stop_id",
            "stop_sequence",
            "pickup_type",
            "drop_off_type",
            "timepoint",
        ),
        [
            (
                row["trip_id"],
                gtfs_time(
                    row["arrival_at"] or row["departure_at"],
                    trip_service_date[row["trip_id"]],
                ),
                gtfs_time(
                    row["departure_at"] or row["arrival_at"],
                    trip_service_date[row["trip_id"]],
                ),
                row["stop_id"],
                row["stop_sequence"],
                _pickup_code(
                    (
                        pattern_stops.get(
                            (trip_pattern.get(row["trip_id"]), row["stop_id"]), {}
                        )
                    ).get("pickup_type")
                ),
                _pickup_code(
                    (
                        pattern_stops.get(
                            (trip_pattern.get(row["trip_id"]), row["stop_id"]), {}
                        )
                    ).get("dropoff_type")
                ),
                1 if row["time_status"] == "scheduled" else 0,
            )
            for row in exported_stop_times
        ],
    )

    calendar_rows = [
        (
            calendar["id"],
            calendar["monday"],
            calendar["tuesday"],
            calendar["wednesday"],
            calendar["thursday"],
            calendar["friday"],
            calendar["saturday"],
            calendar["sunday"],
            gtfs_date(calendar["valid_from"]),
            gtfs_date(calendar["valid_until"]),
        )
        for calendar in calendars
    ]
    for calendar_id in sorted(synthetic_calendars):
        service_date = synthetic_calendars[calendar_id]
        calendar_rows.append(
            (
                calendar_id,
                0, 0, 0, 0, 0, 0, 0,
                gtfs_date(service_date),
                gtfs_date(service_date),
            )
        )
    members["calendar.txt"] = _csv(
        (
            "service_id",
            "monday",
            "tuesday",
            "wednesday",
            "thursday",
            "friday",
            "saturday",
            "sunday",
            "start_date",
            "end_date",
        ),
        sorted(calendar_rows, key=lambda row: row[0]),
    )

    exception_rows = [
        (
            row["calendar_id"],
            gtfs_date(row["service_date"]),
            1 if row["exception_type"] == "added" else 2,
        )
        for row in exceptions
        if row["calendar_id"] in calendar_ids
    ]
    for calendar_id in sorted(synthetic_calendars):
        exception_rows.append(
            (calendar_id, gtfs_date(synthetic_calendars[calendar_id]), 1)
        )
    members["calendar_dates.txt"] = _csv(
        ("service_id", "date", "exception_type"),
        sorted(exception_rows, key=lambda row: (row[0], row[1])),
    )

    members["feed_info.txt"] = _csv(
        (
            "feed_publisher_name",
            "feed_publisher_url",
            "feed_lang",
            "feed_version",
            "feed_contact_email",
        ),
        [
            (
                branding.PRODUCT_NAME,
                branding.PRODUCT_URL,
                "el",
                release_id,
                branding.PRODUCT_SUPPORT_EMAIL,
            )
        ],
    )

    members["attributions.txt"] = _csv(
        (
            "attribution_id",
            "agency_id",
            "organization_name",
            "is_operator",
            "attribution_url",
        ),
        [
            (
                f"att_{operator['id']}",
                operator["id"],
                operator["name_en"],
                1,
                operator["official_site_url"] or operator["directory_url"] or "",
            )
            for operator in operators
            if operator["id"] in {line["operator_id"] for line in lines}
        ],
    )

    return members


def build_feed_zip(conn: sqlite3.Connection, *, release_id: str) -> bytes:
    """Return a byte-identical zip for a given release."""
    members = build_feed(conn, release_id=release_id)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in FEED_FILES:
            body = members.get(name)
            if body is None:
                continue
            # A fixed timestamp keeps the archive reproducible across runs.
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, body)
    return buffer.getvalue()
