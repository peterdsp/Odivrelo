"""Parse the Greek NAP long-distance bus workbook into a candidate dataset.

The NAP 2020 workbook (`artifacts/nap/nap-ktel-2020.xlsx`, ODbL 1.0, published by
the Hellenic Institute of Transport) is the only real, permitted KTEL dataset
available. It is a 2020 vintage, city-level timetable: it lists departure times,
day-of-week prose, and sometimes an arrival time and fare, for origin and
destination *cities*. It has no coordinates, no intermediate stops, and no
physical boarding points.

This module turns it into the normalized import contract as a reviewable
**candidate** dataset. It never claims the data is current:

* every stop is city-level with no coordinate, so it stays `candidate` and is
  never a real boarding point;
* every trip is marked `nap_file` scope and carries the original Greek source
  text beside the normalized values;
* the Greek day prose is normalized into a weekday calendar, with an
  ``ambiguous`` flag kept when the prose does not pin the days exactly.

Two sheet layouts exist in the workbook and both are handled:

* Layout A: uppercase headers (``ΑΠΟ``/``ΠΡΟΣ``/``ΩΡΑ ΑΝΑΧ/ΣΗΣ`` ...), one
  departure time per row, with ``ΑΠΟ``/``ΠΡΟΣ`` merged down a group of rows.
* Layout B: mixed-case headers (``Από``/``Προς`` ...), comma-separated departure
  times in a single cell, hyphen-range day prose such as ``ΔΕΥ-ΚΥΡ``.

Publishing is out of scope here on purpose: 2020 city-level data fails the
freshness and boarding-evidence gates, so it must be reviewed, not shipped.
"""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field
from datetime import date
from typing import Any, Iterable

# Monday..Sunday, the order the service-calendar columns use.
WEEKDAYS = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

#: The dataset's documented validity. It is historical; nothing here is current.
NAP_SOURCE_ID = "greek-nap-ktel"
NAP_LICENCE = "ODbL-1.0"
NAP_ATTRIBUTION = "Greek National Access Point, Hellenic Institute of Transport, ODbL 1.0"
NAP_VINTAGE_YEAR = 2020
PARSER_VERSION = "nap-2020-1"

# Greek day tokens to a weekday index, accent-stripped and upper-cased first.
_DAY_TOKENS = {
    "ΔΕΥ": 0, "ΔΕΥΤΕΡΑ": 0,
    "ΤΡ": 1, "ΤΡΙ": 1, "ΤΡΙΤΗ": 1,
    "ΤΕΤ": 2, "ΤΕΤΑΡΤΗ": 2,
    "ΠΕΜ": 3, "ΠΕΜΠΤΗ": 3,
    "ΠΑΡ": 4, "ΠΑΡΑΣΚΕΥΗ": 4,
    "ΣΑΒ": 5, "ΣΑΒΒΑΤΟ": 5,
    "ΚΥΡ": 6, "ΚΥΡΙΑΚΗ": 6, "Κ": 6,
}


def strip_accents(text: str) -> str:
    """Upper-case and remove Greek diacritics for robust token matching."""
    decomposed = unicodedata.normalize("NFD", text)
    without = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return unicodedata.normalize("NFC", without).upper().strip()


# Latin letters that look identical to upper-case Greek ones. The workbook has a
# few day tokens typed with a Latin letter (for example "TΡ" with a Latin T), so
# folding these lets the day parser still recognise them.
_LATIN_TO_GREEK = str.maketrans({
    "A": "Α", "B": "Β", "E": "Ε", "Z": "Ζ", "H": "Η", "I": "Ι", "K": "Κ",
    "M": "Μ", "N": "Ν", "O": "Ο", "P": "Ρ", "T": "Τ", "X": "Χ", "Y": "Υ",
})


def _fold_lookalikes(text: str) -> str:
    return text.translate(_LATIN_TO_GREEK)


@dataclass
class ParsedDays:
    """A weekday calendar parsed from Greek prose, with its provenance."""

    days: tuple[bool, ...]  # length 7, Monday..Sunday
    ambiguous: bool
    original: str
    note: str = ""

    def as_calendar_flags(self) -> dict[str, bool]:
        return {name: self.days[index] for index, name in enumerate(WEEKDAYS)}

    @property
    def any_day(self) -> bool:
        return any(self.days)


