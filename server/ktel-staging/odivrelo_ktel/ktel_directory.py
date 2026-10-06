"""Read-only procurement helpers for the public KTEL directory.

The directory is intentionally treated as an identity source, not as a
timetable.  This module is usable with a supplied ``fetch`` function so a
refresh can be replayed from fixtures or a cache without making network calls.
It never follows off-site links and never mutates a remote system.
"""
from __future__ import annotations

import hashlib
import html
import re
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timezone
from html.parser import HTMLParser
from typing import Callable, Iterable


DEFAULT_DIRECTORY = "https://ktelbus.com/"
DEFAULT_UA = "Odivrelo-KTEL-Directory/1.0 (+mailto:info@peterdsp.dev)"
Fetcher = Callable[[str], tuple[int, dict[str, str], bytes]]


class CachedRateLimitedFetcher:
    """Deterministic, retry-safe fetcher for directory refreshes."""

    def __init__(self, cache_dir: str | None = None, *, minimum_interval_seconds: float = 1.0, retries: int = 2, timeout_seconds: float = 10.0) -> None:
        import json
        import time
        from pathlib import Path
        self.cache_dir = Path(cache_dir) if cache_dir else None
        self.minimum_interval_seconds = max(0.0, minimum_interval_seconds)
        self.retries = max(0, retries)
        self.timeout_seconds = max(1.0, timeout_seconds)
        self._last_request = 0.0
        self._json = json
        self._time = time

    def __call__(self, url: str) -> tuple[int, dict[str, str], bytes]:
        import urllib.error
        key = hashlib.sha256(url.encode("utf-8")).hexdigest()
        path = self.cache_dir / f"{key}.json" if self.cache_dir else None
        if path and path.exists():
            cached = self._json.loads(path.read_text(encoding="utf-8"))
            return int(cached["status"]), dict(cached["headers"]), bytes.fromhex(cached["body"])
        error: Exception | None = None
        for attempt in range(self.retries + 1):
            delay = self._last_request + self.minimum_interval_seconds - self._time.monotonic()
            if delay > 0:
                self._time.sleep(delay)
            self._last_request = self._time.monotonic()
            try:
                result = _default_fetch(url, timeout_seconds=self.timeout_seconds)
                if path:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(self._json.dumps({"status": result[0], "headers": result[1], "body": result[2].hex()}, sort_keys=True), encoding="utf-8")
                return result
            except (urllib.error.URLError, TimeoutError, OSError) as exc:
                error = exc
                if attempt < self.retries:
                    self._time.sleep(2**attempt)
        raise RuntimeError(f"directory fetch failed for {url}: {error}") from error


def now_utc() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _same_host(url: str, root: str) -> bool:
    return urllib.parse.urlsplit(url).netloc.casefold() == urllib.parse.urlsplit(root).netloc.casefold()


def canonical_url(url: str, base: str = DEFAULT_DIRECTORY) -> str | None:
    value = urllib.parse.urljoin(base, html.unescape(url.strip()))
    parts = urllib.parse.urlsplit(value)
    if parts.scheme not in {"http", "https"} or not parts.netloc:
        return None
    return urllib.parse.urlunsplit((parts.scheme, parts.netloc, parts.path.rstrip("/") or "/", "", ""))


class _LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.links: list[str] = []
        self.text: list[str] = []
        self._title = False
        self.title: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if tag.lower() == "a" and values.get("href"):
            self.links.append(values["href"] or "")
        self._title = tag.lower() == "title"

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() == "title":
            self._title = False

    def handle_data(self, data: str) -> None:
        value = " ".join(data.split())
        if not value:
            return
        self.text.append(value)
        if self._title:
            self.title.append(value)


def _default_fetch(url: str, *, timeout_seconds: float = 30.0) -> tuple[int, dict[str, str], bytes]:
    class _NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None

    request = urllib.request.Request(url, headers={"User-Agent": DEFAULT_UA, "Accept": "text/html,application/xml"})
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        response = opener.open(request, timeout=timeout_seconds)
    except urllib.error.HTTPError as error:
        if 300 <= error.code < 400:
            return error.code, dict(error.headers.items()), error.read(8 * 1024 * 1024 + 1)
        raise
    with response:
        return response.status, dict(response.headers.items()), response.read(8 * 1024 * 1024 + 1)


