"""Shared FastAPI dependencies: settings, per-request handles, paging, release match."""
from __future__ import annotations

import sqlite3
from typing import Annotated, Iterator

from fastapi import Depends, Query, Request

from . import repository
from .config import DEFAULT_PAGE_LIMIT, MAX_PAGE_LIMIT, Settings
from .db import public_connection
from .errors import invalid_request, release_mismatch

RELEASE_HEADER = "X-Release-Id"


def get_settings(request: Request) -> Settings:
    return request.app.state.settings


SettingsDep = Annotated[Settings, Depends(get_settings)]


def public_db(settings: SettingsDep) -> Iterator[sqlite3.Connection]:
    """One read-only handle per request, closed when the response is done."""
    with public_connection(settings.public_db_path) as connection:
        yield connection


PublicDb = Annotated[sqlite3.Connection, Depends(public_db)]


def pagination(
    limit: Annotated[
        int,
        Query(
            description="Maximum rows to return. Values above 200 are refused.",
        ),
    ] = DEFAULT_PAGE_LIMIT,
    offset: Annotated[int, Query(description="Rows to skip.")] = 0,
) -> tuple[int, int]:
    if limit > MAX_PAGE_LIMIT:
        raise invalid_request(
            f"limit must not exceed {MAX_PAGE_LIMIT}; received {limit}", "limit"
        )
    if limit < 1:
        raise invalid_request(f"limit must be at least 1; received {limit}", "limit")
    if offset < 0:
        raise invalid_request(f"offset must not be negative; received {offset}", "offset")
    return limit, offset


Pagination = Annotated[tuple[int, int], Depends(pagination)]


def release_guard(
    request: Request,
    connection: PublicDb,
    release: Annotated[
        str | None,
        Query(
            description=(
                "Release the client already holds packs for. A mismatch is "
                "refused with 409 rather than mixing two snapshots."
            )
        ),
    ] = None,
) -> str | None:
    """Refuse to mix a client's cached release with a different served one."""
    current = repository.release_metadata(connection)["releaseId"]
    expected = release or request.headers.get(RELEASE_HEADER)
    if expected and current and expected != current:
        raise release_mismatch(
            f"this service is serving release {current}; the request asked for "
            f"{expected}. Refresh the offline manifest before retrying.",
            "release",
        )
    return current


ReleaseGuard = Annotated[str | None, Depends(release_guard)]


def language(
    lang: Annotated[
        str | None,
        Query(description="Preferred display language. All languages are returned."),
    ] = None,
) -> str | None:
    """Validate the requested language without narrowing the response.

    The contract returns every language in every name object, so ``lang`` is a
    client hint. An unknown value is still refused, because silently ignoring it
    hides a client bug.
    """
    from .brand import BRAND

    if lang is None:
        return None
    if lang not in BRAND.languages:
        raise invalid_request(
            f"{lang!r} is not a supported language; expected one of "
            f"{', '.join(BRAND.languages)}",
            "lang",
        )
    return lang


Language = Annotated[str | None, Depends(language)]
