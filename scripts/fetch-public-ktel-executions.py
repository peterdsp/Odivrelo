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


def encrypt(payload: dict) -> str:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
    result = subprocess.run(["openssl", "enc", "-aes-128-cbc", "-K", DEFAULT_KEY.hex(), "-iv", "00" * 16], input=raw, capture_output=True, check=True)
    return base64.b64encode(result.stdout).decode("ascii")


def decrypt(value: str) -> dict:
    result = subprocess.run(["openssl", "enc", "-d", "-aes-128-cbc", "-K", DEFAULT_KEY.hex(), "-iv", "00" * 16], input=base64.b64decode(value), capture_output=True, check=True)
    return json.loads(result.stdout.decode("utf-8"))


def get_json(url: str, authorization: str) -> dict:
    request = urllib.request.Request(url, headers={"Accept": "application/json", "Authorization": authorization, "User-Agent": UA})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def client_config(tenant: str) -> tuple[str, int]:
    script = urllib.request.urlopen(
        urllib.request.Request(f"https://ktelbus.gr/{tenant}/ticketweb/main.dart.js", headers={"User-Agent": UA}), timeout=30
    ).read().decode("utf-8", errors="replace")
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
    request = urllib.request.Request(url, data=body, method="POST", headers={"Accept": "application/json", "Authorization": authorization, "Content-Type": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(request, timeout=45) as response:
        envelope = json.loads(response.read().decode("utf-8"))
    return decrypt(envelope["data"])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--start-date", default=date.today().isoformat())
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--max-origins", type=int, default=40)
    parser.add_argument("--max-queries-per-tenant", type=int, default=120)
    parser.add_argument("--output", default=str(DEFAULT_OUT))
    parser.add_argument("--tenant", action="append", help="limit the run to one or more tenants")
    args = parser.parse_args()
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
            for origin, destinations in reachable.items():
                for destination in destinations:
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
                    if query_count >= args.max_queries_per_tenant:
                        break
                if query_count >= args.max_queries_per_tenant:
                    break
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