def _range(a: int, b: int) -> set[int]:
    """Inclusive weekday range that wraps the week, e.g. ΠΑΡ..ΔΕΥ."""
    if a <= b:
        return set(range(a, b + 1))
    return set(range(a, 7)) | set(range(0, b + 1))


def parse_days(raw: str | None) -> ParsedDays:
    """Normalize Greek day-of-week prose into a weekday calendar.

    Handles single days, ``ΜΕΧΡΙ`` and hyphen ranges, ``ΚΑΙ``, comma and slash
    unions, ``ΕΚΤΟΣ`` (except), ``ΣΚ`` (weekend), ``ΜΟΝΟ`` (only), and
    ``ΚΑΘΗΜΕΡΙΝΑ`` (daily, kept ambiguous). Unknown prose returns no days with
    ``ambiguous`` set, so a reviewer, not a guess, decides.
    """
    original = "" if raw is None else str(raw).strip()
    text = _fold_lookalikes(strip_accents(original))
    if not text or text in {"ΚΕΝΟ", "-"}:
        return ParsedDays((False,) * 7, True, original, "empty day prose")

    # ΚΑΘΗΜΕΡΙΝΑ means "daily" literally; in KTEL use it sometimes means
    # weekdays only. Keep all seven but flag the ambiguity for review.
    if text.startswith("ΚΑΘΗΜΕΡΙΝ"):
        days = [True] * 7
        note = "ΚΑΘΗΜΕΡΙΝΑ taken as daily; verify whether weekday-only"
        remainder = text.replace("ΚΑΘΗΜΕΡΙΝΑ", "").replace("ΚΑΘΗΜΕΡΙΝΕΣ", "")
        # Allow a trailing exception such as "ΚΑΘΗΜΕΡΙΝΑ ΕΚΤΟΣ ΣΚ".
        if "ΕΚΤΟΣ" in remainder:
            excepted = _tokens_to_days(remainder.split("ΕΚΤΟΣ", 1)[1])
            for d in excepted:
                days[d] = False
            note = "ΚΑΘΗΜΕΡΙΝΑ with an explicit exception"
        return ParsedDays(tuple(days), True, original, note)

    except_mode = "ΕΚΤΟΣ" in text
    body = text.split("ΕΚΤΟΣ", 1)[1] if except_mode else text
    selected = _parse_day_body(body)

    if except_mode:
        days = [d not in selected for d in range(7)]
        return ParsedDays(tuple(days), False, original, "ΕΚΤΟΣ (all days except)")

    if not selected:
        return ParsedDays((False,) * 7, True, original, "unrecognised day prose")
    return ParsedDays(tuple(d in selected for d in range(7)), False, original)


def _parse_day_body(body: str) -> set[int]:
    body = body.replace("ΜΟΝΟ", " ")
    if "ΣΚ" in body:  # Σαββατοκύριακο, the weekend
        base = {5, 6}
        body = body.replace("ΣΚ", " ")
        return base | _tokens_to_days(body)
    # A range: "ΔΕΥ ΜΕΧΡΙ ΠΑΡ" or "ΔΕΥ-ΚΥΡ".
    range_split = re.split(r"ΜΕΧΡΙ|-", body)
    if len(range_split) == 2:
        left = _single_token(range_split[0])
        right = _single_token(range_split[1])
        if left is not None and right is not None:
            return _range(left, right)
    return _tokens_to_days(body)


def _single_token(chunk: str) -> int | None:
    for token in re.split(r"[\s,/]+", chunk.strip()):
        if token in _DAY_TOKENS:
            return _DAY_TOKENS[token]
    return None


def _tokens_to_days(chunk: str) -> set[int]:
    days: set[int] = set()
    for token in re.split(r"[\s,/]+|ΚΑΙ", chunk):
        token = token.strip()
        if token in _DAY_TOKENS:
            days.add(_DAY_TOKENS[token])
    return days


