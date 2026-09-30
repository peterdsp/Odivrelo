"""Bounded, conditional and rights-aware HTTP acquisition."""
from __future__ import annotations

import hashlib
import json
import os
import random
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional
from urllib.parse import urldefrag, urljoin, urlparse

from odivrelo_pipeline.registry import SourceTarget
from odivrelo_pipeline.state import StateStore, utc_now


def _ticketweb_terms_approved() -> bool:
    """Ticketing acquisition stays off until written terms approval exists.

    The former environment-variable prefixes are honoured so a rename can
    neither accidentally enable nor accidentally disable the gate on an
    existing deployment.
    """
    for name in (
        "ODIVRELO_TICKETWEB_TERMS_APPROVED",
        "PORAVIA_TICKETWEB_TERMS_APPROVED",
        "HODOMAP_TICKETWEB_TERMS_APPROVED",
        "SYRMOS_TICKETWEB_TERMS_APPROVED",
    ):
        if os.environ.get(name) == "1":
            return True
    return False




@dataclass(frozen=True)
class AcquisitionSettings:
    contact_email: str = "info@peterdsp.dev"
    max_requests: int = 100
    minimum_host_interval_seconds: float = 1.5
    jitter_seconds: float = 0.3
    timeout_seconds: float = 20.0


class RequestBudget:
    def __init__(self, settings: AcquisitionSettings) -> None:
        self.settings = settings
        self.request_count = 0
        self._last_request_by_host: Dict[str, float] = {}

    def before_request(self, url: str) -> None:
        if self.request_count >= self.settings.max_requests:
            raise RuntimeError("daily request budget exhausted")
        host = urlparse(url).netloc.casefold()
        previous = self._last_request_by_host.get(host, 0.0)
        wait_for = (
            previous
            + self.settings.minimum_host_interval_seconds
            + random.uniform(0.0, self.settings.jitter_seconds)
            - time.monotonic()
        )
        if wait_for > 0:
            time.sleep(wait_for)
        self.request_count += 1
        self._last_request_by_host[host] = time.monotonic()


class _HrefCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.hrefs: List[str] = []

    def handle_starttag(self, tag: str, attrs: List[Any]) -> None:
        if tag.casefold() != "a":
            return
        for key, value in attrs:
            if key.casefold() == "href" and value:
                self.hrefs.append(str(value))


def discover_candidate_links(base_url: str, body: bytes) -> List[str]:
    parser = _HrefCollector()
    try:
        parser.feed(body.decode("utf-8", errors="replace"))
    except Exception:
        return []

    base_host = urlparse(base_url).netloc.casefold()
    excluded_host_suffixes = {
        "facebook.com",
        "google.com",
        "ibooked.gr",
        "instagram.com",
        "twitter.com",
        "x.com",
        "youtube.com",
    }
    candidates = set()
    for href in parser.hrefs[:2000]:
        absolute, _fragment = urldefrag(urljoin(base_url, href.strip()))
        parsed = urlparse(absolute)
        host = parsed.netloc.casefold()
        if parsed.scheme != "https" or not host:
            continue
        if any(
            host == suffix or host.endswith("." + suffix)
            for suffix in excluded_host_suffixes
        ):
            continue
        if host != base_host:
            candidates.add(absolute)
    return sorted(candidates)[:200]


