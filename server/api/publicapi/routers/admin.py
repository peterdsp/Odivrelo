"""The private administrative surface.

Mounted only when the admin flag is on, guarded by a bearer token, never cached,
and audited on every state change. It is a separate ``APIRouter`` in a separate
module so a deployment can run the public origin from an image that never
imports it.
"""
from __future__ import annotations

import hmac
import sqlite3
from typing import Annotated, Any, Iterator, Literal

from fastapi import APIRouter, Depends, Path, Query, Request, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field

from .. import _staging  # noqa: F401  (installs the staging import path)
from .. import admin_store  # noqa: E402
from .. import release as release_module  # noqa: E402
from ..config import Settings  # noqa: E402
from ..dependencies import Pagination, SettingsDep  # noqa: E402
from ..errors import invalid_request, not_found, unauthorized, unavailable  # noqa: E402
from ..logging_setup import get_logger  # noqa: E402
from ..responses import no_store  # noqa: E402
from poravia_ktel import ktel_db, ktel_release  # noqa: E402
from poravia_ktel.ktel_ingest import REVIEWABLE_TABLES, review_entity  # noqa: E402

router = APIRouter(prefix="/admin", tags=["admin"])

_bearer = HTTPBearer(auto_error=False)

REVIEW_ACTIONS = ("verify", "publish", "quarantine", "reject", "withdraw", "restore")
REVIEWABLE_KINDS = tuple(sorted(REVIEWABLE_TABLES))
QUEUE_STATES = ("candidate", "verified", "quarantined", "stale")


def require_token(
    request: Request,
    settings: SettingsDep,
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(_bearer)
    ] = None,
) -> str:
    """Constant-time bearer check. The token is never logged or echoed."""
    expected = settings.admin_token
    if not expected:
        raise unavailable("the administrative surface is not configured")
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise unauthorized("a bearer token is required")
    if not hmac.compare_digest(credentials.credentials, expected):
        get_logger().warning(
            "admin authentication rejected",
            extra={
                "event": "admin_auth_rejected",
                "path": request.url.path,
                "requestId": getattr(request.state, "request_id", None),
            },
        )
        raise unauthorized("the bearer token is not valid")
    return "admin"


Actor = Annotated[str, Depends(require_token)]


def ingest_db(settings: SettingsDep) -> Iterator[sqlite3.Connection]:
    if settings.ingest_db_path is None:
        raise unavailable("no ingestion database is configured")
    if not settings.ingest_db_path.is_file():
        raise unavailable(f"no ingestion database at {settings.ingest_db_path}")
    connection = ktel_db.connect(str(settings.ingest_db_path))
    try:
        yield connection
    finally:
        connection.close()


IngestDb = Annotated[sqlite3.Connection, Depends(ingest_db)]


def admin_db(settings: SettingsDep) -> Iterator[sqlite3.Connection]:
    if settings.admin_db_path is None:
        raise unavailable("no administrative database is configured")
    with admin_store.connect(settings.admin_db_path) as connection:
        yield connection


AdminDb = Annotated[sqlite3.Connection, Depends(admin_db)]


class ReviewRequest(BaseModel):
    action: Literal[
        "verify", "publish", "quarantine", "reject", "withdraw", "restore"
    ]
    reason: str | None = Field(default=None, max_length=2000)


class CorrectionRequest(BaseModel):
    entityKind: str = Field(min_length=1, max_length=64)
    entityId: str = Field(min_length=1, max_length=128)
    summary: str = Field(min_length=1, max_length=500)
    detail: str | None = Field(default=None, max_length=5000)
    reporterContact: str | None = Field(default=None, max_length=320)


class CorrectionResolution(BaseModel):
    status: Literal["accepted", "rejected", "duplicate"]
    resolution: str | None = Field(default=None, max_length=2000)


class TakedownRequest(BaseModel):
    entityKind: str = Field(min_length=1, max_length=64)
    entityId: str = Field(min_length=1, max_length=128)
    requester: str = Field(min_length=1, max_length=200)
    reason: str = Field(min_length=1, max_length=2000)


def _request_id(request: Request) -> str | None:
    return getattr(request.state, "request_id", None)


