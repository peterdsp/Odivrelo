"""Structured, bounded JSON logging.

Rules this module enforces, not merely documents:

* one JSON object per line on stdout, so a log shipper needs no parser rules;
* no request or response body is ever logged;
* the ``Authorization`` header, any ``token``-like query parameter and any
  field named like a secret are replaced before formatting;
* every string field is truncated, so a hostile URL cannot blow up the log
  pipeline or a disk.
"""
from __future__ import annotations

import json
import logging
import sys
import time
import uuid
from typing import Any, Iterable

MAX_FIELD_CHARS = 512
MAX_MESSAGE_CHARS = 2000
MAX_RECORD_BYTES = 8192

_SECRET_HINTS = (
    "authorization",
    "token",
    "secret",
    "password",
    "cookie",
    "apikey",
    "api_key",
    "bearer",
)

REDACTED = "[redacted]"

#: Reserved attribute names on :class:`logging.LogRecord`, so extras can be split out.
_RESERVED = frozenset(
    vars(logging.LogRecord("", 0, "", 0, "", (), None)).keys()
) | {"message", "asctime", "taskName"}


def _looks_secret(name: str) -> bool:
    lowered = name.lower()
    return any(hint in lowered for hint in _SECRET_HINTS)


def _clip(value: str, limit: int) -> str:
    if len(value) <= limit:
        return value
    return value[: limit - 1] + "…"


def _sanitise(name: str, value: Any, depth: int = 0) -> Any:
    if _looks_secret(name):
        return REDACTED
    if value is None or isinstance(value, (bool, int, float)):
        return value
    if isinstance(value, str):
        return _clip(value, MAX_FIELD_CHARS)
    if depth >= 2:
        return _clip(repr(value), MAX_FIELD_CHARS)
    if isinstance(value, dict):
        return {
            str(key): _sanitise(str(key), item, depth + 1)
            for key, item in list(value.items())[:20]
        }
    if isinstance(value, (list, tuple, set)):
        return [_sanitise(name, item, depth + 1) for item in list(value)[:20]]
    return _clip(repr(value), MAX_FIELD_CHARS)


class JsonFormatter(logging.Formatter):
    """Render a record as one bounded JSON object."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "ts": time.strftime(
                "%Y-%m-%dT%H:%M:%SZ", time.gmtime(record.created)
            ),
            "level": record.levelname,
            "logger": _clip(record.name, 128),
            "message": _clip(record.getMessage(), MAX_MESSAGE_CHARS),
        }
        for key, value in record.__dict__.items():
            if key in _RESERVED or key.startswith("_"):
                continue
            payload[key] = _sanitise(key, value)
        if record.exc_info:
            payload["errorType"] = getattr(record.exc_info[0], "__name__", "error")
            payload["errorMessage"] = _clip(str(record.exc_info[1]), MAX_FIELD_CHARS)
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        if len(body.encode("utf-8")) > MAX_RECORD_BYTES:
            body = json.dumps(
                {
                    "ts": payload["ts"],
                    "level": payload["level"],
                    "logger": payload["logger"],
                    "message": "log record truncated: exceeded the size budget",
                    "event": payload.get("event"),
                },
                ensure_ascii=False,
                separators=(",", ":"),
            )
        return body


def configure_logging(level: str = "INFO", stream: Any | None = None) -> logging.Logger:
    """Install the JSON formatter on the service logger and return it."""
    handler = logging.StreamHandler(stream or sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger("publicapi")
    for existing in list(root.handlers):
        root.removeHandler(existing)
    root.addHandler(handler)
    root.setLevel(level)
    root.propagate = False
    return root


def get_logger(name: str = "publicapi") -> logging.Logger:
    return logging.getLogger(name)


def new_request_id() -> str:
    return uuid.uuid4().hex


def safe_query(pairs: Iterable[tuple[str, str]]) -> dict[str, Any]:
    """Return a redacted, bounded view of query parameters."""
    result: dict[str, Any] = {}
    for index, (key, value) in enumerate(pairs):
        if index >= 20:
            break
        result[_clip(key, 64)] = (
            REDACTED if _looks_secret(key) else _clip(value, 128)
        )
    return result
