"""The one generator of offline packs, in the published contract shape.

An offline client and an online client decode the same payloads with the same
code. A pack therefore carries exactly what the matching ``/v1/...`` endpoint
would have returned, built by the same functions in :mod:`publicapi.repository`,
not by a near-copy that drifts.

Only the payload *shaping* lives here. Content addressing, digests, the manifest
written last, the retained previous manifest and ``verify_release`` remain in
``ktel_release``; this module is passed to it as a payload provider.

Canonical logical pack names, and nothing else:

===========================  ====================================================
``meta``                     the ``/v1/meta`` body
``coverage``                 the ``/v1/coverage`` body
``sources``                  the ``/v1/sources`` body
``places``                   the ``/v1/places`` body, unfiltered and unpaged
``operators``                every operator in the ``/v1/operators/{id}`` shape,
                             keyed by id
``stops``                    every stop in the ``/v1/stops/{id}`` shape, keyed by id
``journeys-<serviceDate>``   the ``/v1/journeys`` result list for that date, plus
                             the ``/v1/journeys/{id}`` detail for each result,
                             keyed by journey id
``gtfs``                     the reviewed feed for the release
===========================  ====================================================
"""
from __future__ import annotations

import json
import re
import sqlite3
from typing import Any, Callable

from . import _staging  # noqa: F401  (installs the staging import path)
from . import repository  # noqa: E402
from .brand import BRAND  # noqa: E402
from hodomap_ktel import ktel_gtfs  # noqa: E402

#: The complete set of logical pack names a release may carry. A journey pack is
#: suffixed with its service date; everything else is fixed. The contract and
#: this tuple are checked against each other by the test suite.
FIXED_PACK_NAMES = (
    "meta",
    "coverage",
    "sources",
    "places",
    "operators",
    "stops",
    "gtfs",
)

JOURNEY_PACK_PATTERN = re.compile(r"^journeys-(\d{4}-\d{2}-\d{2})$")

#: Which JSON Schema in ``data/schemas/`` a decoded pack body validates against.
SCHEMA_FOR_PACK = {
    "meta": "pack-meta-v1",
    "coverage": "pack-coverage-v1",
    "sources": "pack-sources-v1",
    "places": "pack-places-v1",
    "operators": "pack-operators-v1",
    "stops": "pack-stops-v1",
}
JOURNEY_PACK_SCHEMA = "pack-journeys-v1"


def journey_pack_name(service_date: str) -> str:
    return f"journeys-{service_date}"


def is_canonical(name: str) -> bool:
    return name in FIXED_PACK_NAMES or bool(JOURNEY_PACK_PATTERN.match(name))


def canonical_names(service_dates: list[str]) -> set[str]:
    return set(FIXED_PACK_NAMES) | {
        journey_pack_name(service_date) for service_date in service_dates
    }


def canonical_json(payload: Any) -> bytes:
    """The same canonical encoding the release mechanism has always used."""
    return json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def _envelope(connection: sqlite3.Connection, data_mode: str) -> dict[str, Any]:
    return repository.envelope(connection, data_mode)


def build_bodies(
    connection: sqlite3.Connection,
    release_id: str,
    *,
    data_mode: str,
    commit: str = "unknown",
    built_at: str = "unknown",
    now: Any = None,
) -> dict[str, Any]:
    """Return the decoded body of every pack, keyed by logical pack name."""
    envelope = _envelope(connection, data_mode)
    bodies: dict[str, Any] = {}

    bodies["meta"] = {
        **envelope,
        **repository.meta(
            connection, data_mode=data_mode, commit=commit, built_at=built_at
        ),
    }
    bodies["coverage"] = {
        **envelope,
        **repository.coverage(connection, data_mode=data_mode, now=now),
    }
    bodies["sources"] = {
        **envelope,
        **repository.sources(
            connection, limit=repository.PACK_ROW_LIMIT, offset=0
        ),
    }
    bodies["places"] = {
        **envelope,
        **repository.search_places(
            connection,
            query=None,
            limit=repository.PACK_ROW_LIMIT,
            offset=0,
            data_mode=data_mode,
        ),
    }
    bodies["operators"] = {
        **envelope,
        "operators": {
            operator_id: repository.operator_detail(
                connection, operator_id, data_mode=data_mode
            )["operator"]
            for operator_id in repository.all_operator_ids(connection)
        },
    }

    # A station page is bound to a service date. The pack uses the earliest date
    # the release materialises, so an offline client sees a deterministic page
    # rather than one that depends on when the pack was built.
    service_dates = repository.pack_service_dates(connection)
    stop_date = service_dates[0] if service_dates else repository.today_in_athens()
    bodies["stops"] = {
        **envelope,
        "serviceDate": stop_date,
        "stops": {
            stop_id: repository.stop_detail(
                connection,
                stop_id=stop_id,
                service_date=stop_date,
                data_mode=data_mode,
                limit=repository.PACK_ROW_LIMIT,
                now=now,
            )["stop"]
            for stop_id in repository.all_stop_ids(connection)
        },
    }

    for service_date in service_dates:
        results: list[dict[str, Any]] = []
        details: dict[str, Any] = {}
        for trip_id in repository.journey_ids_on_date(connection, service_date):
            summary = repository.journey_summary(
                connection, trip_id=trip_id, service_date=service_date, now=now
            )
            if summary is None:
                continue
            results.append(summary)
            details[trip_id] = repository.journey_detail(
                connection,
                trip_id=trip_id,
                service_date=service_date,
                data_mode=data_mode,
                now=now,
            )["journey"]
        results.sort(key=lambda item: (item["departure"]["at"], item["id"]))
        bodies[journey_pack_name(service_date)] = {
            **envelope,
            "serviceDate": service_date,
            "coverage": "demo" if data_mode == "demo" else "covered",
            "results": results,
            "journeys": details,
        }
    return bodies


def build_payloads(
    connection: sqlite3.Connection,
    release_id: str,
    *,
    data_mode: str,
    commit: str = "unknown",
    built_at: str = "unknown",
    now: Any = None,
) -> dict[str, bytes]:
    """Encode every pack, plus the GTFS feed, ready for the release mechanism."""
    bodies = build_bodies(
        connection,
        release_id,
        data_mode=data_mode,
        commit=commit,
        built_at=built_at,
        now=now,
    )
    payloads = {name: canonical_json(body) for name, body in bodies.items()}
    # The feed is already a reproducible archive, and it is not reshaped here.
    payloads["gtfs"] = ktel_gtfs.build_feed_zip(connection, release_id=release_id)

    unexpected = {name for name in payloads if not is_canonical(name)}
    if unexpected:
        raise ValueError(
            f"non-canonical pack name(s) {sorted(unexpected)}; the offline "
            "manifest section of the data contract lists the permitted set"
        )
    return payloads


def payload_provider(
    *,
    data_mode: str,
    commit: str = "unknown",
    built_at: str = "unknown",
    now: Any = None,
) -> Callable[[sqlite3.Connection, str], dict[str, bytes]]:
    """Bind the release-wide settings and return a provider for ``ktel_release``."""
    if data_mode not in {"real", "demo"}:
        raise ValueError(f"unknown data mode: {data_mode}")

    def provider(
        connection: sqlite3.Connection, release_id: str
    ) -> dict[str, bytes]:
        return build_payloads(
            connection,
            release_id,
            data_mode=data_mode,
            commit=commit,
            built_at=built_at,
            now=now,
        )

    provider.__doc__ = (
        f"Canonical {BRAND.contract_version} pack payloads, data mode {data_mode!r}."
    )
    return provider
