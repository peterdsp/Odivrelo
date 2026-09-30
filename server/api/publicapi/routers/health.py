"""Liveness and readiness probes.

``/healthz`` answers whether the process is up. It must not touch the database,
or a bad release would make an orchestrator kill a healthy process instead of
taking it out of rotation.

``/readyz`` answers whether this process can serve the contract: the compiled
public database exists and is intact, it carries a published release, and that
release agrees with the generated manifest and its packs.
"""
from __future__ import annotations

from fastapi import APIRouter, Response

from .. import release as release_module
from ..brand import BRAND
from ..dependencies import SettingsDep
from ..responses import no_store

router = APIRouter(tags=["operations"])


@router.get("/healthz", summary="Process liveness")
def healthz() -> Response:
    return no_store(
        {
            "status": "ok",
            "product": BRAND.name,
            "contractVersion": BRAND.contract_version,
        }
    )


@router.get(
    "/readyz",
    summary="Release readiness",
    responses={503: {"description": "unavailable"}},
)
def readyz(settings: SettingsDep) -> Response:
    report = release_module.readiness(settings)
    payload = {
        **report.payload(),
        "dataMode": settings.data_mode,
        "contractVersion": BRAND.contract_version,
    }
    return no_store(payload, status_code=200 if report.ready else 503)
