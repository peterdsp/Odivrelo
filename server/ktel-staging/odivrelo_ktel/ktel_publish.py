"""Compile the reviewed KTEL ingestion DB into an atomic read-only artifact."""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from . import ktel_db
from .ktel_registry import seed_registry


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _rows(
    conn: sqlite3.Connection,
    sql: str,
    params: Iterable[Any] = (),
) -> list[dict[str, Any]]:
    return [dict(row) for row in conn.execute(sql, tuple(params)).fetchall()]


def _release_id(payload: dict[str, Any]) -> str:
    body = json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    )
    return hashlib.sha256(body.encode("utf-8")).hexdigest()[:16]


def _insert_rows(
    conn: sqlite3.Connection,
    table: str,
    rows: list[dict[str, Any]],
) -> None:
    if not rows:
        return
    columns = list(rows[0])
    placeholders = ",".join("?" for _ in columns)
    conn.executemany(
        f"INSERT OR REPLACE INTO {table}({','.join(columns)}) "
        f"VALUES ({placeholders})",
        [[row[column] for column in columns] for row in rows],
    )


def compile_public_database(
    *,
    ingest_db_path: str = ktel_db.DEFAULT_KTEL_DB_PATH,
    public_db_path: str = ktel_db.DEFAULT_KTEL_PUBLIC_DB_PATH,
) -> dict[str, Any]:
    """Publish one coherent snapshot without copying staging or raw records."""
    target = Path(public_db_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".tmp")
    if temporary.exists():
        temporary.unlink()

    with ktel_db.connect(ingest_db_path, read_only=True) as source:
        operators = _rows(
            source, "SELECT * FROM ktel_operators ORDER BY id"
        )
        sources = _rows(
            source, "SELECT * FROM ktel_sources ORDER BY id"
        )
        operator_sources = _rows(
            source,
            "SELECT * FROM ktel_operator_sources "
            "ORDER BY operator_id, source_id",
        )
        stop_places = _rows(
            source,
            "SELECT e.* FROM ktel_stop_places e "
            "JOIN ktel_sources s ON s.id=e.source_id "
            "WHERE e.publication_state='published' "
            "AND s.rights_status='allowed' ORDER BY e.id",
        )
        stops = _rows(
            source,
            "SELECT e.* FROM ktel_stops e "
            "JOIN ktel_sources s ON s.id=e.source_id "
            "WHERE e.publication_state='published' "
            "AND s.rights_status='allowed' ORDER BY e.id",
        )
        lines = _rows(
            source,
            "SELECT e.* FROM ktel_lines e "
            "JOIN ktel_sources s ON s.id=e.source_id "
            "WHERE e.publication_state='published' "
            "AND s.rights_status='allowed' ORDER BY e.id",
        )
        patterns = _rows(
            source,
            "SELECT e.* FROM ktel_journey_patterns e "
            "JOIN ktel_sources s ON s.id=e.source_id "
            "WHERE e.publication_state='published' "
            "AND s.rights_status='allowed' ORDER BY e.id",
        )
        calendars = _rows(
            source,
            "SELECT e.* FROM ktel_service_calendars e "
            "JOIN ktel_sources s ON s.id=e.source_id "
            "WHERE e.publication_state='published' "
            "AND s.rights_status='allowed' ORDER BY e.id",
        )
        trips = _rows(
            source,
            "SELECT e.* FROM ktel_trips e "
            "JOIN ktel_sources s ON s.id=e.source_id "
            "WHERE e.publication_state='published' "
            "AND s.rights_status='allowed' ORDER BY e.id",
        )

        place_ids = {row["id"] for row in stop_places}
        stop_ids = {row["id"] for row in stops}
        line_ids = {row["id"] for row in lines}
        pattern_ids = {row["id"] for row in patterns}
        calendar_ids = {row["id"] for row in calendars}
        trip_ids = {row["id"] for row in trips}

        for row in stops:
            if row["stop_place_id"] not in place_ids:
                row["stop_place_id"] = None
        for row in patterns:
            if row["line_id"] not in line_ids:
                row["line_id"] = None
        for row in trips:
            if row["line_id"] not in line_ids:
                row["line_id"] = None
            if row["pattern_id"] not in pattern_ids:
                row["pattern_id"] = None
            if row["calendar_id"] not in calendar_ids:
                row["calendar_id"] = None

        pattern_stops = [
            row for row in _rows(
                source, "SELECT * FROM ktel_pattern_stops ORDER BY pattern_id, stop_sequence"
            )
            if row["pattern_id"] in pattern_ids and row["stop_id"] in stop_ids
        ]
        calendar_exceptions = [
            row for row in _rows(
                source,
                "SELECT * FROM ktel_calendar_exceptions "
                "ORDER BY calendar_id, service_date",
            )
            if row["calendar_id"] in calendar_ids
        ]
        stop_times = [
            row for row in _rows(
                source,
                "SELECT * FROM ktel_stop_times ORDER BY trip_id, stop_sequence",
            )
            if row["trip_id"] in trip_ids and row["stop_id"] in stop_ids
        ]

        release_basis = {
            "operators": operators,
            "sources": sources,
            "operatorSources": operator_sources,
            "stopPlaces": stop_places,
            "stops": stops,
            "lines": lines,
            "patterns": patterns,
            "patternStops": pattern_stops,
            "calendars": calendars,
            "calendarExceptions": calendar_exceptions,
            "trips": trips,
            "stopTimes": stop_times,
        }
        release_id = _release_id(release_basis)

    counts = {
        "stopPlaces": len(stop_places),
        "stops": len(stops),
        "lines": len(lines),
        "journeyPatterns": len(patterns),
        "serviceCalendars": len(calendars),
        "trips": len(trips),
    }
    if target.exists():
        try:
            with ktel_db.connect(str(target), read_only=True) as existing:
                row = existing.execute(
                    """
                    SELECT id FROM ktel_publication_releases
                    WHERE status='published'
                    ORDER BY published_at DESC, created_at DESC
                    LIMIT 1
                    """
                ).fetchone()
                if row and row["id"] == release_id:
                    return {
                        "releaseId": release_id,
                        "publicDbPath": str(target),
                        "counts": counts,
                        "unchanged": True,
                    }
        except (OSError, sqlite3.Error):
            pass

    with ktel_db.connect(str(temporary)) as public:
        ktel_db.migrate(public)
        seed_registry(public)
        public.execute("BEGIN")
        try:
            for table, rows in (
                ("ktel_operators", operators),
                ("ktel_sources", sources),
                ("ktel_operator_sources", operator_sources),
                ("ktel_stop_places", stop_places),
                ("ktel_stops", stops),
                ("ktel_lines", lines),
                ("ktel_journey_patterns", patterns),
                ("ktel_pattern_stops", pattern_stops),
                ("ktel_service_calendars", calendars),
                ("ktel_calendar_exceptions", calendar_exceptions),
                ("ktel_trips", trips),
                ("ktel_stop_times", stop_times),
            ):
                _insert_rows(public, table, rows)
            public.execute(
                """
                INSERT INTO ktel_publication_releases(
                    id, status, created_at, published_at, manifest_hash, notes
                ) VALUES (?, 'published', ?, ?, ?, ?)
                """,
                (
                    release_id,
                    _now_iso(),
                    _now_iso(),
                    hashlib.sha256(
                        json.dumps(
                            release_basis,
                            ensure_ascii=False,
                            sort_keys=True,
                            separators=(",", ":"),
                        ).encode("utf-8")
                    ).hexdigest(),
                    "Atomic compiled public KTEL artifact.",
                ),
            )
            violations = public.execute("PRAGMA foreign_key_check").fetchall()
            if violations:
                raise RuntimeError(
                    f"public KTEL DB failed foreign key check: {violations[:5]}"
                )
            public.execute("COMMIT")
        except Exception:
            public.execute("ROLLBACK")
            raise
        public.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        public.execute("PRAGMA journal_mode = DELETE")

    if target.exists():
        previous = target.with_name(f"{target.stem}-previous{target.suffix}")
        previous_tmp = previous.with_suffix(previous.suffix + ".tmp")
        shutil.copy2(target, previous_tmp)
        os.replace(previous_tmp, previous)
    os.replace(temporary, target)
    for suffix in ("-wal", "-shm"):
        sidecar = Path(f"{temporary}{suffix}")
        if sidecar.exists():
            sidecar.unlink()
    return {
        "releaseId": release_id,
        "publicDbPath": str(target),
        "counts": counts,
        "unchanged": False,
    }