def discover_operator_urls(
    *,
    root_url: str = DEFAULT_DIRECTORY,
    fetch: Fetcher = _default_fetch,
    max_pages: int = 500,
) -> dict[str, object]:
    """Discover directory pages from sitemaps and bounded same-site links."""
    root = canonical_url(root_url) or DEFAULT_DIRECTORY
    if urllib.parse.urlsplit(root_url).path.casefold().endswith("/category/ktel/"):
        root = root + "/"
    queue: deque[str] = deque([root])
    seen: set[str] = set()
    operators: set[str] = set()
    sitemaps: set[str] = set()
    errors: list[dict[str, object]] = []

    root_path = urllib.parse.urlsplit(root).path.casefold()
    seed_paths = () if "/category/" in root_path or "/ktel-" in root_path else ("sitemap.xml", "robots.txt")
    for candidate in tuple(urllib.parse.urljoin(root, path) for path in seed_paths):
        try:
            status, _, body = fetch(candidate)
            text = body.decode("utf-8", "replace")
            if candidate.endswith("robots.txt"):
                sitemaps.update(line.split(":", 1)[1].strip() for line in text.splitlines() if line.lower().startswith("sitemap:"))
            elif status < 400:
                try:
                    document = ET.fromstring(text)
                    sitemaps.update(node.text.strip() for node in document.iter() if node.tag.rsplit("}", 1)[-1] == "loc" and node.text)
                except ET.ParseError:
                    pass
        except Exception as exc:  # inventory reports are allowed to be partial
            errors.append({"url": candidate, "error": str(exc)})
    queue.extend(canonical_url(url, root) for url in sorted(sitemaps) if _same_host(url, root) and canonical_url(url, root))

    while queue and len(seen) < max(1, max_pages):
        url = queue.popleft()
        if url in seen or not _same_host(url, root):
            continue
        seen.add(url)
        try:
            status, _, body = fetch(url)
        except Exception as exc:
            errors.append({"url": url, "error": str(exc)})
            continue
        if status >= 400:
            errors.append({"url": url, "status": status})
            continue
        text = body.decode("utf-8", "replace")
        if url.endswith(".xml") or "sitemap" in url:
            try:
                document = ET.fromstring(text)
                for node in document.iter():
                    if node.tag.rsplit("}", 1)[-1] == "loc" and node.text:
                        linked = canonical_url(node.text, root)
                        if linked and re.search(r"/(?:ktel[-_/][^/]+|[^/]+[-_]ktel)(?:/|$)", urllib.parse.urlsplit(linked).path.casefold()):
                            operators.add(linked)
                        if linked and linked not in seen:
                            queue.append(linked)
            except ET.ParseError:
                pass
            continue
        parser = _LinkParser()
        parser.feed(text)
        for href in parser.links:
            linked = canonical_url(href, url)
            if not linked or not _same_host(linked, root):
                continue
            path = urllib.parse.urlsplit(linked).path.casefold()
            if re.search(r"/(?:ktel[-_/][^/]+|[^/]+[-_]ktel)(?:/|$)", path):
                operators.add(linked)
            crawlable = (
                "/category/" in path
                or "/ktel-" in path
                or "/page/" in path
                or path.endswith(".xml")
                or path.endswith("/sitemap")
            )
            if crawlable and linked not in seen:
                queue.append(linked)
    return {"rootUrl": root, "operatorUrls": sorted(operators), "visitedUrls": sorted(seen), "sitemaps": sorted(sitemaps), "errors": errors}


def _clean(value: str | None) -> str | None:
    if not value:
        return None
    value = re.sub(r"\s+", " ", html.unescape(value)).strip(" \t\r\n:-")
    return value or None


def _first(patterns: Iterable[str], text: str, flags: int = re.I) -> str | None:
    for pattern in patterns:
        match = re.search(pattern, text, flags)
        if match:
            return _clean(match.group(1))
    return None