class SourceAcquirer:
    def __init__(
        self,
        *,
        store: StateStore,
        artifact_root: Path,
        settings: AcquisitionSettings,
        opener: Optional[Any] = None,
    ) -> None:
        self.store = store
        self.artifact_root = artifact_root
        self.settings = settings
        self.budget = RequestBudget(settings)
        self.opener = opener or urllib.request.build_opener()

    def acquire(self, run_id: str, target: SourceTarget) -> Dict[str, Any]:
        blocked_reason = self._blocked_reason(target)
        if blocked_reason:
            self.store.record_event(
                run_id=run_id,
                source_id=target.source_id,
                outcome="skipped",
                error=blocked_reason,
            )
            return {
                "sourceId": target.source_id,
                "outcome": "skipped",
                "reason": blocked_reason,
            }

        previous = self.store.source_state(target.source_id)
        headers = {
            "Accept": ", ".join(target.expected_content_types) or "*/*",
            "User-Agent": "Odivrelo-Source-Monitor/0.1 (+mailto:{})".format(
                self.settings.contact_email
            ),
        }
        if previous.get("etag"):
            headers["If-None-Match"] = previous["etag"]
        if previous.get("last_modified"):
            headers["If-Modified-Since"] = previous["last_modified"]

        try:
            self.budget.before_request(target.url)
            request = urllib.request.Request(
                target.url,
                method="GET",
                headers=headers,
            )
            try:
                response_context = self.opener.open(
                    request,
                    timeout=self.settings.timeout_seconds,
                )
            except urllib.error.HTTPError as exc:
                if exc.code == 304:
                    self.store.record_event(
                        run_id=run_id,
                        source_id=target.source_id,
                        outcome="not_modified",
                        http_status=304,
                        etag=exc.headers.get("ETag"),
                        last_modified=exc.headers.get("Last-Modified"),
                    )
                    return {
                        "sourceId": target.source_id,
                        "outcome": "not_modified",
                        "httpStatus": 304,
                    }
                raise

            with response_context as response:
                status = int(getattr(response, "status", 200))
                content_type = (
                    response.headers.get("Content-Type", "")
                    .split(";", 1)[0]
                    .strip()
                    .casefold()
                )
                self._validate_content_type(target, content_type)
                declared_size = int(
                    response.headers.get("Content-Length", "0") or "0"
                )
                if declared_size > target.max_bytes:
                    raise RuntimeError("declared response exceeds maxBytes")
                body = response.read(target.max_bytes + 1)
                if len(body) > target.max_bytes:
                    raise RuntimeError("response exceeds maxBytes")

                digest = hashlib.sha256(body).hexdigest()
                previous_digest = previous.get("content_sha256")
                outcome = "unchanged" if digest == previous_digest else "changed"
                discovered_links: List[str] = []
                new_discovered_links = 0
                if (
                    content_type == "text/html"
                    and target.source_kind
                    in {"operator_directory", "federation_directory"}
                ):
                    discovered_links = discover_candidate_links(target.url, body)
                    new_discovered_links = self.store.record_discovered_links(
                        target.source_id,
                        discovered_links,
                    )
                artifact_path = None
                if target.stores_body:
                    artifact_path = str(
                        self._persist_artifact(target, digest, content_type, body)
                    )

                self.store.record_event(
                    run_id=run_id,
                    source_id=target.source_id,
                    outcome=outcome,
                    http_status=status,
                    etag=response.headers.get("ETag"),
                    last_modified=response.headers.get("Last-Modified"),
                    content_sha256=digest,
                    content_type=content_type,
                    byte_size=len(body),
                    artifact_path=artifact_path,
                )
                return {
                    "sourceId": target.source_id,
                    "outcome": outcome,
                    "httpStatus": status,
                    "sha256": digest,
                    "byteSize": len(body),
                    "retentionMode": target.retention_mode,
                    "artifactPath": artifact_path,
                    "discoveredLinks": len(discovered_links),
                    "newDiscoveredLinks": new_discovered_links,
                }
        except Exception as exc:
            message = "{}: {}".format(type(exc).__name__, exc)
            self.store.record_event(
                run_id=run_id,
                source_id=target.source_id,
                outcome="failed",
                error=message,
            )
            return {
                "sourceId": target.source_id,
                "outcome": "failed",
                "error": message,
            }

    def _blocked_reason(self, target: SourceTarget) -> Optional[str]:
        if target.rights_status in {"prohibited", "restricted_internal"}:
            return "source rights do not permit automated acquisition"
        if target.stores_body and target.rights_status != "permitted":
            return "body retention requires permitted rights"
        if (
            target.source_kind == "ticketing"
            and not _ticketweb_terms_approved()
        ):
            return "ticketing acquisition disabled until written terms approval"
        return None

    @staticmethod
    def _validate_content_type(
        target: SourceTarget,
        content_type: str,
    ) -> None:
        if not target.expected_content_types or not content_type:
            return
        if not any(
            content_type == expected.casefold()
            or content_type.startswith(expected.casefold() + "+")
            for expected in target.expected_content_types
        ):
            raise RuntimeError(
                "unexpected content type {!r}".format(content_type)
            )

    def _persist_artifact(
        self,
        target: SourceTarget,
        digest: str,
        content_type: str,
        body: bytes,
    ) -> Path:
        suffix = {
            "application/json": ".json",
            "application/pdf": ".pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": ".xlsx",
            "text/html": ".html",
            "text/csv": ".csv",
        }.get(content_type, ".bin")
        destination = (
            self.artifact_root
            / target.source_id
            / digest[:2]
            / (digest + suffix)
        )
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            return destination
        temporary = destination.with_suffix(destination.suffix + ".tmp")
        temporary.write_bytes(body)
        os.replace(str(temporary), str(destination))
        return destination


