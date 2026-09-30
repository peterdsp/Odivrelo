"""SQLite state and review queue for source acquisition."""
from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class StateStore:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.path = path
        self.connection = sqlite3.connect(str(path))
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA foreign_keys = ON")
        self.connection.execute("PRAGMA journal_mode = WAL")
        self._migrate()

    def close(self) -> None:
        self.connection.close()

    def __enter__(self) -> "StateStore":
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()

    def _migrate(self) -> None:
        self.connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS acquisition_runs (
                run_id TEXT PRIMARY KEY,
                started_at TEXT NOT NULL,
                finished_at TEXT,
                status TEXT NOT NULL,
                planned_count INTEGER NOT NULL DEFAULT 0,
                checked_count INTEGER NOT NULL DEFAULT 0,
                changed_count INTEGER NOT NULL DEFAULT 0,
                unchanged_count INTEGER NOT NULL DEFAULT 0,
                not_modified_count INTEGER NOT NULL DEFAULT 0,
                skipped_count INTEGER NOT NULL DEFAULT 0,
                failed_count INTEGER NOT NULL DEFAULT 0,
                request_count INTEGER NOT NULL DEFAULT 0,
                report_json TEXT
            );

            CREATE TABLE IF NOT EXISTS source_state (
                source_id TEXT PRIMARY KEY,
                etag TEXT,
                last_modified TEXT,
                content_sha256 TEXT,
                content_type TEXT,
                byte_size INTEGER,
                last_checked_at TEXT,
                last_changed_at TEXT,
                last_outcome TEXT,
                last_http_status INTEGER,
                consecutive_failures INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            );

            CREATE TABLE IF NOT EXISTS acquisition_events (
                event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL REFERENCES acquisition_runs(run_id),
                source_id TEXT NOT NULL,
                checked_at TEXT NOT NULL,
                outcome TEXT NOT NULL,
                http_status INTEGER,
                content_sha256 TEXT,
                byte_size INTEGER,
                artifact_path TEXT,
                error TEXT
            );

            CREATE TABLE IF NOT EXISTS review_queue (
                review_id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id TEXT NOT NULL,
                content_sha256 TEXT NOT NULL,
                reason TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TEXT NOT NULL,
                reviewed_at TEXT,
                reviewer TEXT,
                notes TEXT,
                UNIQUE(source_id, content_sha256, reason)
            );

            CREATE TABLE IF NOT EXISTS discovered_links (
                source_id TEXT NOT NULL,
                url TEXT NOT NULL,
                first_seen_at TEXT NOT NULL,
                last_seen_at TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                reviewer TEXT,
                reviewed_at TEXT,
                notes TEXT,
                PRIMARY KEY(source_id, url)
            );
            """
        )
        self.connection.commit()

    def start_run(self, run_id: str, planned_count: int) -> None:
        self.connection.execute(
            """
            INSERT INTO acquisition_runs(
                run_id, started_at, status, planned_count
            ) VALUES (?, ?, 'running', ?)
            """,
            (run_id, utc_now(), planned_count),
        )
        self.connection.commit()

    def record_discovered_links(self, source_id: str, urls: list) -> int:
        now = utc_now()
        new_count = 0
        for url in urls:
            existing = self.connection.execute(
                """
                SELECT 1 FROM discovered_links
                WHERE source_id = ? AND url = ?
                """,
                (source_id, url),
            ).fetchone()
            if existing is None:
                new_count += 1
            self.connection.execute(
                """
                INSERT INTO discovered_links(
                    source_id, url, first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT(source_id, url) DO UPDATE SET
                    last_seen_at = excluded.last_seen_at
                """,
                (source_id, url, now, now),
            )
        self.connection.commit()
        return new_count

    def source_state(self, source_id: str) -> Dict[str, Any]:
        row = self.connection.execute(
            "SELECT * FROM source_state WHERE source_id = ?",
            (source_id,),
        ).fetchone()
        return dict(row) if row else {}

    def record_event(
        self,
        *,
        run_id: str,
        source_id: str,
        outcome: str,
        http_status: Optional[int] = None,
        etag: Optional[str] = None,
        last_modified: Optional[str] = None,
        content_sha256: Optional[str] = None,
        content_type: Optional[str] = None,
        byte_size: Optional[int] = None,
        artifact_path: Optional[str] = None,
        error: Optional[str] = None,
    ) -> None:
        checked_at = utc_now()
        self.connection.execute(
            """
            INSERT INTO acquisition_events(
                run_id, source_id, checked_at, outcome, http_status,
                content_sha256, byte_size, artifact_path, error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                run_id,
                source_id,
                checked_at,
                outcome,
                http_status,
                content_sha256,
                byte_size,
                artifact_path,
                error,
            ),
        )

        previous = self.source_state(source_id)
        failures = (
            int(previous.get("consecutive_failures") or 0) + 1
            if outcome == "failed"
            else 0
        )
        changed_at = (
            checked_at
            if outcome == "changed"
            else previous.get("last_changed_at")
        )
        self.connection.execute(
            """
            INSERT INTO source_state(
                source_id, etag, last_modified, content_sha256, content_type,
                byte_size, last_checked_at, last_changed_at, last_outcome,
                last_http_status, consecutive_failures, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(source_id) DO UPDATE SET
                etag = COALESCE(excluded.etag, source_state.etag),
                last_modified = COALESCE(
                    excluded.last_modified, source_state.last_modified
                ),
                content_sha256 = COALESCE(
                    excluded.content_sha256, source_state.content_sha256
                ),
                content_type = COALESCE(
                    excluded.content_type, source_state.content_type
                ),
                byte_size = COALESCE(excluded.byte_size, source_state.byte_size),
                last_checked_at = excluded.last_checked_at,
                last_changed_at = COALESCE(
                    excluded.last_changed_at, source_state.last_changed_at
                ),
                last_outcome = excluded.last_outcome,
                last_http_status = excluded.last_http_status,
                consecutive_failures = excluded.consecutive_failures,
                last_error = excluded.last_error
            """,
            (
                source_id,
                etag,
                last_modified,
                content_sha256,
                content_type,
                byte_size,
                checked_at,
                changed_at,
                outcome,
                http_status,
                failures,
                error,
            ),
        )
        if outcome == "changed" and content_sha256:
            self.connection.execute(
                """
                INSERT OR IGNORE INTO review_queue(
                    source_id, content_sha256, reason, created_at
                ) VALUES (?, ?, 'source_content_changed', ?)
                """,
                (source_id, content_sha256, checked_at),
            )
        self.connection.commit()

    def finish_run(self, run_id: str, report: Dict[str, Any]) -> None:
        self.connection.execute(
            """
            UPDATE acquisition_runs
            SET finished_at = ?, status = ?, checked_count = ?,
                changed_count = ?, unchanged_count = ?,
                not_modified_count = ?, skipped_count = ?, failed_count = ?,
                request_count = ?, report_json = ?
            WHERE run_id = ?
            """,
            (
                utc_now(),
                report["status"],
                report["counts"]["checked"],
                report["counts"]["changed"],
                report["counts"]["unchanged"],
                report["counts"]["notModified"],
                report["counts"]["skipped"],
                report["counts"]["failed"],
                report["counts"]["requests"],
                json.dumps(report, ensure_ascii=False, sort_keys=True),
                run_id,
            ),
        )
        self.connection.commit()

    def status(self) -> Dict[str, Any]:
        source_counts = {
            row["last_outcome"]: row["count"]
            for row in self.connection.execute(
                """
                SELECT COALESCE(last_outcome, 'never') AS last_outcome,
                       COUNT(*) AS count
                FROM source_state
                GROUP BY COALESCE(last_outcome, 'never')
                """
            )
        }
        pending = self.connection.execute(
            "SELECT COUNT(*) AS count FROM review_queue WHERE status = 'pending'"
        ).fetchone()["count"]
        pending_links = self.connection.execute(
            """
            SELECT COUNT(*) AS count
            FROM discovered_links
            WHERE status = 'pending'
            """
        ).fetchone()["count"]
        last_run = self.connection.execute(
            """
            SELECT run_id, started_at, finished_at, status, planned_count,
                   checked_count, changed_count, failed_count, request_count
            FROM acquisition_runs
            ORDER BY started_at DESC
            LIMIT 1
            """
        ).fetchone()
        return {
            "database": str(self.path),
            "sourceOutcomes": source_counts,
            "pendingReviews": pending,
            "pendingDiscoveredLinks": pending_links,
            "lastRun": dict(last_run) if last_run else None,
        }
