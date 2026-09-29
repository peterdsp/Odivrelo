"""National KTEL registry, coverage queries, and validation helpers."""
from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import unicodedata
from pathlib import Path
from typing import Any, Iterable

REGISTRY_PATH = (
    Path(__file__).resolve().parent.parent / "pkg" / "ktel" / "operators.json"
)

# Broad bounding box that contains mainland Greece and its inhabited islands.
# It is a quarantine test, not a claim that every point inside is in Greece.
GREECE_LAT_RANGE = (34.0, 42.5)
GREECE_LON_RANGE = (18.0, 30.5)


def load_registry(path: Path = REGISTRY_PATH) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    official = [
        item for item in payload["operators"]
        if item.get("federationNumber") is not None
    ]
    numbers = sorted(int(item["federationNumber"]) for item in official)
    if numbers != list(range(1, 63)):
        raise ValueError("KTEL registry must contain federation numbers 1 through 62")
    tenants = payload.get("ticketwebTenants", [])
    if len({item["tenant"] for item in tenants}) != len(tenants):
        raise ValueError("TicketWeb tenant codes must be unique")
    return payload


def seed_registry(
    conn: sqlite3.Connection,
    path: Path = REGISTRY_PATH,
) -> dict[str, int]:
    """Idempotently seed the national operator and source registries."""
    payload = load_registry(path)
    verified_at = payload["registryVerifiedAt"]

    conn.execute("BEGIN")
    try:
        for item in payload["operators"]:
            conn.execute(
                """
                INSERT INTO ktel_operators(
                    id, federation_number, slug, name_en, name_el,
                    operator_kind, federation_status, directory_url,
                    registry_verified_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    federation_number=excluded.federation_number,
                    slug=excluded.slug,
                    name_en=excluded.name_en,
                    name_el=excluded.name_el,
                    operator_kind=excluded.operator_kind,
                    federation_status=excluded.federation_status,
                    directory_url=excluded.directory_url,
                    registry_verified_at=excluded.registry_verified_at
                """,
                (
                    item["id"],
                    item.get("federationNumber"),
                    item["slug"],
                    item["nameEn"],
                    item["nameEl"],
                    item.get("operatorKind", "intercity"),
                    item.get("federationStatus", "listed"),
                    item.get("directoryUrl"),
                    verified_at,
                ),
            )

        for source in payload["sources"]:
            conn.execute(
                """
                INSERT INTO ktel_sources(
                    id, label, source_kind, base_url, authority_level,
                    access_mode, license_id, terms_status, terms_url,
                    rights_status, retention_mode, allowed_data_classes,
                    contains_personal_data, contains_commercial_state,
                    retention_days, legal_reviewed_at, refresh_policy
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    label=excluded.label,
                    source_kind=excluded.source_kind,
                    base_url=excluded.base_url,
                    authority_level=excluded.authority_level,
                    access_mode=excluded.access_mode,
                    license_id=excluded.license_id,
                    terms_status=excluded.terms_status,
                    terms_url=excluded.terms_url,
                    rights_status=excluded.rights_status,
                    retention_mode=excluded.retention_mode,
                    allowed_data_classes=excluded.allowed_data_classes,
                    contains_personal_data=excluded.contains_personal_data,
                    contains_commercial_state=excluded.contains_commercial_state,
                    retention_days=excluded.retention_days,
                    legal_reviewed_at=excluded.legal_reviewed_at,
                    refresh_policy=excluded.refresh_policy
                """,
                (
                    source["id"],
                    source["label"],
                    source["sourceKind"],
                    source["baseUrl"],
                    source["authorityLevel"],
                    source["accessMode"],
                    source.get("licenseId"),
                    source["termsStatus"],
                    source.get("termsUrl"),
                    source.get("rightsStatus", "unknown"),
                    source.get("retentionMode", "metadata_only"),
                    json.dumps(
                        source.get("allowedDataClasses", []),
                        ensure_ascii=False,
                        separators=(",", ":"),
                    ),
                    source.get("containsPersonalData", "possible"),
                    1 if source.get("containsCommercialState") else 0,
                    source.get("retentionDays"),
                    source.get("legalReviewedAt"),
                    source["refreshPolicy"],
                ),
            )

        # Every official operator receives an explicit TicketWeb coverage row.
        # A missing tenant is unverified, never interpreted as verified absent.
        official_ids = [
            item["id"] for item in payload["operators"]
            if item.get("federationNumber") is not None
        ]
        for operator_id in official_ids:
            conn.execute(
                """
                INSERT OR IGNORE INTO ktel_operator_sources(
                    operator_id, source_id, coverage_status, notes
                ) VALUES (?, 'ticketweb', 'unverified',
                    'No live tenant was confirmed during the 2026-07-26 sweep.')
                """,
                (operator_id,),
            )

        for tenant in payload["ticketwebTenants"]:
            conn.execute(
                """
                INSERT INTO ktel_operator_sources(
                    operator_id, source_id, external_operator_id, tenant_code,
                    tenant_url, coverage_status, stop_group_count, stop_count,
                    web_active_count, verified_at, notes
                ) VALUES (?, 'ticketweb', ?, ?, ?, 'verified_live', ?, ?, ?, ?, ?)
                ON CONFLICT(operator_id, source_id) DO UPDATE SET
                    external_operator_id=excluded.external_operator_id,
                    tenant_code=excluded.tenant_code,
                    tenant_url=excluded.tenant_url,
                    coverage_status=excluded.coverage_status,
                    stop_group_count=excluded.stop_group_count,
                    stop_count=excluded.stop_count,
                    web_active_count=excluded.web_active_count,
                    verified_at=excluded.verified_at,
                    notes=excluded.notes
                """,
                (
                    tenant["operatorId"],
                    str(tenant["carrierId"]),
                    tenant["tenant"],
                    f"https://ktelbus.gr/{tenant['tenant']}/ticketweb/",
                    int(tenant["stopGroups"]),
                    int(tenant["stops"]),
                    int(tenant["webActiveStops"]),
                    verified_at,
                    "Verified through the public passenger ticket interface.",
                ),
            )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise

    return {
        "officialOperators": len(official_ids),
        "allOperators": len(payload["operators"]),
        "ticketwebTenants": len(payload["ticketwebTenants"]),
    }