def is_due(
    target: SourceTarget,
    previous: Dict[str, Any],
    *,
    now: Optional[datetime] = None,
) -> bool:
    if target.cadence == "manual":
        return False
    checked = previous.get("last_checked_at")
    if not checked:
        return True
    current = now or datetime.now(timezone.utc)
    checked_at = datetime.fromisoformat(str(checked).replace("Z", "+00:00"))
    intervals = {
        "daily": timedelta(hours=20),
        "weekly": timedelta(days=6),
        "monthly": timedelta(days=27),
    }
    return current - checked_at >= intervals[target.cadence]


def run_refresh(
    *,
    store: StateStore,
    targets: Iterable[SourceTarget],
    artifact_root: Path,
    report_root: Path,
    settings: AcquisitionSettings,
    force: bool = False,
    source_ids: Optional[List[str]] = None,
    max_sources: Optional[int] = None,
) -> Dict[str, Any]:
    selected_ids = set(source_ids or [])
    selected = [
        target
        for target in targets
        if target.enabled
        and (not selected_ids or target.source_id in selected_ids)
        and (force or is_due(target, store.source_state(target.source_id)))
    ]
    if max_sources is not None:
        selected = selected[: max(0, max_sources)]

    run_id = str(uuid.uuid4())
    store.start_run(run_id, len(selected))
    acquirer = SourceAcquirer(
        store=store,
        artifact_root=artifact_root,
        settings=settings,
    )
    results = [acquirer.acquire(run_id, target) for target in selected]
    counts = {
        "planned": len(selected),
        "checked": sum(
            result["outcome"] not in {"skipped"} for result in results
        ),
        "changed": sum(result["outcome"] == "changed" for result in results),
        "unchanged": sum(result["outcome"] == "unchanged" for result in results),
        "notModified": sum(
            result["outcome"] == "not_modified" for result in results
        ),
        "skipped": sum(result["outcome"] == "skipped" for result in results),
        "failed": sum(result["outcome"] == "failed" for result in results),
        "requests": acquirer.budget.request_count,
    }
    status = "failed" if counts["failed"] and not counts["checked"] else (
        "partial" if counts["failed"] else "succeeded"
    )
    report = {
        "schemaVersion": 1,
        "runId": run_id,
        "generatedAt": utc_now(),
        "status": status,
        "counts": counts,
        "results": results,
        "publicDataPublished": False,
        "requiresReview": counts["changed"] > 0,
    }
    store.finish_run(run_id, report)
    report_root.mkdir(parents=True, exist_ok=True)
    report_path = report_root / "{}.json".format(run_id)
    temporary = report_path.with_suffix(".tmp")
    temporary.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(str(temporary), str(report_path))
    report["reportPath"] = str(report_path)
    return report
