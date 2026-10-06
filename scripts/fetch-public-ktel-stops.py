#!/usr/bin/env python3
"""Fetch the public read-only KTEL TicketWeb directory for every known tenant.

The public Flutter client uses an encrypted JSON envelope. This command keeps
the same bounded scope as the client: agency metadata, stop groups and stops.
It does not call reservations, seats, payments, ticket issuance, or executions.
"""
from __future__ import annotations

import base64
import json
import re
import subprocess
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "server/ktel-staging/pkg/ktel/operators.json"
OUT = ROOT / "artifacts/ktel-ticketweb/public-stops"
UA = "Odivrelo-public-KTEL-import/1.0 (+mailto:info@peterdsp.dev)"


def encrypt(payload: dict, key: bytes) -> str:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
    result = subprocess.run(
        ["openssl", "enc", "-aes-128-cbc", "-K", key.hex(), "-iv", "00" * 16],
        input=raw,
        capture_output=True,
        check=True,
    )
    return base64.b64encode(result.stdout).decode("ascii")


def decrypt(value: str, key: bytes) -> dict:
    result = subprocess.run(
        ["openssl", "enc", "-d", "-aes-128-cbc", "-K", key.hex(), "-iv", "00" * 16],
        input=base64.b64decode(value),
        capture_output=True,
        check=True,
    )
    return json.loads(result.stdout.decode("utf-8"))


def get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def client_config(tenant: str) -> tuple[str, bytes]:
    script = get(f"https://ktelbus.gr/{tenant}/ticketweb/main.dart.js").decode(
        "utf-8", errors="replace"
    )
    authorization = re.search(r'Authorization","([^"]+)"', script)
    crypto_key = re.search(r'A\.e8\(B\.cj\.d5\("([^"]+)"\)\)', script)
    if not authorization:
        raise RuntimeError(f"could not discover public client config for {tenant}")
    # Newer tenant bundles inline the same public transport key through a
    # generated constant instead of retaining the literal expression above.
    key = (crypto_key.group(1) if crypto_key else "!m3w3bt0k3n2022!").encode("utf-8")
    if len(key) != 16:
        raise RuntimeError(f"unexpected AES key length for {tenant}: {len(key)}")
    return authorization.group(1), key


def api_request(tenant: str, endpoint: str, payload: dict, auth: str, key: bytes) -> dict:
    url = f"https://ktelbus.gr/{tenant}/ticketweb/svc/api/v1/{endpoint}"
    body = json.dumps({"data": encrypt(payload, key)}, separators=(",", ":")).encode()
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Accept": "application/json",
            "Authorization": auth,
            "Content-Type": "application/json;charset=utf-8",
            "User-Agent": UA,
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        envelope = json.loads(response.read().decode("utf-8"))
    if "data" not in envelope:
        raise RuntimeError(f"{tenant}/{endpoint} returned no encrypted data")
    return decrypt(envelope["data"], key)


def main() -> int:
    registry = json.loads(REGISTRY.read_text(encoding="utf-8"))
    tenants = registry["ticketwebTenants"]
    retrieved_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for item in tenants:
        tenant = item["tenant"]
        auth, key = client_config(tenant)
        agency = api_request(tenant, "getAgencyData/", {"url": f"https://ktelbus.gr/{tenant}/"}, auth, key)
        carrier_id = agency["data"]["carrierId"]
        groups = api_request(
            tenant, "booking/stopGroups", {"carrierId": carrier_id, "language": "el"}, auth, key
        )
        stops = api_request(
            tenant, "booking/stops", {"carrierId": carrier_id, "contentLang": "el"}, auth, key
        )
        payload = {
            "dataset": "real",
            "sourceId": "ticketweb",
            "operatorId": item["operatorId"],
            "tenant": tenant,
            "carrierId": carrier_id,
            "retrievedAt": retrieved_at,
            "agency": agency,
            "stopGroups": groups,
            "stops": stops,
        }
        path = OUT / f"{tenant}.json"
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        results.append({
            "tenant": tenant,
            "operatorId": item["operatorId"],
            "carrierId": carrier_id,
            "stopGroups": len(groups.get("StopGroups", [])),
            "stopPayloadKeys": sorted(stops),
            "path": str(path),
        })
        print(json.dumps(results[-1], ensure_ascii=False))
    print(json.dumps({"retrievedAt": retrieved_at, "tenants": len(results), "results": results}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
