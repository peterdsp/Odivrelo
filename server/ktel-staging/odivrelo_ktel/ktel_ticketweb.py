"""Bounded, read-only TicketWeb acquisition primitives.

Live access is disabled unless a maintainer has reviewed the platform terms
and explicitly provides the public-client configuration outside the repo.
This module never calls reservation, seat, payment, or ticket issuance paths.
"""
from __future__ import annotations

import hashlib
import json
import os
import random
import base64
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Iterable, Iterator

READ_ONLY_ENDPOINTS = {
    "agency": "getAgencyData",
    "stop_groups": "booking/stopGroups",
    "stops": "booking/stops",
    "reachable_stops": "booking/reachableStops",
    "executions": "booking/executions",
}
MUTATING_PATH_PARTS = (
    "reservation",
    "seats",
    "book_open",
    "retrieve_open",
    "payment",
    "ticket",
)


@dataclass(frozen=True)
class ExecutionQuery:
    tenant: str
    origin_external_id: str
    destination_external_id: str
    service_date: str


class RequestBudget:
    def __init__(
        self,
        *,
        max_requests: int = 500,
        minimum_interval_seconds: float = 2.0,
        jitter_seconds: float = 0.4,
    ) -> None:
        self.max_requests = max(1, int(max_requests))
        self.minimum_interval_seconds = max(
            0.0, float(minimum_interval_seconds)
        )
        self.jitter_seconds = max(0.0, float(jitter_seconds))
        self.request_count = 0
        self._last_request_at = 0.0

    def before_request(self) -> None:
        if self.request_count >= self.max_requests:
            raise RuntimeError("TicketWeb request budget exhausted")
        wait_for = (
            self._last_request_at
            + self.minimum_interval_seconds
            + random.uniform(0.0, self.jitter_seconds)
            - time.monotonic()
        )
        if wait_for > 0:
            time.sleep(wait_for)
        self.request_count += 1
        self._last_request_at = time.monotonic()


class JsonDiskCache:
    def __init__(self, root: Path, ttl_seconds: int) -> None:
        self.root = root
        self.ttl_seconds = max(0, int(ttl_seconds))

    def _path(self, key: str) -> Path:
        digest = hashlib.sha256(key.encode("utf-8")).hexdigest()
        return self.root / digest[:2] / f"{digest}.json"

    def get(self, key: str) -> Any | None:
        path = self._path(key)
        try:
            age = time.time() - path.stat().st_mtime
            if age > self.ttl_seconds:
                return None
            return json.loads(path.read_text(encoding="utf-8"))
        except (FileNotFoundError, OSError, ValueError):
            return None

    def put(self, key: str, value: Any) -> None:
        path = self._path(key)
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(".tmp")
        temporary.write_text(
            json.dumps(
                value,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ),
            encoding="utf-8",
        )
        os.replace(temporary, path)


class MemoryJsonCache:
    """Per-run cache used when raw TicketWeb retention is not approved."""

    def __init__(self) -> None:
        self.values: dict[str, Any] = {}

    def get(self, key: str) -> Any | None:
        return self.values.get(key)

    def put(self, key: str, value: Any) -> None:
        self.values[key] = value


