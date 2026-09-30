"""Application factory.

The public surface and the administrative surface are separate routers in
separate modules. ``ADMIN_ENABLED`` decides whether the admin router is mounted
at all, so a public deployment does not merely refuse admin requests: it has no
admin routes, and they are absent from the served OpenAPI document too.
"""
from __future__ import annotations

from typing import Mapping

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .brand import BRAND
from .config import Settings, load_settings
from .errors import install_error_handlers
from .logging_setup import configure_logging, get_logger
from .routers import health, public
from .security import RequestLogMiddleware, SecurityHeadersMiddleware

API_TITLE = f"{BRAND.name} public API"
API_DESCRIPTION = (
    "Read-only public transport data for "
    f"{BRAND.name}, version {BRAND.contract_version} of the published data "
    "contract. Every response carries the release it was served from."
)


def create_app(
    settings: Settings | None = None,
    environ: Mapping[str, str] | None = None,
) -> FastAPI:
    """Build the application, refusing to start on an invalid environment."""
    resolved = settings or load_settings(environ)
    configure_logging(resolved.log_level)

    app = FastAPI(
        title=API_TITLE,
        version=BRAND.contract_version,
        description=API_DESCRIPTION,
        openapi_version="3.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url="/openapi.json",
    )
    app.state.settings = resolved

    install_error_handlers(app)

    app.include_router(public.router)
    app.include_router(health.router)
    if resolved.admin_enabled:
        from .routers import admin

        app.include_router(admin.router)

    # Middleware runs outermost-first in reverse registration order, so the
    # logger wraps the header middleware and sees the final status code.
    app.add_middleware(SecurityHeadersMiddleware, settings=resolved)
    if resolved.cors_allowed_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=list(resolved.cors_allowed_origins),
            allow_credentials=resolved.admin_enabled,
            allow_methods=["GET", "POST", "OPTIONS"],
            allow_headers=["Authorization", "Content-Type", "If-None-Match", "X-Release-Id"],
            expose_headers=["ETag", "X-Release-Id", "X-Request-Id"],
            max_age=600,
        )
    app.add_middleware(RequestLogMiddleware)

    get_logger().info(
        "service configured",
        extra={
            "event": "startup",
            "product": BRAND.name,
            "contractVersion": BRAND.contract_version,
            "dataMode": resolved.data_mode,
            "adminEnabled": resolved.admin_enabled,
            "corsOrigins": len(resolved.cors_allowed_origins),
        },
    )
    return app


def build() -> FastAPI:
    """Entry point for ``uvicorn publicapi.app:build --factory``."""
    return create_app()
