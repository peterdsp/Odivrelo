"""Service-date and timezone arithmetic, delegated to the staging layer.

The GTFS service-day definition (noon minus twelve hours, ``Europe/Athens``) is
already implemented once in ``poravia_ktel.ktel_gtfs`` and is what makes
daylight-saving days and past-midnight departures come out right. This module
reuses it rather than restating it, and adds only what the public contract needs
on top: projecting a template journey onto another service date, and deciding
whether a journey crosses midnight.
"""
from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

from . import _staging  # noqa: F401  (installs the staging import path)
from poravia_ktel.ktel_gtfs import (  # noqa: E402
    ATHENS,
    GtfsExportError,
    parse_instant,
    service_day_origin,
)

__all__ = [
    "ATHENS",
    "GtfsExportError",
    "parse_instant",
    "service_day_origin",
    "is_service_date",
    "parse_service_date",
    "offset_within_service_day",
    "project_onto_service_date",
    "crosses_midnight",
    "iso",
    "now_utc",
    "freshness",
    "weekday_column",
]

#: ``ktel_service_calendars`` column per Python weekday index (Monday is 0).
WEEKDAY_COLUMNS = (
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday",
    "sunday",
)

FRESH_HOURS = 48
AGING_HOURS = 168


def weekday_column(service_date: str) -> str:
    return WEEKDAY_COLUMNS[date.fromisoformat(service_date).weekday()]


def is_service_date(value: str) -> bool:
    try:
        date.fromisoformat(value)
    except (TypeError, ValueError):
        return False
    return len(value) == 10


def parse_service_date(value: str) -> date:
    return date.fromisoformat(value)


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def iso(moment: datetime) -> str:
    """Format an instant with an explicit offset, as the contract requires."""
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=ATHENS)
    if moment.utcoffset() == timedelta(0) and moment.tzinfo is timezone.utc:
        return moment.strftime("%Y-%m-%dT%H:%M:%SZ")
    return moment.isoformat(timespec="seconds")


def offset_within_service_day(instant: str, service_date: str) -> timedelta:
    """Seconds from the start of ``service_date`` to ``instant``.

    Negative offsets are refused: a stop time that precedes its own service day
    is a data defect, not something to round away.
    """
    delta = parse_instant(instant) - service_day_origin(service_date)
    if delta.total_seconds() < 0:
        raise GtfsExportError(
            f"instant {instant} precedes service date {service_date}"
        )
    return delta


def project_onto_service_date(
    instant: str, template_date: str, target_date: str
) -> datetime:
    """Move an instant from one service day to another, preserving wall clock.

    Because the service-day origin is defined as noon minus twelve hours, adding
    the same offset on a daylight-saving transition day reproduces the same
    local wall-clock time rather than sliding it by an hour. That is exactly the
    behaviour the contract promises for 2026-03-29 and 2026-10-25.
    """
    if template_date == target_date:
        return parse_instant(instant)
    offset = offset_within_service_day(instant, template_date)
    return (service_day_origin(target_date) + offset).astimezone(ATHENS)


def crosses_midnight(departure: datetime, arrival: datetime | None) -> bool:
    """True when the journey arrives on a later calendar day than it departs."""
    if arrival is None:
        return False
    return arrival.astimezone(ATHENS).date() > departure.astimezone(ATHENS).date()


def freshness(observed_at: str | None, *, now: datetime | None = None) -> dict:
    """Contract freshness block for an observation timestamp."""
    if not observed_at:
        return {"checkedAt": None, "ageHours": None, "state": "stale"}
    moment = now or now_utc()
    try:
        checked = parse_instant(observed_at)
    except (ValueError, TypeError):
        return {"checkedAt": None, "ageHours": None, "state": "stale"}
    age_hours = max(0, int((moment - checked).total_seconds() // 3600))
    if age_hours < FRESH_HOURS:
        state = "fresh"
    elif age_hours < AGING_HOURS:
        state = "aging"
    else:
        state = "stale"
    return {"checkedAt": iso(checked), "ageHours": age_hours, "state": state}