def parse_times(raw: Any) -> list[str]:
    """Return zero or more ``HH:MM`` departure times from one cell.

    Layout A carries one time per row; Layout B packs several into one cell as
    ``7:45,16:00``. A datetime.time value (Excel time) is formatted too.
    Overnight times past midnight are kept as written and flagged by the caller.
    """
    if raw is None:
        return []
    if hasattr(raw, "hour") and hasattr(raw, "minute"):
        return [f"{raw.hour:02d}:{raw.minute:02d}"]
    text = str(raw).strip()
    if not text:
        return []
    times: list[str] = []
    for part in re.split(r"[,/]| ΚΑΙ ", text):
        part = part.strip()
        match = re.match(r"^(\d{1,2}):(\d{2})(?::\d{2})?$", part)
        if match:
            hour, minute = int(match.group(1)), int(match.group(2))
            times.append(f"{hour:02d}:{minute:02d}")
    return times


# Header aliases, accent-stripped upper-case, mapped to a logical column.
_COLUMN_ALIASES = {
    "ΑΠΟ": "from",
    "ΠΡΟΣ": "to",
    "ΩΡΑ ΑΝΑΧ/ΣΗΣ": "departure",
    "ΩΡΑ ΑΝΑΧΩΡΗΣΗΣ": "departure",
    "ΩΡΑ ΑΦΙΞΗΣ": "arrival",
    "ΔΙΑΡΚΕΙΑ": "duration",
    "ΗΜΕΡΕΣ": "days",
    "ΟΛΟΚΛΗΡΟ": "fare_full",
    "ΑΠΛΟ": "fare_full",
    "ΑΠΟΣΤΑΣΗ": "distance",
    "ΑΛΛΕΣ ΠΛΗΡΟΦΟΡΙΕΣ": "info",
    "ΠΛΗΡΟΦΟΡΙΕΣ": "info",
}


@dataclass
class SheetResult:
    prefecture: str
    rows: int = 0
    trips: list[dict[str, Any]] = field(default_factory=list)
    cities: dict[str, str] = field(default_factory=dict)  # upper name -> original
    pairs: set[tuple[str, str]] = field(default_factory=set)
    issues: list[str] = field(default_factory=list)


def _find_header(rows: list[tuple]) -> tuple[int, dict[int, str]] | None:
    for index, row in enumerate(rows[:15]):
        cells = {strip_accents(str(c)) if c is not None else "": pos for pos, c in enumerate(row)}
        if "ΑΠΟ" in cells and "ΠΡΟΣ" in cells:
            mapping: dict[int, str] = {}
            for pos, value in enumerate(row):
                key = strip_accents(str(value)) if value is not None else ""
                if key in _COLUMN_ALIASES:
                    mapping[pos] = _COLUMN_ALIASES[key]
            return index, mapping
    return None


def parse_sheet(title: str, rows: list[tuple]) -> SheetResult:
    """Parse one prefecture worksheet into candidate trips (both layouts)."""
    result = SheetResult(prefecture=title)
    header = _find_header(rows)
    if header is None:
        result.issues.append("no ΑΠΟ/ΠΡΟΣ header found")
        return result
    header_index, columns = header
    by_role = {role: pos for pos, role in columns.items()}

    last_from = last_to = None
    for row in rows[header_index + 1:]:
        if not any(cell is not None for cell in row):
            continue

        def cell(role: str) -> Any:
            pos = by_role.get(role)
            return row[pos] if pos is not None and pos < len(row) else None

        city_from = cell("from")
        city_to = cell("to")
        # Layout A merges ΑΠΟ/ΠΡΟΣ down a group of rows; carry them forward.
        if city_from:
            last_from = str(city_from).strip()
        if city_to:
            last_to = str(city_to).strip()
        times = parse_times(cell("departure"))
        if not times or not last_from or not last_to:
            continue

        result.rows += 1
        days = parse_days(cell("days"))
        arrival_times = parse_times(cell("arrival"))
        fare = _parse_fare(cell("fare_full"))
        for key, original in ((strip_accents(last_from), last_from), (strip_accents(last_to), last_to)):
            result.cities.setdefault(key, original)
        result.pairs.add((strip_accents(last_from), strip_accents(last_to)))

        for order, departure in enumerate(times):
            overnight = _is_overnight(departure, arrival_times[0] if arrival_times else None)
            result.trips.append(
                {
                    "fromCity": strip_accents(last_from),
                    "toCity": strip_accents(last_to),
                    "fromNameEl": last_from,
                    "toNameEl": last_to,
                    "departure": departure,
                    "arrival": arrival_times[0] if len(arrival_times) == 1 else None,
                    "days": days,
                    "fare": fare,
                    "overnight": overnight,
                    "sourceRow": {
                        "days": days.original,
                        "departureRaw": str(cell("departure")),
                        "fareRaw": None if cell("fare_full") is None else str(cell("fare_full")),
                        "duration": None if cell("duration") is None else str(cell("duration")),
                        "info": None if cell("info") is None else str(cell("info")),
                    },
                }
            )
    return result


