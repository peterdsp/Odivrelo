#!/usr/bin/env python3
"""Bounded, read-only crawl of official KTEL websites and public documents.

This produces procurement evidence, not publishable timetable data. Every HTML
page and document keeps its source URL, retrieval time, digest, response shape,
and an explicit unknown-rights/candidate state until manual review approves it.
The crawler never submits forms, follows off-site links, or calls booking,
reservation, payment, account, or seat-hold actions.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import mimetypes
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, deque
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path


UA = "Odivrelo-KTEL-Public-Research/1.0 (+mailto:info@peterdsp.dev)"
PDF_RE = re.compile(r"\.pdf(?:$|[?#])", re.I)
SKIP_RE = re.compile(r"(?:/wp-admin/|/login|/reservation|/booking|/payment|/checkout|\?[^#]*(?:reserve|seat|payment))", re.I)


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def canonical(value: str, base: str) -> str | None:
    value = html.unescape(value.strip())
    if not value or value.startswith(("mailto:", "tel:", "javascript:", "#")):
        return None
    parsed = urllib.parse.urlsplit(urllib.parse.urljoin(base, value))
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        return None
    path = urllib.parse.quote(parsed.path or "/", safe="/%:@!$&'()*+,;=-._~")
    query = urllib.parse.quote(parsed.query, safe="/?%:@!$&'()*+,;=-._~")
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, path, query, ""))


def same_host(url: str, root: str) -> bool:
    return urllib.parse.urlsplit(url).netloc.casefold() == urllib.parse.urlsplit(root).netloc.casefold()


class Links(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.links: list[str] = []
        self.text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        for key in ("href", "src"):
            if values.get(key):
                self.links.append(values[key] or "")

    def handle_data(self, data: str) -> None:
        value = " ".join(data.split())
        if value:
            self.text.append(value)


class Fetcher:
    def __init__(self, cache: Path, interval: float, retries: int, timeout: float, max_bytes: int) -> None:
        self.cache = cache
        self.interval = max(0.0, interval)
        self.retries = max(0, retries)
        self.timeout = max(2.0, timeout)
        self.max_bytes = max_bytes
        self.last = 0.0

    def get(self, url: str) -> tuple[int, dict[str, str], bytes, str]:
        digest = hashlib.sha256(url.encode()).hexdigest()
        path = self.cache / digest[:2] / f"{digest}.bin"
        meta = path.with_suffix(".json")
        if path.exists() and meta.exists():
            info = json.loads(meta.read_text(encoding="utf-8"))
            return int(info["status"]), dict(info["headers"]), path.read_bytes(), info["retrievedAt"]
        error: Exception | None = None
        for attempt in range(self.retries + 1):
            delay = self.last + self.interval - time.monotonic()
            if delay > 0:
                time.sleep(delay)
            self.last = time.monotonic()
            request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "text/html,application/pdf,*/*"})
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    body = response.read(self.max_bytes + 1)
                    if len(body) > self.max_bytes:
                        raise RuntimeError(f"response exceeds {self.max_bytes} bytes")
                    result = (response.status, dict(response.headers.items()), body, now())
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(result[2])
                meta.write_text(json.dumps({"status": result[0], "headers": result[1], "retrievedAt": result[3]}, sort_keys=True), encoding="utf-8")
                return result
            except (urllib.error.URLError, TimeoutError, OSError, RuntimeError) as exc:
                error = exc
                if attempt < self.retries:
                    time.sleep(2**attempt)
        raise RuntimeError(f"fetch failed: {error}") from error


def pdf_facts(body: bytes, cache_path: Path) -> dict[str, object]:
    digest = hashlib.sha256(body).hexdigest()
    facts: dict[str, object] = {"sha256": digest, "bytes": len(body), "textDigest": None, "pageCount": None, "textSample": ""}
    pdf_path = cache_path.with_suffix(".pdf")
    pdf_path.parent.mkdir(parents=True, exist_ok=True)
    pdf_path.write_bytes(body)
    try:
        info = subprocess.run(["pdfinfo", str(pdf_path)], capture_output=True, text=True, timeout=10, check=False).stdout
        match = re.search(r"^Pages:\s+(\d+)", info, re.M)
        if match:
            facts["pageCount"] = int(match.group(1))
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        text = subprocess.run(["pdftotext", "-layout", str(pdf_path), "-"], capture_output=True, text=True, timeout=20, check=False).stdout
        facts["textDigest"] = hashlib.sha256(text.encode("utf-8")).hexdigest()
        facts["textSample"] = " ".join(text.split())[:1000]
    except (OSError, subprocess.TimeoutExpired):
        pass
    return facts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--cache-dir", default="artifacts/ktel-directory/web-cache")
    parser.add_argument("--max-pages-per-site", type=int, default=30)
    parser.add_argument("--max-total-pages", type=int, default=1000)
    parser.add_argument("--interval", type=float, default=0.5)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--timeout", type=float, default=20)
    args = parser.parse_args()

    inventory = json.loads(Path(args.inventory).read_text(encoding="utf-8"))
    sites = sorted({str(record.get("fields", {}).get("officialWebsite")) for record in inventory.get("records", []) if record.get("fields", {}).get("officialWebsite")})
    fetch = Fetcher(Path(args.cache_dir), args.interval, args.retries, args.timeout, 20 * 1024 * 1024)
    pages: list[dict[str, object]] = []
    documents: list[dict[str, object]] = []
    errors: list[dict[str, object]] = []
    visited: set[str] = set()
    queued: set[str] = set()
    per_site = Counter()

    for site in sites:
        root = canonical(site, site)
        if not root:
            continue
        host = urllib.parse.urlsplit(root).netloc
        queue: deque[str] = deque([root])
        queued.add(root)
        while queue and per_site[host] < args.max_pages_per_site and len(visited) < args.max_total_pages:
            url = queue.popleft()
            if url in visited or not same_host(url, root) or SKIP_RE.search(url):
                continue
            visited.add(url)
            per_site[host] += 1
            try:
                status, headers, body, retrieved_at = fetch.get(url)
            except Exception as exc:
                errors.append({"url": url, "error": str(exc), "retrievedAt": now()})
                continue
            content_type = headers.get("Content-Type", "").split(";", 1)[0].casefold()
            is_pdf = PDF_RE.search(url) or content_type == "application/pdf"
            entry = {
                "url": url,
                "sourceType": "official_operator_web_pdf" if is_pdf else "official_operator_web_page",
                "status": status,
                "contentType": content_type,
                "bytes": len(body),
                "retrievedAt": retrieved_at,
                "sha256": hashlib.sha256(body).hexdigest(),
                "rightsStatus": "unknown",
                "termsStatus": "unreviewed",
                "verificationState": "candidate",
                "freshnessPolicy": "manual review required before publication",
            }
            if is_pdf:
                if status < 400:
                    entry["pdf"] = pdf_facts(body, Path(args.cache_dir) / "pdf" / hashlib.sha256(url.encode()).hexdigest())
                documents.append(entry)
                continue
            parser_html = Links()
            parser_html.feed(body.decode("utf-8", "replace"))
            entry["textSample"] = " ".join(parser_html.text)[:500]
            pages.append(entry)
            if status >= 400:
                continue
            for link in parser_html.links:
                child = canonical(link, url)
                if not child or not same_host(child, root) or SKIP_RE.search(child):
                    continue
                if PDF_RE.search(child):
                    if child not in queued:
                        queued.add(child)
                        queue.append(child)
                elif child not in queued and per_site[host] < args.max_pages_per_site:
                    queued.add(child)
                    queue.append(child)

    result = {
        "retrievedAt": now(),
        "policy": {"readOnly": True, "offSiteLinks": "not followed", "mutatingEndpoints": "not called", "rights": "unknown until manual review", "publishable": False},
        "sites": sites,
        "siteCount": len(sites),
        "pageCount": len(pages),
        "documentCount": len(documents),
        "errorCount": len(errors),
        "pages": pages,
        "documents": documents,
        "errors": errors,
        "coverageByHost": [{"host": host, "pages": count} for host, count in sorted(per_site.items())],
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"sites": len(sites), "pages": len(pages), "documents": len(documents), "errors": len(errors), "output": str(output)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