@router.get("/review/queue", summary="Entities awaiting a review decision")
def review_queue(
    actor: Actor,
    connection: IngestDb,
    page: Pagination,
    entity_kind: Annotated[
        str | None, Query(description="Restrict the queue to one entity kind.")
    ] = None,
    state: Annotated[
        str | None, Query(description="Restrict the queue to one publication state.")
    ] = None,
) -> Response:
    limit, offset = page
    if entity_kind and entity_kind not in REVIEWABLE_TABLES:
        raise invalid_request(
            f"{entity_kind!r} is not reviewable; expected one of "
            f"{', '.join(REVIEWABLE_KINDS)}",
            "entity_kind",
        )
    if state and state not in QUEUE_STATES:
        raise invalid_request(
            f"{state!r} is not a queue state; expected one of {', '.join(QUEUE_STATES)}",
            "state",
        )
    kinds = [entity_kind] if entity_kind else list(REVIEWABLE_KINDS)
    states = [state] if state else list(QUEUE_STATES)
    entries: list[dict[str, Any]] = []
    for kind in kinds:
        table = REVIEWABLE_TABLES[kind]
        placeholders = ",".join("?" for _ in states)
        for row in connection.execute(
            f"SELECT id, operator_id, source_id, publication_state "
            f"FROM {table} WHERE publication_state IN ({placeholders}) ORDER BY id",
            states,
        ).fetchall():
            entries.append(
                {
                    "entityKind": kind,
                    "entityId": row["id"],
                    "operatorId": row["operator_id"],
                    "sourceId": row["source_id"],
                    "state": row["publication_state"],
                }
            )
    entries.sort(key=lambda item: (item["entityKind"], item["entityId"]))
    return no_store(
        {"queue": entries[offset : offset + limit], "total": len(entries)}
    )


@router.post(
    "/review/{entity_kind}/{entity_id}", summary="Record a review decision"
)
def review(
    request: Request,
    actor: Actor,
    connection: IngestDb,
    audit: AdminDb,
    body: ReviewRequest,
    entity_kind: Annotated[str, Path(description="Reviewable entity kind.")],
    entity_id: Annotated[str, Path(description="Entity id.")],
) -> Response:
    if entity_kind not in REVIEWABLE_TABLES:
        raise invalid_request(
            f"{entity_kind!r} is not reviewable; expected one of "
            f"{', '.join(REVIEWABLE_KINDS)}",
            "entity_kind",
        )
    try:
        review_entity(
            connection,
            entity_kind=entity_kind,
            entity_id=entity_id,
            action=body.action,
            reviewer=actor,
            reason=body.reason,
        )
    except ValueError as error:
        admin_store.record_audit(
            audit,
            actor=actor,
            action=f"review.{body.action}",
            outcome="refused",
            subject_kind=entity_kind,
            subject_id=entity_id,
            request_id=_request_id(request),
            detail={"reason": str(error)},
        )
        message = str(error)
        if message.startswith("unknown "):
            raise not_found(message, "entity_id") from error
        raise invalid_request(message, "action") from error
    event = admin_store.record_audit(
        audit,
        actor=actor,
        action=f"review.{body.action}",
        outcome="succeeded",
        subject_kind=entity_kind,
        subject_id=entity_id,
        request_id=_request_id(request),
        detail={"reason": body.reason},
    )
    return no_store({"reviewed": True, "audit": event})


@router.get("/audit", summary="Administrative audit trail")
def audit_trail(actor: Actor, audit: AdminDb, page: Pagination) -> Response:
    limit, offset = page
    return no_store(admin_store.audit_events(audit, limit=limit, offset=offset))


@router.post("/corrections", status_code=201, summary="Record a correction report")
def create_correction(
    request: Request, actor: Actor, audit: AdminDb, body: CorrectionRequest
) -> Response:
    record = admin_store.create_correction(
        audit,
        entity_kind=body.entityKind,
        entity_id=body.entityId,
        summary=body.summary,
        detail=body.detail,
        reporter_contact=body.reporterContact,
    )
    admin_store.record_audit(
        audit,
        actor=actor,
        action="correction.create",
        outcome="succeeded",
        subject_kind=body.entityKind,
        subject_id=body.entityId,
        request_id=_request_id(request),
        detail={"correctionId": record["id"]},
    )
    return no_store({"correction": record}, status_code=201)


@router.get("/corrections", summary="List correction reports")
def list_corrections(
    actor: Actor,
    audit: AdminDb,
    page: Pagination,
    status: Annotated[
        str | None, Query(description="Restrict to one correction status.")
    ] = None,
) -> Response:
    limit, offset = page
    if status and status not in {"open", "accepted", "rejected", "duplicate"}:
        raise invalid_request(f"{status!r} is not a correction status", "status")
    return no_store(
        admin_store.corrections(audit, status=status, limit=limit, offset=offset)
    )


@router.post("/corrections/{correction_id}", summary="Resolve a correction report")
def resolve_correction(
    request: Request,
    actor: Actor,
    audit: AdminDb,
    body: CorrectionResolution,
    correction_id: Annotated[str, Path(description="Correction id.")],
) -> Response:
    record = admin_store.resolve_correction(
        audit,
        correction_id=correction_id,
        status=body.status,
        resolution=body.resolution,
    )
    if record is None:
        raise not_found(
            f"no open correction with id {correction_id}", "correction_id"
        )
    admin_store.record_audit(
        audit,
        actor=actor,
        action=f"correction.{body.status}",
        outcome="succeeded",
        subject_kind="correction",
        subject_id=correction_id,
        request_id=_request_id(request),
        detail={"resolution": body.resolution},
    )
    return no_store({"correction": record})


