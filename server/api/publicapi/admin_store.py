"""Operational store owned by this service: audit trail, corrections, takedowns.

These records belong to the API, not to the compiled-data layer, so they live in
their own database rather than growing the staging schema. Review decisions
themselves are still delegated to ``odivrelo_ktel.ktel_ingest.review_entity``,
which writes ``ktel_review_events`` in the ingestion database; the rows here are
the API-level trail of who called what, from where, and with what outcome.
"""
from __future__ import annotations

import json
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

from contextlib import contextmanager

SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS admin_schema_version (
    version    INTEGER NOT NULL PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE TABLE IF NOT EXISTS admin_audit_events (
    id           TEXT PRIMARY KEY,
    at           TEXT NOT NULL,
    actor        TEXT NOT NULL,
    action       TEXT NOT NULL,
    subject_kind TEXT,
    subject_id   TEXT,
    outcome      TEXT NOT NULL,
    request_id   TEXT,
    detail_json  TEXT NOT NULL DEFAULT '{}',
    CHECK (outcome IN ('succeeded', 'failed', 'refused'))
);

CREATE TABLE IF NOT EXISTS admin_corrections (
    id             TEXT PRIMARY KEY,
    created_at     TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'open',
    entity_kind    TEXT NOT NULL,
    entity_id      TEXT NOT NULL,
    summary        TEXT NOT NULL,
    detail         TEXT,
    reporter_contact TEXT,
    resolution     TEXT,
    resolved_at    TEXT,
    CHECK (status IN ('open', 'accepted', 'rejected', 'duplicate'))
);

CREATE TABLE IF NOT EXISTS admin_takedowns (
    id           TEXT PRIMARY KEY,
    created_at   TEXT NOT NULL,
    entity_kind  TEXT NOT NULL,
    entity_id    TEXT NOT NULL,
    requester    TEXT NOT NULL,
    reason       TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'actioned',
    actioned_at  TEXT,
    CHECK (status IN ('actioned', 'refused'))
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_at ON admin_audit_events(at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_corrections_status
    ON admin_corrections(status, created_at DESC);

INSERT OR IGNORE INTO admin_schema_version(version) VALUES (1);
"""


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:24]}"


@contextmanager
def connect(path: Path | str) -> Iterator[sqlite3.Connection]:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(str(target), isolation_level=None)
    connection.row_factory = sqlite3.Row
    try:
        connection.execute("PRAGMA busy_timeout = 5000")
        connection.execute("PRAGMA journal_mode = WAL")
        connection.executescript(SCHEMA)
        yield connection
    finally:
        connection.close()


def record_audit(
    connection: sqlite3.Connection,
    *,
    actor: str,
    action: str,
    outcome: str,
    subject_kind: str | None = None,
    subject_id: str | None = None,
    request_id: str | None = None,
    detail: dict[str, Any] | None = None,
) -> dict[str, Any]:
    identifier = new_id("aud")
    at = now_iso()
    connection.execute(
        "INSERT INTO admin_audit_events(id, at, actor, action, subject_kind, "
        "subject_id, outcome, request_id, detail_json) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            identifier,
            at,
            actor,
            action,
            subject_kind,
            subject_id,
            outcome,
            request_id,
            json.dumps(detail or {}, ensure_ascii=False, sort_keys=True),
        ),
    )
    return {
        "id": identifier,
        "at": at,
        "actor": actor,
        "action": action,
        "subjectKind": subject_kind,
        "subjectId": subject_id,
        "outcome": outcome,
        "requestId": request_id,
        "detail": detail or {},
    }


def audit_events(
    connection: sqlite3.Connection, *, limit: int, offset: int
) -> dict[str, Any]:
    total = connection.execute(
        "SELECT COUNT(*) AS n FROM admin_audit_events"
    ).fetchone()["n"]
    rows = connection.execute(
        "SELECT * FROM admin_audit_events ORDER BY at DESC, id DESC LIMIT ? OFFSET ?",
        (limit, offset),
    ).fetchall()
    return {
        "events": [
            {
                "id": row["id"],
                "at": row["at"],
                "actor": row["actor"],
                "action": row["action"],
                "subjectKind": row["subject_kind"],
                "subjectId": row["subject_id"],
                "outcome": row["outcome"],
                "requestId": row["request_id"],
                "detail": json.loads(row["detail_json"] or "{}"),
            }
            for row in rows
        ],
        "total": total,
    }


def create_correction(
    connection: sqlite3.Connection,
    *,
    entity_kind: str,
    entity_id: str,
    summary: str,
    detail: str | None,
    reporter_contact: str | None,
) -> dict[str, Any]:
    identifier = new_id("cor")
    created_at = now_iso()
    connection.execute(
        "INSERT INTO admin_corrections(id, created_at, status, entity_kind, "
        "entity_id, summary, detail, reporter_contact) "
        "VALUES (?, ?, 'open', ?, ?, ?, ?, ?)",
        (identifier, created_at, entity_kind, entity_id, summary, detail, reporter_contact),
    )
    return {
        "id": identifier,
        "createdAt": created_at,
        "status": "open",
        "entityKind": entity_kind,
        "entityId": entity_id,
        "summary": summary,
        "detail": detail,
        "reporterContact": reporter_contact,
        "resolution": None,
        "resolvedAt": None,
    }


def resolve_correction(
    connection: sqlite3.Connection,
    *,
    correction_id: str,
    status: str,
    resolution: str | None,
) -> dict[str, Any] | None:
    resolved_at = now_iso()
    cursor = connection.execute(
        "UPDATE admin_corrections SET status=?, resolution=?, resolved_at=? "
        "WHERE id=? AND status='open'",
        (status, resolution, resolved_at, correction_id),
    )
    if cursor.rowcount == 0:
        return None
    return correction(connection, correction_id)


def correction(
    connection: sqlite3.Connection, correction_id: str
) -> dict[str, Any] | None:
    row = connection.execute(
        "SELECT * FROM admin_corrections WHERE id=?", (correction_id,)
    ).fetchone()
    return _correction_row(row) if row else None


def _correction_row(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "id": row["id"],
        "createdAt": row["created_at"],
        "status": row["status"],
        "entityKind": row["entity_kind"],
        "entityId": row["entity_id"],
        "summary": row["summary"],
        "detail": row["detail"],
        "reporterContact": row["reporter_contact"],
        "resolution": row["resolution"],
        "resolvedAt": row["resolved_at"],
    }


def corrections(
    connection: sqlite3.Connection, *, status: str | None, limit: int, offset: int
) -> dict[str, Any]:
    where = "WHERE status = ?" if status else ""
    params: list[Any] = [status] if status else []
    total = connection.execute(
        f"SELECT COUNT(*) AS n FROM admin_corrections {where}", params
    ).fetchone()["n"]
    rows = connection.execute(
        f"SELECT * FROM admin_corrections {where} "
        "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
        [*params, limit, offset],
    ).fetchall()
    return {"corrections": [_correction_row(row) for row in rows], "total": total}


def record_takedown(
    connection: sqlite3.Connection,
    *,
    entity_kind: str,
    entity_id: str,
    requester: str,
    reason: str,
    status: str,
) -> dict[str, Any]:
    identifier = new_id("tkd")
    created_at = now_iso()
    connection.execute(
        "INSERT INTO admin_takedowns(id, created_at, entity_kind, entity_id, "
        "requester, reason, status, actioned_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (
            identifier,
            created_at,
            entity_kind,
            entity_id,
            requester,
            reason,
            status,
            created_at if status == "actioned" else None,
        ),
    )
    return {
        "id": identifier,
        "createdAt": created_at,
        "entityKind": entity_kind,
        "entityId": entity_id,
        "requester": requester,
        "reason": reason,
        "status": status,
    }


def takedowns(
    connection: sqlite3.Connection, *, limit: int, offset: int
) -> dict[str, Any]:
    total = connection.execute(
        "SELECT COUNT(*) AS n FROM admin_takedowns"
    ).fetchone()["n"]
    rows = connection.execute(
        "SELECT * FROM admin_takedowns ORDER BY created_at DESC, id DESC "
        "LIMIT ? OFFSET ?",
        (limit, offset),
    ).fetchall()
    return {
        "takedowns": [
            {
                "id": row["id"],
                "createdAt": row["created_at"],
                "entityKind": row["entity_kind"],
                "entityId": row["entity_id"],
                "requester": row["requester"],
                "reason": row["reason"],
                "status": row["status"],
                "actionedAt": row["actioned_at"],
            }
            for row in rows
        ],
        "total": total,
    }