def normalize_stop_name(value: str) -> str:
    text = unicodedata.normalize("NFKD", value or "")
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"[^\w]+", " ", text.casefold(), flags=re.UNICODE)
    return " ".join(text.split())


def coordinate_status(
    latitude: float | int | str | None,
    longitude: float | int | str | None,
) -> str:
    if latitude in (None, "") or longitude in (None, ""):
        return "missing"
    try:
        lat = float(latitude)
        lon = float(longitude)
    except (TypeError, ValueError):
        return "out_of_range"
    if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
        return "out_of_range"
    if abs(lat) < 0.0001 and abs(lon) < 0.0001:
        return "placeholder"
    if not (
        GREECE_LAT_RANGE[0] <= lat <= GREECE_LAT_RANGE[1]
        and GREECE_LON_RANGE[0] <= lon <= GREECE_LON_RANGE[1]
    ):
        return "outside_greece"
    return "valid"


def stable_entity_id(*parts: object) -> str:
    canonical = "|".join(str(part or "").strip() for part in parts)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:24]


def content_hash(payload: Any) -> str:
    body = json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    )
    return hashlib.sha256(body.encode("utf-8")).hexdigest()


def operator_rows(
    conn: sqlite3.Connection,
    *,
    official_only: bool = True,
    coverage_status: str | None = None,
) -> list[dict[str, Any]]:
    clauses: list[str] = []
    params: list[Any] = []
    if official_only:
        clauses.append("o.federation_number IS NOT NULL")
    if coverage_status:
        clauses.append("os.coverage_status = ?")
        params.append(coverage_status)
    where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
    rows = conn.execute(
        f"""
        SELECT o.id, o.federation_number, o.slug, o.name_en, o.name_el,
               o.operator_kind, o.federation_status, o.directory_url,
               o.official_site_url, o.registry_verified_at,
               os.tenant_code, os.tenant_url, os.coverage_status,
               os.external_operator_id, os.stop_group_count, os.stop_count,
               os.web_active_count, os.verified_at
        FROM ktel_operators o
        LEFT JOIN ktel_operator_sources os
          ON os.operator_id = o.id AND os.source_id = 'ticketweb'
        {where}
        ORDER BY o.federation_number IS NULL, o.federation_number, o.name_en
        """,
        params,
    ).fetchall()
    return [
        {
            "id": row["id"],
            "federationNumber": row["federation_number"],
            "slug": row["slug"],
            "nameEn": row["name_en"],
            "nameEl": row["name_el"],
            "operatorKind": row["operator_kind"],
            "federationStatus": row["federation_status"],
            "directoryUrl": row["directory_url"],
            "officialSiteUrl": row["official_site_url"],
            "registryVerifiedAt": row["registry_verified_at"],
            "ticketweb": {
                "coverageStatus": row["coverage_status"] or "unverified",
                "tenant": row["tenant_code"],
                "tenantUrl": row["tenant_url"],
                "carrierId": row["external_operator_id"],
                "stopGroups": row["stop_group_count"],
                "stops": row["stop_count"],
                "webActiveStops": row["web_active_count"],
                "verifiedAt": row["verified_at"],
            },
        }
        for row in rows
    ]