def parse_operator_page(url: str, source_html: str, *, retrieved_at: str | None = None) -> dict[str, object]:
    """Extract directory facts without pretending that absent facts exist."""
    parser = _LinkParser()
    parser.feed(source_html)
    text = " ".join(parser.text)
    address_match = re.search(r"<address\b[^>]*>(.*?)</address>", source_html, re.I | re.S)
    address_html = address_match.group(1) if address_match else ""
    address_text = re.sub(r"<br\s*/?>", "\n", address_html, flags=re.I)
    address_text = re.sub(r"<[^>]+>", " ", address_text)
    address_text = html.unescape(address_text)
    address_lines = [_clean(line) for line in address_text.splitlines() if _clean(line)]
    title = _clean(" ".join(parser.title))
    name_el = _first((r"(?:ΚΤΕΛ|ΚΤΕΛ\.)\s+([^|–-]+)", r"(?:operator|εταιρεία)\s*:?\s*([^|]+)"), title or text)
    name_el = name_el or title or urllib.parse.urlsplit(url).path.strip("/").split("/")[-1]
    email = _first((r"mailto:([^\s<>\"']+)", r"\b([A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,})\b"), source_html)
    phone = _first((r"(?:Τηλ\.?|phone)\s*:?\s*((?:\+30\s*)?\d[\d\s-]{6,})",), address_text)
    athens_phone = _first((r"(?:Τηλ\.?\s*Αθηνών|Athens)\s*:?\s*((?:\+30\s*)?\d[\d\s-]{6,})",), address_text)
    fax = _first((r"(?:Fax|Φαξ)\s*:?\s*((?:\+30\s*)?\d[\d\s-]{6,})",), address_text)
    phones = sorted(set(value for value in (phone, athens_phone) if value))
    postal = _first((r"\b(\d{5})\b",), address_text or text)
    address = address_lines[0] if address_lines else _first((r"(?:διεύθυνση|address)\s*:?\s*([^|;]+)",), text)
    city = None
    if address_lines:
        city_match = re.search(r",\s*([^,\d|]+)$", address_lines[0] or "")
        city = _clean(city_match.group(1)) if city_match else None
    website = next((canonical_url(h, url) for h in parser.links if canonical_url(h, url) and urllib.parse.urlsplit(canonical_url(h, url) or "").netloc.casefold() != "ktelbus.com"), None)
    updated = re.search(r'<time[^>]+class=["\'][^"\']*updated[^"\']*["\'][^>]+datetime=["\']([^"\']+)', source_html, re.I)
    fields = {
        "nameEl": name_el,
        "nameEn": _first((r"(?:english|\ben\b)\s*:?\s*([^|]+)",), text),
        "directoryUrl": canonical_url(url),
        "directoryTitle": title,
        "address": address,
        "city": city,
        "postalCode": postal,
        "phones": phones,
        "athensPhone": athens_phone,
        "fax": fax,
        "email": email,
        "officialWebsite": website,
        "sourceLastModified": updated.group(1) if updated else None,
        "retrievedAt": retrieved_at or now_utc(),
    }
    source_ref = {"sourceUrl": canonical_url(url), "sourceType": "federation_directory", "retrievedAt": fields["retrievedAt"], "sourceRecordId": hashlib.sha256(source_html.encode("utf-8")).hexdigest(), "rightsStatus": "unknown", "termsStatus": "unreviewed", "freshnessPolicy": "weekly identity and link diff", "verificationState": "candidate"}
    return {"fields": fields, "provenance": {key: dict(source_ref, originalValue=value) for key, value in fields.items() if value not in (None, [], "")}, "sourceHtmlDigest": source_ref["sourceRecordId"]}


def reconcile_ticketweb_tenants(operators: Iterable[dict[str, object]], tenants: Iterable[dict[str, object]]) -> dict[str, object]:
    """Reconcile by explicit IDs first, then conservative normalized tokens."""
    ops = list(operators)
    remaining = list(tenants)
    matched: list[dict[str, object]] = []
    ambiguous: list[dict[str, object]] = []
    for tenant in remaining[:]:
        explicit = [op for op in ops if str(tenant.get("operatorId") or "") == str(op.get("id") or "")]
        if len(explicit) == 1:
            matched.append({"operator": explicit[0], "tenant": tenant, "match": "registry_id"})
            remaining.remove(tenant)
            continue
        token = re.sub(r"[^a-z0-9]+", "", str(tenant.get("tenant") or "").casefold())
        candidates = [op for op in ops if token and token in re.sub(r"[^a-z0-9]+", "", str(op.get("slug") or op.get("nameEn") or "").casefold())]
        if len(candidates) == 1:
            matched.append({"operator": candidates[0], "tenant": tenant, "match": "slug_token"})
            remaining.remove(tenant)
        elif len(candidates) > 1:
            ambiguous.append({"tenant": tenant, "candidates": candidates})
            remaining.remove(tenant)
    matched_ids = {str(item["operator"].get("id")) for item in matched}
    return {"matched": matched, "operatorsWithoutTicketWeb": [op for op in ops if str(op.get("id")) not in matched_ids], "unmatchedTenants": remaining, "ambiguous": ambiguous}