class TicketWebReadClient:
    def __init__(
        self,
        *,
        url_template: str,
        authorization: str,
        client_key: str | None,
        cache: JsonDiskCache | MemoryJsonCache,
        budget: RequestBudget,
        timeout_seconds: float = 20.0,
        max_response_bytes: int = 10 * 1024 * 1024,
        crypto_key: str | None = None,
    ) -> None:
        if "{tenant}" not in url_template:
            raise ValueError("TicketWeb URL template must contain {tenant}")
        self.url_template = url_template.rstrip("/")
        self.authorization = authorization
        self.client_key = client_key
        self.cache = cache
        self.budget = budget
        self.timeout_seconds = max(1.0, float(timeout_seconds))
        self.max_response_bytes = max(1024, int(max_response_bytes))
        self.crypto_key = crypto_key

    @classmethod
    def from_environment(cls) -> "TicketWebReadClient":
        if os.environ.get("KTEL_TICKETWEB_TERMS_APPROVED") != "1":
            raise RuntimeError(
                "TicketWeb live ingestion is disabled until "
                "KTEL_TICKETWEB_TERMS_APPROVED=1 is set after terms review"
            )
        url_template = os.environ.get("KTEL_TICKETWEB_API_URL_TEMPLATE")
        authorization = os.environ.get("KTEL_TICKETWEB_AUTHORIZATION")
        if not url_template or not authorization:
            raise RuntimeError(
                "KTEL_TICKETWEB_API_URL_TEMPLATE and "
                "KTEL_TICKETWEB_AUTHORIZATION are required"
            )
        crypto_key = os.environ.get("KTEL_TICKETWEB_CRYPTO_KEY")
        if not crypto_key:
            raise RuntimeError(
                "KTEL_TICKETWEB_CRYPTO_KEY is required for the public TicketWeb "
                "AES transport"
            )
        if os.environ.get("KTEL_TICKETWEB_RAW_CACHE_APPROVED") == "1":
            cache: JsonDiskCache | MemoryJsonCache = JsonDiskCache(
                Path(
                    os.environ.get(
                        "KTEL_TICKETWEB_CACHE_DIR",
                        "/home/peterdsp/odivrelo/cache/ktel-ticketweb",
                    )
                ),
                ttl_seconds=int(
                    os.environ.get(
                        "KTEL_TICKETWEB_CACHE_TTL_SECONDS", "21600"
                    )
                ),
            )
        else:
            cache = MemoryJsonCache()
        return cls(
            url_template=url_template,
            authorization=authorization,
            client_key=os.environ.get("KTEL_TICKETWEB_CLIENT_KEY"),
            cache=cache,
            budget=RequestBudget(
                max_requests=int(
                    os.environ.get("KTEL_TICKETWEB_MAX_REQUESTS_PER_RUN", "500")
                ),
                minimum_interval_seconds=float(
                    os.environ.get(
                        "KTEL_TICKETWEB_MIN_INTERVAL_SECONDS", "2.0"
                    )
                ),
                jitter_seconds=float(
                    os.environ.get("KTEL_TICKETWEB_JITTER_SECONDS", "0.4")
                ),
            ),
            crypto_key=crypto_key,
        )

    def _request_body(self, payload: dict[str, Any]) -> bytes:
        """Encode the body used by the public Flutter TicketWeb client.

        TicketWeb's browser client sends ``{data: base64(AES-128-CBC(JSON))}``
        with a zero IV and PKCS#7 padding. The key is deployment configuration,
        because the provider may rotate the public client bundle independently
        of Odivrelo. OpenSSL is used here instead of adding a crypto package to
        the standard-library-only staging runtime.
        """
        encoded = json.dumps(
            payload, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        if not self.crypto_key:
            return json.dumps(payload, separators=(",", ":")).encode("utf-8")
        if len(self.crypto_key.encode("utf-8")) != 16:
            raise ValueError("TicketWeb AES-128 key must be 16 UTF-8 bytes")
        result = subprocess.run(
            [
                "openssl", "enc", "-aes-128-cbc",
                "-K", self.crypto_key.encode("utf-8").hex(),
                "-iv", "00" * 16,
            ],
            input=encoded,
            capture_output=True,
            check=True,
        )
        return json.dumps(
            {"data": base64.b64encode(result.stdout).decode("ascii")},
            separators=(",", ":"),
        ).encode("utf-8")

    def _decode_response(self, result: Any) -> Any:
        if not self.crypto_key or not isinstance(result, dict):
            return result
        encrypted = result.get("data")
        if not isinstance(encrypted, str):
            return result
        decoded = base64.b64decode(encrypted)
        key_bytes = self.crypto_key.encode("utf-8")
        decrypted = subprocess.run(
            [
                "openssl", "enc", "-d", "-aes-128-cbc",
                "-K", key_bytes.hex(), "-iv", "00" * 16,
            ],
            input=decoded,
            capture_output=True,
            check=True,
        )
        return json.loads(decrypted.stdout.decode("utf-8"))

    def request(
        self,
        tenant: str,
        endpoint_name: str,
        payload: dict[str, Any] | None = None,
    ) -> Any:
        if endpoint_name not in READ_ONLY_ENDPOINTS:
            raise ValueError(f"unsupported read-only endpoint: {endpoint_name}")
        path = READ_ONLY_ENDPOINTS[endpoint_name]
        lowered = path.casefold()
        if any(part in lowered for part in MUTATING_PATH_PARTS):
            raise ValueError("mutating TicketWeb endpoint rejected")

        url = f"{self.url_template.format(tenant=tenant)}/{path}"
        body_obj = payload or {}
        cache_key = json.dumps(
            [tenant, endpoint_name, body_obj],
            sort_keys=True,
            separators=(",", ":"),
        )
        cached = self.cache.get(cache_key)
        if cached is not None:
            return cached

        self.budget.before_request()
        body = self._request_body(body_obj)
        headers = {
            "Accept": "application/json",
            "Authorization": self.authorization,
            "Content-Type": "application/json",
            "User-Agent": "Odivrelo-KTEL-Research/1.0 (+mailto:info@peterdsp.dev)",
        }
        if self.client_key:
            header_name = os.environ.get(
                "KTEL_TICKETWEB_CLIENT_KEY_HEADER", "X-Client-Key"
            )
            headers[header_name] = self.client_key
        request = urllib.request.Request(
            url,
            data=body,
            method="POST",
            headers=headers,
        )
        try:
            with urllib.request.urlopen(
                request, timeout=self.timeout_seconds
            ) as response:
                content_length = int(
                    response.headers.get("Content-Length", "0") or 0
                )
                if content_length > self.max_response_bytes:
                    raise RuntimeError("TicketWeb response exceeds size limit")
                raw = response.read(self.max_response_bytes + 1)
                if len(raw) > self.max_response_bytes:
                    raise RuntimeError("TicketWeb response exceeds size limit")
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise RuntimeError(
                f"TicketWeb read failed for {tenant}/{endpoint_name}: {exc}"
            ) from exc
        result = self._decode_response(json.loads(raw.decode("utf-8")))
        self.cache.put(cache_key, result)
        return result


def bounded_execution_plan(
    *,
    tenant: str,
    reachable_by_origin: dict[str, Iterable[str]],
    start_date: date,
    days: int = 14,
    max_origins: int = 100,
    max_destinations_per_origin: int = 100,
    max_queries: int = 2000,
) -> Iterator[ExecutionQuery]:
    """Yield only known reachable directed edges over a bounded date window."""
    if days < 1 or days > 31:
        raise ValueError("days must be between 1 and 31")
    yielded = 0
    origin_items = sorted(reachable_by_origin.items())[:max_origins]
    for origin, destinations in origin_items:
        unique_destinations = sorted(
            {str(item) for item in destinations if str(item) != str(origin)}
        )[:max_destinations_per_origin]
        for day_offset in range(days):
            service_date = (start_date + timedelta(days=day_offset)).isoformat()
            for destination in unique_destinations:
                if yielded >= max_queries:
                    return
                yield ExecutionQuery(
                    tenant=tenant,
                    origin_external_id=str(origin),
                    destination_external_id=destination,
                    service_date=service_date,
                )
                yielded += 1