def coverage_summary(conn: sqlite3.Connection) -> dict[str, Any]:
    official = conn.execute(
        "SELECT COUNT(*) AS n FROM ktel_operators WHERE federation_number IS NOT NULL"
    ).fetchone()["n"]
    verified_official = conn.execute(
        """
        SELECT COUNT(*) AS n
        FROM ktel_operator_sources os
        JOIN ktel_operators o ON o.id = os.operator_id
        WHERE os.source_id='ticketweb'
          AND os.coverage_status='verified_live'
          AND o.federation_number IS NOT NULL
        """
    ).fetchone()["n"]
    extra_tenants = conn.execute(
        """
        SELECT COUNT(*) AS n
        FROM ktel_operator_sources os
        JOIN ktel_operators o ON o.id = os.operator_id
        WHERE os.source_id='ticketweb'
          AND os.coverage_status='verified_live'
          AND o.federation_number IS NULL
        """
    ).fetchone()["n"]
    totals = conn.execute(
        """
        SELECT COALESCE(SUM(os.stop_group_count), 0) AS stop_groups,
               COALESCE(SUM(os.stop_count), 0) AS stops,
               COALESCE(SUM(os.web_active_count), 0) AS web_active
        FROM ktel_operator_sources os
        JOIN ktel_operators o ON o.id = os.operator_id
        WHERE os.source_id='ticketweb'
          AND os.coverage_status='verified_live'
          AND o.federation_number IS NOT NULL
        """
    ).fetchone()
    entity_counts = {}
    for key, table in (
        ("normalizedStopPlaces", "ktel_stop_places"),
        ("normalizedStops", "ktel_stops"),
        ("lines", "ktel_lines"),
        ("journeyPatterns", "ktel_journey_patterns"),
        ("trips", "ktel_trips"),
    ):
        entity_counts[key] = conn.execute(
            f"SELECT COUNT(*) AS n FROM {table} "
            "WHERE publication_state='published'"
        ).fetchone()["n"]
    operator_coverage: list[dict[str, Any]] = []
    for operator in conn.execute(
        """
        WITH stop_counts AS (
          SELECT operator_id, COUNT(*) AS stops
          FROM ktel_stops
          WHERE publication_state='published'
          GROUP BY operator_id
        ),
        pattern_counts AS (
          SELECT operator_id, COUNT(*) AS patterns,
                 SUM(CASE WHEN geometry_status='reviewed' THEN 1 ELSE 0 END)
                   AS geometries
          FROM ktel_journey_patterns
          WHERE publication_state='published'
          GROUP BY operator_id
        ),
        trip_counts AS (
          SELECT operator_id, COUNT(*) AS trips,
                 SUM(CASE WHEN source_id='ticketweb' THEN 1 ELSE 0 END)
                   AS booking_trips,
                 SUM(CASE WHEN schedule_scope IN (
                    'operator_published','nap_published'
                 ) THEN 1 ELSE 0 END) AS official_trips,
                 SUM(CASE WHEN fare_amount IS NOT NULL THEN 1 ELSE 0 END)
                   AS fares
          FROM ktel_trips
          WHERE publication_state='published'
          GROUP BY operator_id
        )
        SELECT o.id, o.federation_number, o.name_en, o.registry_verified_at,
               os.coverage_status, os.verified_at, os.last_success_at,
               COALESCE(sc.stops, 0) AS stops,
               COALESCE(pc.patterns, 0) AS patterns,
               COALESCE(pc.geometries, 0) AS geometries,
               COALESCE(tc.trips, 0) AS trips,
               COALESCE(tc.booking_trips, 0) AS booking_trips,
               COALESCE(tc.official_trips, 0) AS official_trips,
               COALESCE(tc.fares, 0) AS fares
        FROM ktel_operators o
        LEFT JOIN ktel_operator_sources os
          ON os.operator_id=o.id AND os.source_id='ticketweb'
        LEFT JOIN stop_counts sc ON sc.operator_id=o.id
        LEFT JOIN pattern_counts pc ON pc.operator_id=o.id
        LEFT JOIN trip_counts tc ON tc.operator_id=o.id
        WHERE o.federation_number IS NOT NULL
        ORDER BY o.federation_number
        """
    ):
        if operator["official_trips"]:
            coverage_level = "official_stop_schedule"
        elif operator["booking_trips"]:
            coverage_level = "booking_observed"
        else:
            coverage_level = "directory_only"
        operator_coverage.append({
            "operatorId": operator["id"],
            "federationNumber": operator["federation_number"],
            "operatorName": operator["name_en"],
            "directoryIdentity": "verified",
            "timetableCoverage": coverage_level,
            "ticketwebCoverage": operator["coverage_status"] or "unverified",
            "terminalAndStopCount": operator["stops"],
            "journeyPatternCount": operator["patterns"],
            "publishedTripCount": operator["trips"],
            "reviewedGeometryCount": operator["geometries"],
            "publishedFareObservationCount": operator["fares"],
            "registryVerifiedAt": operator["registry_verified_at"],
            "ticketwebVerifiedAt": operator["verified_at"],
            "lastSuccessfulSourceRetrievalAt": operator["last_success_at"],
            "knownLimitations": (
                "Directory identity only. Timetable, stop, geometry, fare, "
                "and alert coverage remain unverified."
                if coverage_level == "directory_only"
                else "Coverage is limited to currently published reviewed rows."
            ),
        })
    return {
        "officialOperatorCount": official,
        "ticketwebVerifiedOfficialOperators": verified_official,
        "ticketwebUnverifiedOfficialOperators": official - verified_official,
        "ticketwebAdditionalTenants": extra_tenants,
        "ticketwebCoveragePercent": round(
            100.0 * verified_official / official, 1
        ) if official else 0.0,
        "ticketwebInventoryRecords": {
            "stopGroups": totals["stop_groups"],
            "stops": totals["stops"],
            "webActiveStops": totals["web_active"],
            "normalizedOrDeduplicated": False,
        },
        "publishedEntities": entity_counts,
        "operatorCoverage": operator_coverage,
        "journeyAbsenceSemantics": {
            "confirmed_no_service": (
                "An applicable authoritative schedule proves no service."
            ),
            "not_observed": (
                "A bounded booking observation found no sellable execution."
            ),
            "unknown": (
                "Coverage is missing, stale, rights-blocked, or insufficient."
            ),
        },
        "interpretation": (
            "TicketWeb coverage means verified public bookable inventory, "
            "not a complete legal national timetable."
        ),
    }


