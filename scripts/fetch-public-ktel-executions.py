#!/usr/bin/env python3
"""Bounded read-only collection of public TicketWeb route executions.

The collector discovers reachable directed stop pairs, queries a short rolling
service-date window, and stores raw response references plus normalized
candidate journeys. It never selects seats or submits a reservation.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import subprocess
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from odivrelo_ktel.ktel_ticketweb import normalize_journey_response

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "server/ktel-staging/pkg/ktel/operators.json"
STOPS = ROOT / "artifacts/ktel-ticketweb/public-stops"
DEFAULT_OUT = ROOT / "artifacts/ktel-ticketweb/public-executions"
UA = "Odivrelo-public-KTEL-executions/1.0 (+mailto:info@peterdsp.dev)"
DEFAULT_KEY = b"!m3w3bt0k3n2022!"

_MIN_INTERVAL = 0.0
_LAST_CALL = 0.0


def pace() -> None:
    """Space out requests so a bounded daily run stays a polite read-only crawl."""
    global _LAST_CALL
    if _MIN_INTERVAL <= 0:
        return
    wait = _MIN_INTERVAL - (time.monotonic() - _LAST_CALL)
    if wait > 0:
        time.sleep(wait)
    _LAST_CALL = time.monotonic()



def encrypt(payload: dict) -> str:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
    result = subprocess.run(["openssl", "enc", "-aes-128-cbc", "-K", DEFAULT_KEY.hex(), "-iv", "00" * 16], input=raw, capture_output=True, check=True)
    return base64.b64encode(result.stdout).decode("ascii")


def decrypt(value: str) -> dict:
    result = subprocess.run(["openssl", "enc", "-d", "-aes-128-cbc", "-K", DEFAULT_KEY.hex(), "-iv", "00" * 16], input=base64.b64decode(value), capture_output=True, check=True)
    return json.loads(result.stdout.decode("utf-8"))


TRANSIENT_STATUS = {429, 500, 502, 503, 504}
RETRY_ATTEMPTS = 3


def with_retry(what: str, call):
    """Retry a read on a transient server error, backing off between tries.

    A single 500 or 502 used to cost the whole tenant: the collector recorded
    the error and moved on, so that operator contributed nothing to the release
    even though the next attempt would have succeeded.
    """
    delay = 4.0
    for attempt in range(1, RETRY_ATTEMPTS + 1):
        try:
            return call()
        except urllib.error.HTTPError as error:
            if error.code not in TRANSIENT_STATUS or attempt == RETRY_ATTEMPTS:
                raise
            print(json.dumps({"retry": what, "status": error.code, "attempt": attempt, "sleep": delay}))
        except urllib.error.URLError as error:
            if attempt == RETRY_ATTEMPTS:
                raise
            print(json.dumps({"retry": what, "reason": str(error.reason), "attempt": attempt, "sleep": delay}))
        time.sleep(delay)
        delay *= 3
    raise RuntimeError(f"unreachable retry state for {what}")


def get_json(url: str, authorization: str) -> dict:
    request = urllib.request.Request(url, headers={"Accept": "application/json", "Authorization": authorization, "User-Agent": UA})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def client_config(tenant: str) -> tuple[str, int]:
    def fetch_bundle() -> str:
        pace()
        return urllib.request.urlopen(
            urllib.request.Request(f"https://ktelbus.gr/{tenant}/ticketweb/main.dart.js", headers={"User-Agent": UA}), timeout=30
        ).read().decode("utf-8", errors="replace")

    script = with_retry(f"{tenant}:bundle", fetch_bundle)
    authorization = re.search(r'Authorization","([^"]+)"', script)
    if not authorization:
        raise RuntimeError(f"public authorization marker missing for {tenant}")
    auth_value = authorization.group(1)
    probe = api_request(tenant, "getAgencyData", {"url": f"https://ktelbus.gr/{tenant}/"}, auth_value)
    agency = probe["data"]
    return auth_value, int(agency["carrierId"])


def api_request(tenant: str, endpoint: str, payload: dict, authorization: str) -> dict:
    url = f"https://ktelbus.gr/{tenant}/ticketweb/svc/api/v1/{endpoint}/"
    body = json.dumps({"data": encrypt(payload)}, separators=(",", ":")).encode()

    def call() -> dict:
        pace()
        request = urllib.request.Request(url, data=body, method="POST", headers={"Accept": "application/json", "Authorization": authorization, "Content-Type": "application/json", "User-Agent": UA})
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.loads(response.read().decode("utf-8"))

    envelope = with_retry(f"{tenant}:{endpoint}", call)
    return decrypt(envelope["data"])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--start-date", default=date.today().isoformat())
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--max-origins", type=int, default=40)
    parser.add_argument("--max-queries-per-tenant", type=int, default=120)
    parser.add_argument("--output", default=str(DEFAULT_OUT))
    parser.add_argument("--tenant", action="append", help="limit the run to one or more tenants")
    parser.add_argument("--min-interval", type=float, default=1.5, help="minimum seconds between requests")
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="leave tenants that already have an output file alone, to top up only the ones that failed",
    )
    args = parser.parse_args()
    global _MIN_INTERVAL
    _MIN_INTERVAL = max(0.0, args.min_interval)
    if not 1 <= args.days <= 14:
        raise SystemExit("--days must be between 1 and 14")
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    tenants = json.loads(REGISTRY.read_text(encoding="utf-8"))["ticketwebTenants"]
    if args.tenant:
        tenants = [item for item in tenants if item["tenant"] in set(args.tenant)]
    start = date.fromisoformat(args.start_date)
    run_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    summary = []
    for item in tenants:
        tenant = item["tenant"]
        if args.skip_existing and (output / f"{tenant}.json").exists():
            summary.append({"tenant": tenant, "skipped": "already collected"})
            print(json.dumps(summary[-1], ensure_ascii=False))
            continue
        path = STOPS / f"{tenant}.json"
        if not path.exists():
            summary.append({"tenant": tenant, "error": "missing stop snapshot"})
            continue
        snapshot = json.loads(path.read_text(encoding="utf-8"))
        stops = snapshot["stops"].get("Stops", [])
        active = [s for s in stops if s.get("webActive") and s.get("direction") == "OUT"]
        origins = sorted(active, key=lambda s: (0 if any(token in str(s.get("name", "")).casefold() for token in ("σταθμ", "athena", "αθηνα", "tripoli", "τριπολ")) else 1, str(s.get("name", ""))))[: args.max_origins]
        try:
            authorization, carrier_id = client_config(tenant)
            reachable = {}
            raw_reachable = {}
            for origin in origins:
                response = api_request(tenant, "booking/reachableStops", {"carrierId": carrier_id, "stopId": origin["id"], "language": "el"}, authorization)
                raw_reachable[str(origin["id"])] = response
                reachable[str(origin["id"])] = [x.get("stopId") for x in response.get("reachableStops", []) if x.get("stopId")]
            rows = []
            raw_executions = []
            query_count = 0

            # Interleave the origin/destination pairs rather than walking one
            # origin to exhaustion. The budget is pairs times days, so a
            # depth-first walk spent an entire tenant's allowance on the first
            # origin's first few destinations: one tenant whose first origin was
            # a busy terminal returned thousands of journeys while another,
            # with 1,823 reachable destinations, returned none at all. Taking
            # the nth destination of every origin in turn samples the whole
            # network instead of one corner of it.
            ordered_pairs: list[tuple[str, object]] = []
            if reachable:
                for index in range(max(len(v) for v in reachable.values())):
                    for origin, destinations in reachable.items():
                        if index < len(destinations):
                            ordered_pairs.append((origin, destinations[index]))

            for origin, destination in ordered_pairs:
                if query_count >= args.max_queries_per_tenant:
                    break
                for offset in range(args.days):
                    if query_count >= args.max_queries_per_tenant:
                        break
                    day = start + timedelta(days=offset)
                    payload = {"carriersId": carrier_id, "paymentChannelId": 1, "language": "el", "tripType": 1, "stopFromId": int(origin), "stopToId": int(destination), "stopGroupFromId": None, "stopGroupToId": None, "ticketsPerPassengerType": {"1": 1}, "deviceUuid": "E-TICKETING", "reservationCode": None, "dateOutbound": day.strftime("%d/%m/%Y 00:00"), "dateReturn": None, "ticketMaterialId": 4}
                    try:
                        response = api_request(tenant, "booking/executions", payload, authorization)
                    except Exception as exc:
                        raw_executions.append({"query": payload, "error": str(exc)})
                        query_count += 1
                        continue
                    source_url = f"https://ktelbus.gr/{tenant}/ticketweb/#/routeExecutions"
                    rows.extend(normalize_journey_response(response, tenant=tenant, source_url=source_url, retrieved_at=run_at))
                    raw_executions.append({"query": payload, "response": response})
                    query_count += 1
            result = {"dataset": "real", "sourceId": "ticketweb", "tenant": tenant, "operatorId": item["operatorId"], "carrierId": carrier_id, "retrievedAt": run_at, "policy": {"readOnly": True, "rawResponseDigest": hashlib.sha256(json.dumps(raw_executions, ensure_ascii=False, sort_keys=True).encode()).hexdigest()}, "reachable": raw_reachable, "executions": raw_executions, "normalizedJourneys": rows}
            (output / f"{tenant}.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            summary.append({"tenant": tenant, "carrierId": carrier_id, "origins": len(origins), "queries": query_count, "journeys": len(rows)})
            print(json.dumps(summary[-1], ensure_ascii=False))
        except Exception as exc:
            summary.append({"tenant": tenant, "error": str(exc)})
            print(json.dumps(summary[-1], ensure_ascii=False))
        (output / "_manifest.json").write_text(json.dumps({"retrievedAt": run_at, "tenants": len(tenants), "summary": summary}, ensure_ascii=False, indent=2), encoding="utf-8")
    report = {"retrievedAt": run_at, "tenants": len(tenants), "summary": summary}
    (output / "_manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
