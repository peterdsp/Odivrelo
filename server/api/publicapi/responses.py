"""Cache-aware JSON and pack responses.

Release-scoped JSON gets a strong ETag over its own bytes and a short freshness
window, so a client revalidates cheaply after a publish. Content-addressed packs
get the immutable one-year policy the contract specifies, because their name
already contains their digest.
"""
from __future__ import annotations

import hashlib
import json
from typing import Any

from fastapi import Request, Response
from fastapi.responses import JSONResponse

from .release import etag_matches

IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable"


def _canonical(payload: Any) -> bytes:
    return json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def json_response(
    request: Request,
    payload: dict[str, Any],
    *,
    max_age: int,
    release_id: str | None = None,
) -> Response:
    body = _canonical(payload)
    tag = f'"{hashlib.sha256(body).hexdigest()}"'
    headers = {
        "ETag": tag,
        "Cache-Control": f"public, max-age={max_age}, must-revalidate",
    }
    if release_id:
        headers["X-Release-Id"] = release_id
    if etag_matches(request.headers.get("if-none-match"), tag):
        return Response(status_code=304, headers=headers)
    return Response(
        content=body,
        media_type="application/json",
        headers=headers,
    )


def immutable_response(
    request: Request,
    body: bytes,
    *,
    tag: str,
    media_type: str,
    release_id: str | None = None,
) -> Response:
    headers = {
        "ETag": tag,
        "Cache-Control": IMMUTABLE_CACHE_CONTROL,
    }
    if release_id:
        headers["X-Release-Id"] = release_id
    if etag_matches(request.headers.get("if-none-match"), tag):
        return Response(status_code=304, headers=headers)
    return Response(content=body, media_type=media_type, headers=headers)


def no_store(payload: dict[str, Any], status_code: int = 200) -> JSONResponse:
    response = JSONResponse(content=payload, status_code=status_code)
    response.headers["Cache-Control"] = "no-store"
    return response
