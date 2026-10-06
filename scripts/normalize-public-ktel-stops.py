#!/usr/bin/env python3
"""Convert live TicketWeb stop snapshots into the normalized review contract."""
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def number(value):
    if value in (None, ""):
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def normalize(path: Path, retrieved_at: str) -> dict:
    raw = json.loads(path.read_text(encoding="utf-8"))
    operator_id = str(raw["operatorId"])
    source_id = str(raw.get("sourceId", "ticketweb"))
    tenant = str(raw["tenant"])
    groups = raw.get("stopGroups", {}).get("StopGroups", [])
    stops = raw.get("stops", {}).get("Stops", [])

    stop_places = []
    for group in groups:
        external_id = str(group["id"])
        stop_places.append({
            "externalId": external_id,
            "name": str(group.get("name") or f"Stop group {external_id}"),
            "nameEl": group.get("name"),
            "defaultStopExternalId": (
                str(group["defaultStopId"])
                if group.get("defaultStopId") is not None else None
            ),
            "webActive": True,
            "publicAttributes": {
                "tenant": tenant,
                "carrierId": raw.get("carrierId"),
                "axisId": group.get("axisId"),
                "urbanZone": group.get("urbanZone"),
                "ordinal": group.get("ordinal"),
                "providerUserIdentifier": group.get("userIdentifier"),
            },
        })

    normalized_stops = []
    for stop in stops:
        external_id = str(stop["id"])
        normalized_stops.append({
            "externalId": external_id,
            "stopPlaceExternalId": (
                str(stop["stopGroupId"])
                if stop.get("stopGroupId") is not None else None
            ),
            "name": str(stop.get("name") or f"Stop {external_id}"),
            "nameEl": stop.get("name"),
            "address": stop.get("address"),
            "phone": stop.get("phone"),
            "latitude": number(stop.get("latitude")),
            "longitude": number(stop.get("longitude")),
            "webActive": bool(stop.get("webActive")),
            "publicationState": "candidate",
            "publicAttributes": {
                "tenant": tenant,
                "carrierId": raw.get("carrierId"),
                "direction": stop.get("direction"),
                "embarkingSequence": stop.get("embarking_sequency"),
                "disembarkingSequence": stop.get("disembarking_sequency"),
                "providerUserIdentifier": stop.get("userIdentifier"),
            },
        })

    return {
        "dataset": "real",
        "operatorId": operator_id,
        "sourceId": source_id,
        "retrievedAt": retrieved_at,
        "stopPlaces": stop_places,
        "stops": normalized_stops,
        "lines": [],
        "journeyPatterns": [],
        "serviceCalendars": [],
        "trips": [],
        "metadata": {
            "adapter": "ktel-ticketweb-public-client",
            "tenant": tenant,
            "carrierId": raw.get("carrierId"),
            "agency": raw.get("agency"),
            "bookingUrl": f"https://ktelbus.gr/{tenant}/ticketweb/",
            "publicationNote": (
                "Candidate stop directory from a public first-party client; "
                "TicketWeb source rights remain pending explicit review."
            ),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("input_dir", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--retrieved-at", default=now())
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    total_places = total_stops = 0
    for path in sorted(args.input_dir.glob("*.json")):
        payload = normalize(path, args.retrieved_at)
        (args.output_dir / path.name).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        total_places += len(payload["stopPlaces"])
        total_stops += len(payload["stops"])
    print(json.dumps({
        "operators": len(list(args.output_dir.glob("*.json"))),
        "stopPlaces": total_places,
        "stops": total_stops,
        "publicationState": "candidate",
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