def _parse_fare(raw: Any) -> float | None:
    if raw is None:
        return None
    if isinstance(raw, (int, float)):
        return float(raw) if raw > 0 else None
    match = re.search(r"\d+(?:[.,]\d+)?", str(raw))
    if not match:
        return None
    try:
        value = float(match.group(0).replace(",", "."))
    except ValueError:
        return None
    return value if value > 0 else None


def _is_overnight(departure: str, arrival: str | None) -> bool:
    if not arrival:
        return False
    try:
        dh = int(departure[:2])
        ah = int(arrival[:2])
    except (ValueError, IndexError):
        return False
    return ah < dh


# --------------------------------------------------------------------------- #
# Operator mapping and normalized snapshot building
# --------------------------------------------------------------------------- #

#: Sheet-name to operator-id aliases the fuzzy match does not catch.
# Ν.ΒΟΙΩΤΙΑΣ spans two operators (ktel-thiva and ktel-livadeia); it is assigned
# to the prefecture-capital operator here and flagged for a reviewer to split.
_OPERATOR_ALIASES = {
    "ΒΟΙΩΤΙΑΣ": "ktel-livadeia",
    "ΚΕΦΑΛΛΗΝΙΑΣ": "ktel-kefalonia",
    # Spelling variant: the sheet writes ΛΑΣΙΘΕΙΟΥ, the registry ΛΑΣΙΘΙΟΥ.
    "ΗΡΑΚΛΕΙΟΥ-ΛΑΣΙΘΕΙΟΥ": "ktel-heraklion-lasithi",
}

#: Template date the 2020 timetables are stored against. It is a Monday, and it
#: is deliberately in 2020 so the data reads as the historical vintage it is.
NAP_TEMPLATE_DATE = "2020-06-01"
NAP_VALID_FROM = "2020-01-01"
NAP_VALID_UNTIL = "2020-12-31"


def _operator_key(name: str) -> str:
    key = strip_accents(name)
    for drop in ("ΥΠΕΡΑΣΤΙΚΟ", "ΚΤΕΛ", "ΝΟΜΟΥ", "Ν."):
        key = key.replace(drop, "")
    return key.strip()


def load_operator_index(operators: Iterable[dict[str, Any]]) -> dict[str, str]:
    """Map a normalized operator name key to its registry id."""
    index: dict[str, str] = {}
    for operator in operators:
        key = _operator_key(operator.get("nameEl") or "")
        if key:
            index[key] = operator["id"]
    return index


def map_operator(prefecture: str, index: dict[str, str]) -> str | None:
    """Resolve a prefecture sheet title to a registry operator id, or None."""
    key = _operator_key(prefecture)
    if key in _OPERATOR_ALIASES:
        return _OPERATOR_ALIASES[key]
    if key in index:
        return index[key]
    # A guarded fuzzy fallback: only a prefix relation between reasonably long
    # keys, so a short genitive fragment (for example ΙΟΥ inside ΗΡΑΚΛΕΙΟΥ)
    # cannot match the wrong operator.
    for candidate, operator_id in index.items():
        if len(candidate) >= 6 and (key.startswith(candidate) or candidate.startswith(key)):
            return operator_id
    return None


def _day_bitmask(days: ParsedDays) -> str:
    return "".join("1" if d else "0" for d in days.days)


