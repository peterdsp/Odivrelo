"""The public read surface defined by data/schemas/CONTRACT-v1.md."""
from __future__ import annotations

from typing import Annotated, Any

from fastapi import APIRouter, Path, Query, Request, Response

from .. import release as release_module
from .. import repository
from ..brand import BRAND
from ..dependencies import (
    Language,
    Pagination,
    PublicDb,
    ReleaseGuard,
    SettingsDep,
)
from ..errors import unavailable
from ..responses import immutable_response, json_response

router = APIRouter(prefix="/v1", tags=["public"])

ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    # Every release-scoped response carries a strong ETag, so every one of them
    # can answer a conditional request with 304.
    304: {"description": "not_modified"},
    400: {"description": "invalid_request"},
    404: {"description": "not_found"},
    409: {"description": "release_mismatch"},
    503: {"description": "unavailable"},
}


def _envelope(connection, settings) -> dict[str, Any]:
    return repository.envelope(connection, settings.data_mode)


@router.get("/meta", responses=ERROR_RESPONSES, summary="Release and product metadata")
def meta(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    lang: Language = None,
) -> Response:
    payload = {
        **_envelope(connection, settings),
        **repository.meta(
            connection,
            data_mode=settings.data_mode,
            commit=settings.commit,
            built_at=settings.built_at,
        ),
    }
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get("/places", responses=ERROR_RESPONSES, summary="Place disambiguation")
def places(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    page: Pagination,
    q: Annotated[
        str | None,
        Query(description="Case- and accent-insensitive search across el and en."),
    ] = None,
    lang: Language = None,
) -> Response:
    limit, offset = page
    result = repository.search_places(
        connection, query=q, limit=limit, offset=offset, data_mode=settings.data_mode
    )
    payload = {**_envelope(connection, settings), **result}
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get("/journeys", responses=ERROR_RESPONSES, summary="Journey search")
def journeys(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    page: Pagination,
    origin: Annotated[str, Query(description="Place id of either kind.")],
    destination: Annotated[str, Query(description="Place id of either kind.")],
    date: Annotated[
        str | None, Query(description="Service date, YYYY-MM-DD in Europe/Athens.")
    ] = None,
    accessible: Annotated[
        bool,
        Query(description="Only journeys with a reviewed step-free boarding point."),
    ] = False,
    lang: Language = None,
) -> Response:
    limit, offset = page
    service_date = repository.require_service_date(date)
    result = repository.search_journeys(
        connection,
        origin_id=origin,
        destination_id=destination,
        service_date=service_date,
        accessible=accessible,
        limit=limit,
        offset=offset,
        data_mode=settings.data_mode,
    )
    payload = {**_envelope(connection, settings), **result}
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get(
    "/journeys/{journey_id}", responses=ERROR_RESPONSES, summary="Journey detail"
)
def journey(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    journey_id: Annotated[str, Path(description="Opaque journey id.")],
    date: Annotated[
        str | None, Query(description="Service date, YYYY-MM-DD in Europe/Athens.")
    ] = None,
    lang: Language = None,
) -> Response:
    service_date = repository.require_service_date(date) if date else None
    result = repository.journey_detail(
        connection,
        trip_id=journey_id,
        service_date=service_date,
        data_mode=settings.data_mode,
    )
    payload = {**_envelope(connection, settings), **result}
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get("/operators", responses=ERROR_RESPONSES, summary="Operators with content")
def operators(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    page: Pagination,
    lang: Language = None,
) -> Response:
    limit, offset = page
    result = repository.list_operators(
        connection, limit=limit, offset=offset, data_mode=settings.data_mode
    )
    payload = {**_envelope(connection, settings), **result}
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get(
    "/operators/{operator_id}", responses=ERROR_RESPONSES, summary="Operator detail"
)
def operator(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    operator_id: Annotated[str, Path(description="Opaque operator id.")],
    lang: Language = None,
) -> Response:
    result = repository.operator_detail(
        connection, operator_id, data_mode=settings.data_mode
    )
    payload = {**_envelope(connection, settings), **result}
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get("/stops/{stop_id}", responses=ERROR_RESPONSES, summary="Station page")
def stop(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    page: Pagination,
    stop_id: Annotated[str, Path(description="Opaque stop id.")],
    date: Annotated[
        str | None, Query(description="Service date, YYYY-MM-DD in Europe/Athens.")
    ] = None,
    lang: Language = None,
) -> Response:
    limit, _ = page
    service_date = repository.require_service_date(date)
    result = repository.stop_detail(
        connection,
        stop_id=stop_id,
        service_date=service_date,
        data_mode=settings.data_mode,
        limit=limit,
    )
    payload = {**_envelope(connection, settings), **result}
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get("/coverage", responses=ERROR_RESPONSES, summary="Coverage and freshness")
def coverage(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
) -> Response:
    payload = {
        **_envelope(connection, settings),
        **repository.coverage(connection, data_mode=settings.data_mode),
    }
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get("/sources", responses=ERROR_RESPONSES, summary="Public source registry")
def sources(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    page: Pagination,
) -> Response:
    limit, offset = page
    payload = {
        **_envelope(connection, settings),
        **repository.sources(connection, limit=limit, offset=offset),
    }
    return json_response(
        request,
        payload,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get(
    "/offline/manifest", responses=ERROR_RESPONSES, summary="Offline release manifest"
)
def offline_manifest(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
) -> Response:
    manifest = release_module.manifest_for_release(settings, release_id)
    return json_response(
        request,
        manifest,
        max_age=settings.release_json_max_age,
        release_id=release_id,
    )


@router.get(
    "/offline/packs/{filename}",
    responses=ERROR_RESPONSES,
    summary="Content-addressed offline pack",
)
def offline_pack(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
    filename: Annotated[str, Path(description="Content-addressed pack file name.")],
) -> Response:
    manifest = release_module.manifest_for_release(settings, release_id)
    entry = release_module.pack_entry(manifest, filename)
    tag = release_module.etag(entry)
    if release_module.etag_matches(request.headers.get("if-none-match"), tag):
        return Response(
            status_code=304,
            headers={
                "ETag": tag,
                "Cache-Control": "public, max-age=31536000, immutable",
                "X-Release-Id": release_id or "",
            },
        )
    body = release_module.read_pack(settings, entry)
    return immutable_response(
        request,
        body,
        tag=tag,
        media_type=release_module.media_type(entry),
        release_id=release_id,
    )


@router.get("/gtfs", responses=ERROR_RESPONSES, summary="Reviewed GTFS feed")
def gtfs(
    request: Request,
    connection: PublicDb,
    settings: SettingsDep,
    release_id: ReleaseGuard,
) -> Response:
    manifest = release_module.manifest_for_release(settings, release_id)
    entry = manifest.get("files", {}).get("gtfs")
    if not isinstance(entry, dict):
        raise unavailable("this release carries no GTFS feed")
    tag = release_module.etag(entry)
    if release_module.etag_matches(request.headers.get("if-none-match"), tag):
        return Response(
            status_code=304,
            headers={
                "ETag": tag,
                "Cache-Control": "public, max-age=31536000, immutable",
                "X-Release-Id": release_id or "",
            },
        )
    body = release_module.read_pack(settings, entry)
    return immutable_response(
        request,
        body,
        tag=tag,
        media_type=release_module.media_type(entry),
        release_id=release_id,
    )