def source_rows(conn: sqlite3.Connection) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        SELECT id, label, source_kind, base_url, authority_level, access_mode,
               license_id, terms_status, terms_url, rights_status,
               retention_mode, allowed_data_classes, contains_personal_data,
               contains_commercial_state, retention_days, legal_reviewed_at,
               refresh_policy, enabled, notes, updated_at
        FROM ktel_sources
        ORDER BY id
        """
    ).fetchall()
    return [
        {
            "id": row["id"],
            "label": row["label"],
            "sourceKind": row["source_kind"],
            "baseUrl": row["base_url"],
            "authorityLevel": row["authority_level"],
            "accessMode": row["access_mode"],
            "licenseId": row["license_id"],
            "termsStatus": row["terms_status"],
            "termsUrl": row["terms_url"],
            "rightsStatus": row["rights_status"],
            "retentionMode": row["retention_mode"],
            "allowedDataClasses": json.loads(row["allowed_data_classes"] or "[]"),
            "containsPersonalData": row["contains_personal_data"],
            "containsCommercialState": bool(row["contains_commercial_state"]),
            "retentionDays": row["retention_days"],
            "legalReviewedAt": row["legal_reviewed_at"],
            "refreshPolicy": row["refresh_policy"],
            "enabled": bool(row["enabled"]),
            "notes": row["notes"],
            "updatedAt": row["updated_at"],
        }
        for row in rows
    ]


def mark_duplicate_coordinates(
    records: Iterable[dict[str, Any]],
) -> list[dict[str, Any]]:
    """Mark same-operator, same-name, same-coordinate duplicates for review."""
    seen: set[tuple[str, str, float, float]] = set()
    result: list[dict[str, Any]] = []
    for item in records:
        row = dict(item)
        status = coordinate_status(row.get("latitude"), row.get("longitude"))
        row["coordinateStatus"] = status
        if status == "valid":
            key = (
                str(row.get("operatorId") or ""),
                normalize_stop_name(str(row.get("name") or "")),
                round(float(row["latitude"]), 5),
                round(float(row["longitude"]), 5),
            )
            if key in seen:
                row["coordinateStatus"] = "duplicate"
            else:
                seen.add(key)
        result.append(row)
    return result