@router.post("/takedown", status_code=201, summary="Withdraw an entity on request")
def takedown(
    request: Request,
    actor: Actor,
    connection: IngestDb,
    audit: AdminDb,
    body: TakedownRequest,
) -> Response:
    if body.entityKind not in REVIEWABLE_TABLES:
        raise invalid_request(
            f"{body.entityKind!r} cannot be taken down; expected one of "
            f"{', '.join(REVIEWABLE_KINDS)}",
            "entityKind",
        )
    try:
        review_entity(
            connection,
            entity_kind=body.entityKind,
            entity_id=body.entityId,
            action="withdraw",
            reviewer=actor,
            reason=f"takedown: {body.reason}",
        )
    except ValueError as error:
        admin_store.record_takedown(
            audit,
            entity_kind=body.entityKind,
            entity_id=body.entityId,
            requester=body.requester,
            reason=body.reason,
            status="refused",
        )
        admin_store.record_audit(
            audit,
            actor=actor,
            action="takedown",
            outcome="failed",
            subject_kind=body.entityKind,
            subject_id=body.entityId,
            request_id=_request_id(request),
            detail={"reason": str(error)},
        )
        raise not_found(str(error), "entityId") from error
    record = admin_store.record_takedown(
        audit,
        entity_kind=body.entityKind,
        entity_id=body.entityId,
        requester=body.requester,
        reason=body.reason,
        status="actioned",
    )
    admin_store.record_audit(
        audit,
        actor=actor,
        action="takedown",
        outcome="succeeded",
        subject_kind=body.entityKind,
        subject_id=body.entityId,
        request_id=_request_id(request),
        detail={"takedownId": record["id"], "requester": body.requester},
    )
    return no_store({"takedown": record}, status_code=201)


@router.get("/takedowns", summary="List takedown records")
def list_takedowns(actor: Actor, audit: AdminDb, page: Pagination) -> Response:
    limit, offset = page
    return no_store(admin_store.takedowns(audit, limit=limit, offset=offset))


@router.get("/releases", summary="Release state as this process sees it")
def releases(actor: Actor, settings: SettingsDep) -> Response:
    report = release_module.readiness(settings)
    return no_store(
        {
            "release": report.payload(),
            "releaseDir": str(settings.release_dir),
            "dataMode": settings.data_mode,
        }
    )


@router.post("/releases/publish", summary="Compile and publish a new release")
def publish(request: Request, actor: Actor, audit: AdminDb, settings: SettingsDep) -> Response:
    if settings.ingest_db_path is None:
        raise unavailable("no ingestion database is configured")
    try:
        result = ktel_release.generate_public_release(
            settings.release_out_dir,
            str(settings.ingest_db_path),
            str(settings.public_db_path),
        )
    except (ktel_release.ReleaseConsistencyError, RuntimeError, OSError) as error:
        admin_store.record_audit(
            audit,
            actor=actor,
            action="release.publish",
            outcome="failed",
            request_id=_request_id(request),
            detail={"reason": str(error)[:500]},
        )
        raise unavailable(f"the release could not be published: {error}") from error
    admin_store.record_audit(
        audit,
        actor=actor,
        action="release.publish",
        outcome="succeeded",
        subject_kind="release",
        subject_id=result["releaseId"],
        request_id=_request_id(request),
        detail={"counts": result.get("counts", {})},
    )
    return no_store(
        {
            "releaseId": result["releaseId"],
            "counts": result.get("counts", {}),
            "manifestPath": result["manifestPath"],
        }
    )


@router.post("/releases/rollback", summary="Restore the previous release manifest")
def rollback(request: Request, actor: Actor, audit: AdminDb, settings: SettingsDep) -> Response:
    try:
        manifest = ktel_release.rollback_release(settings.release_dir)
        swapped = release_module.restore_previous_database(
            settings, manifest.get("releaseId")
        )
    except ktel_release.ReleaseConsistencyError as error:
        admin_store.record_audit(
            audit,
            actor=actor,
            action="release.rollback",
            outcome="refused",
            request_id=_request_id(request),
            detail={"reason": str(error)[:500]},
        )
        raise unavailable(str(error)) from error
    admin_store.record_audit(
        audit,
        actor=actor,
        action="release.rollback",
        outcome="succeeded",
        subject_kind="release",
        subject_id=manifest.get("releaseId"),
        request_id=_request_id(request),
        detail={"databaseRestored": swapped},
    )
    return no_store(
        {"releaseId": manifest.get("releaseId"), "databaseRestored": swapped}
    )