def build_snapshot(
    sheet: SheetResult, operator_id: str, retrieved_at: str
) -> dict[str, Any]:
    """Turn one parsed sheet into a normalized, candidate import snapshot.

    Everything stays candidate: stops are city-level with no coordinate, trips
    carry the 2020 vintage and the original Greek prose, and calendars are bound
    to the parsed weekdays. Nothing here is publishable as current service.
    """
    stops = [
        {
            "externalId": f"nap-city-{key}",
            "name": original,
            "nameEl": original,
            "publicAttributes": {
                "cityLevel": True,
                "source": "nap-2020",
                "note": "City-level origin/destination from the 2020 NAP workbook; "
                "not a physical boarding point.",
            },
        }
        for key, original in sorted(sheet.cities.items())
    ]

    lines = []
    patterns = []
    for origin, destination in sorted(sheet.pairs):
        from_name = sheet.cities.get(origin, origin)
        to_name = sheet.cities.get(destination, destination)
        pair_id = f"{origin}>{destination}"
        lines.append(
            {
                "externalId": f"nap-line-{pair_id}",
                "publicCode": None,
                "name": f"{from_name} - {to_name}",
                "nameEl": f"{from_name} - {to_name}",
                "dataStatus": "stale",
            }
        )
        patterns.append(
            {
                "externalId": f"nap-pat-{pair_id}",
                "lineExternalId": f"nap-line-{pair_id}",
                "name": f"{from_name} - {to_name}",
                "direction": "outbound",
                "geometryStatus": "unverified",
                "stops": [
                    {"stopExternalId": f"nap-city-{origin}", "sequence": 1,
                     "pickupType": "allowed", "dropoffType": "not_allowed"},
                    {"stopExternalId": f"nap-city-{destination}", "sequence": 2,
                     "pickupType": "not_allowed", "dropoffType": "allowed"},
                ],
            }
        )

    calendars: dict[str, dict[str, Any]] = {}
    trips = []
    for index, trip in enumerate(sheet.trips):
        days: ParsedDays = trip["days"]
        pair_id = f"{trip['fromCity']}>{trip['toCity']}"
        calendar_external = None
        if days.any_day:
            mask = _day_bitmask(days)
            calendar_external = f"nap-cal-{mask}"
            if calendar_external not in calendars:
                calendars[calendar_external] = {
                    "externalId": calendar_external,
                    "name": f"NAP 2020 days {mask}",
                    "validFrom": NAP_VALID_FROM,
                    "validUntil": NAP_VALID_UNTIL,
                    **days.as_calendar_flags(),
                    "verificationState": "candidate",
                    "exceptions": [],
                }
        arrival_at = None
        if trip["arrival"]:
            arrival_date = "2020-06-02" if trip["overnight"] else NAP_TEMPLATE_DATE
            arrival_at = f"{arrival_date}T{trip['arrival']}:00+03:00"
        trips.append(
            {
                "externalId": f"nap-{pair_id}-{trip['departure']}-{index}",
                "lineExternalId": f"nap-line-{pair_id}",
                "patternExternalId": f"nap-pat-{pair_id}",
                "calendarExternalId": calendar_external,
                "serviceDate": NAP_TEMPLATE_DATE,
                "departureAt": f"{NAP_TEMPLATE_DATE}T{trip['departure']}:00+03:00",
                "approximateArrivalAt": arrival_at,
                "commercialState": "unknown",
                "scheduleScope": "nap_published",
                "fareAmount": trip["fare"],
                "fareCurrency": "EUR" if trip["fare"] else None,
                "observedAt": retrieved_at,
                "publicAttributes": {
                    "positionQuality": "scheduled",
                    "vintage": NAP_VINTAGE_YEAR,
                    "stale": True,
                    "daysOriginal": days.original,
                    "daysAmbiguous": days.ambiguous,
                    "overnight": trip["overnight"],
                    "attribution": NAP_ATTRIBUTION,
                    "parserVersion": PARSER_VERSION,
                    "sourceRow": trip["sourceRow"],
                },
                "stopTimes": [
                    {"stopExternalId": f"nap-city-{trip['fromCity']}", "sequence": 1,
                     "departureAt": f"{NAP_TEMPLATE_DATE}T{trip['departure']}:00+03:00",
                     "timeStatus": "scheduled"},
                    *(
                        [{"stopExternalId": f"nap-city-{trip['toCity']}", "sequence": 2,
                          "arrivalAt": arrival_at, "timeStatus": "approximate"}]
                        if arrival_at else []
                    ),
                ],
            }
        )

    return {
        "dataset": "real",
        "operatorId": operator_id,
        "sourceId": NAP_SOURCE_ID,
        "retrievedAt": retrieved_at,
        "importRunId": f"nap-2020-{operator_id}",
        "stopPlaces": [],
        "stops": stops,
        "lines": lines,
        "journeyPatterns": patterns,
        "serviceCalendars": list(calendars.values()),
        "trips": trips,
    }
