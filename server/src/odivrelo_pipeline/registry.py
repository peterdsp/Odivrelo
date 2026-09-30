"""Validated operator and acquisition-source registry."""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import urlparse

RIGHTS_STATES = {
    "permitted",
    "permission_pending",
    "restricted_internal",
    "prohibited",
    "unknown",
}
RETENTION_MODES = {"metadata_only", "raw_timeboxed", "raw_persistent"}
CADENCES = {"daily", "weekly", "monthly", "manual"}


@dataclass(frozen=True)
class Operator:
    operator_id: str
    federation_number: int
    slug: str
    name_en: str
    name_el: str
    directory_url: str


@dataclass(frozen=True)
class SourceTarget:
    source_id: str
    operator_id: Optional[str]
    label: str
    url: str
    source_kind: str
    cadence: str
    enabled: bool
    rights_status: str
    retention_mode: str
    max_bytes: int
    expected_content_types: Tuple[str, ...]

    @property
    def stores_body(self) -> bool:
        return self.retention_mode in {"raw_timeboxed", "raw_persistent"}


@dataclass(frozen=True)
class Registry:
    schema_version: int
    verified_at: str
    operators: Tuple[Operator, ...]
    sources: Tuple[SourceTarget, ...]

    @classmethod
    def load(cls, path: Path) -> "Registry":
        payload = json.loads(path.read_text(encoding="utf-8"))
        if payload.get("schemaVersion") != 1:
            raise ValueError("registry schemaVersion must be 1")

        operator_rows = payload.get("operators")
        source_rows = payload.get("sources")
        if not isinstance(operator_rows, list) or not isinstance(source_rows, list):
            raise ValueError("registry operators and sources must be arrays")

        operators = tuple(_operator_from_json(item) for item in operator_rows)
        sources = tuple(_source_from_json(item) for item in source_rows)
        _validate_operators(operators)
        _validate_sources(operators, sources)

        return cls(
            schema_version=1,
            verified_at=str(payload.get("verifiedAt") or ""),
            operators=operators,
            sources=sources,
        )

    def source_by_id(self) -> Dict[str, SourceTarget]:
        return {item.source_id: item for item in self.sources}

    def enabled_sources(self) -> List[SourceTarget]:
        return [item for item in self.sources if item.enabled]


def _operator_from_json(item: Dict[str, Any]) -> Operator:
    return Operator(
        operator_id=str(item["id"]),
        federation_number=int(item["federationNumber"]),
        slug=str(item["slug"]),
        name_en=str(item["nameEn"]),
        name_el=str(item["nameEl"]),
        directory_url=str(item["directoryUrl"]),
    )


def _source_from_json(item: Dict[str, Any]) -> SourceTarget:
    content_types = item.get("expectedContentTypes", [])
    if not isinstance(content_types, list):
        raise ValueError("expectedContentTypes must be an array")
    return SourceTarget(
        source_id=str(item["id"]),
        operator_id=(
            str(item["operatorId"]) if item.get("operatorId") is not None else None
        ),
        label=str(item["label"]),
        url=str(item["url"]),
        source_kind=str(item["sourceKind"]),
        cadence=str(item["cadence"]),
        enabled=bool(item.get("enabled", False)),
        rights_status=str(item["rightsStatus"]),
        retention_mode=str(item["retentionMode"]),
        max_bytes=int(item.get("maxBytes", 2 * 1024 * 1024)),
        expected_content_types=tuple(str(value) for value in content_types),
    )


def _validate_operators(operators: Tuple[Operator, ...]) -> None:
    numbers = sorted(item.federation_number for item in operators)
    if numbers != list(range(1, 63)):
        raise ValueError("registry must contain federation numbers 1 through 62")
    ids = [item.operator_id for item in operators]
    if len(ids) != len(set(ids)):
        raise ValueError("operator ids must be unique")
    slugs = [item.slug for item in operators]
    if len(slugs) != len(set(slugs)):
        raise ValueError("operator slugs must be unique")


def _validate_sources(
    operators: Tuple[Operator, ...],
    sources: Tuple[SourceTarget, ...],
) -> None:
    operator_ids = {item.operator_id for item in operators}
    source_ids = [item.source_id for item in sources]
    if len(source_ids) != len(set(source_ids)):
        raise ValueError("source ids must be unique")

    for source in sources:
        if source.operator_id and source.operator_id not in operator_ids:
            raise ValueError(
                "source {} references unknown operator {}".format(
                    source.source_id,
                    source.operator_id,
                )
            )
        if source.rights_status not in RIGHTS_STATES:
            raise ValueError(
                "source {} has invalid rightsStatus".format(source.source_id)
            )
        if source.retention_mode not in RETENTION_MODES:
            raise ValueError(
                "source {} has invalid retentionMode".format(source.source_id)
            )
        if source.cadence not in CADENCES:
            raise ValueError(
                "source {} has invalid cadence".format(source.source_id)
            )
        if source.max_bytes < 1024 or source.max_bytes > 50 * 1024 * 1024:
            raise ValueError(
                "source {} maxBytes must be between 1 KiB and 50 MiB".format(
                    source.source_id
                )
            )
        parsed = urlparse(source.url)
        if parsed.scheme != "https" or not parsed.netloc:
            raise ValueError(
                "source {} must use an absolute HTTPS URL".format(source.source_id)
            )
        if source.stores_body and source.rights_status != "permitted":
            raise ValueError(
                "source {} cannot retain bodies without permitted rights".format(
                    source.source_id
                )
            )
