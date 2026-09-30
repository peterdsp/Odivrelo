"""Response hardening and request logging middleware."""
from __future__ import annotations

import time

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp

from .config import Settings
from .logging_setup import get_logger, new_request_id, safe_query

#: A JSON API serves no documents, loads no subresources and is never framed.
#: ``default-src 'none'`` plus the navigational directives is the whole policy.
CONTENT_SECURITY_POLICY = (
    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; "
    "form-action 'none'; sandbox"
)

REQUEST_ID_HEADER = "X-Request-Id"


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """Apply the fixed security header set to every response."""

    def __init__(self, app: ASGIApp, settings: Settings) -> None:
        super().__init__(app)
        self._settings = settings

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        response: Response = await call_next(request)
        headers = response.headers
        headers.setdefault("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        headers.setdefault("X-Content-Type-Options", "nosniff")
        headers.setdefault("Referrer-Policy", "no-referrer")
        headers.setdefault("X-Frame-Options", "DENY")
        headers.setdefault("Cross-Origin-Resource-Policy", "same-origin")
        headers.setdefault("Cross-Origin-Opener-Policy", "same-origin")
        headers.setdefault(
            "Permissions-Policy", "geolocation=(), camera=(), microphone=()"
        )
        if self._settings.hsts_max_age:
            headers.setdefault(
                "Strict-Transport-Security",
                f"max-age={self._settings.hsts_max_age}; includeSubDomains; preload",
            )
        if request.url.path.startswith("/admin"):
            headers["Cache-Control"] = "no-store"
            headers["Pragma"] = "no-cache"
        else:
            headers.setdefault("Vary", "Accept-Encoding, Origin")
        return response


class RequestLogMiddleware(BaseHTTPMiddleware):
    """One bounded structured line per request. No bodies, no credentials."""

    async def dispatch(self, request: Request, call_next):  # type: ignore[override]
        request_id = request.headers.get(REQUEST_ID_HEADER) or new_request_id()
        request.state.request_id = request_id[:64]
        started = time.perf_counter()
        status = 500
        try:
            response: Response = await call_next(request)
            status = response.status_code
            response.headers[REQUEST_ID_HEADER] = request.state.request_id
            return response
        finally:
            get_logger().info(
                "request",
                extra={
                    "event": "http_request",
                    "requestId": request.state.request_id,
                    "method": request.method,
                    "path": request.url.path,
                    "query": safe_query(request.query_params.multi_items()),
                    "status": status,
                    "durationMs": round((time.perf_counter() - started) * 1000, 2),
                    "surface": "admin"
                    if request.url.path.startswith("/admin")
                    else "public",
                },
            )
