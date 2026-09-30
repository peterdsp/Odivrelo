"""Product identity, read once from ``brand.json`` at import time.

Nothing in this service may hardcode the product name, slug, domain, bundle id
or environment prefix. A rename is a one-file change at the repository root and
every runtime string follows from it.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

#: ``server/api/publicapi/brand.py`` -> ``server/api/publicapi`` -> ... -> repo root.
_REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_BRAND_PATH = _REPO_ROOT / "brand.json"

_REQUIRED_KEYS = (
    "name",
    "slug",
    "domain",
    "url",
    "supportEmail",
    "bundleId",
    "envPrefix",
    "contractVersion",
    "version",
    "languages",
    "defaultLanguage",
)


@dataclass(frozen=True)
class Brand:
    """Immutable view of ``brand.json``."""

    name: str
    slug: str
    domain: str
    url: str
    support_email: str
    bundle_id: str
    env_prefix: str
    contract_version: str
    version: str
    languages: tuple[str, ...]
    default_language: str
    repository: str | None
    legacy_names: tuple[str, ...]
    tagline: dict[str, str]

    @property
    def correction_base_url(self) -> str:
        return f"{self.url.rstrip('/')}/corrections"

    def correction_url(self, entity_kind: str, entity_id: str) -> str:
        return f"{self.correction_base_url}?entity={entity_kind}:{entity_id}"


def _brand_path() -> Path:
    override = os.environ.get("BRAND_JSON_PATH")
    return Path(override).expanduser().resolve() if override else DEFAULT_BRAND_PATH


@lru_cache(maxsize=1)
def load_brand() -> Brand:
    path = _brand_path()
    try:
        payload: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:  # pragma: no cover - deployment error
        raise RuntimeError(
            f"product identity file not found at {path}; set BRAND_JSON_PATH"
        ) from error
    except json.JSONDecodeError as error:  # pragma: no cover - deployment error
        raise RuntimeError(f"product identity file at {path} is not valid JSON") from error

    missing = [key for key in _REQUIRED_KEYS if not payload.get(key)]
    if missing:
        raise RuntimeError(
            f"product identity file at {path} is missing: {', '.join(missing)}"
        )

    return Brand(
        name=str(payload["name"]),
        slug=str(payload["slug"]),
        domain=str(payload["domain"]),
        url=str(payload["url"]),
        support_email=str(payload["supportEmail"]),
        bundle_id=str(payload["bundleId"]),
        env_prefix=str(payload["envPrefix"]),
        contract_version=str(payload["contractVersion"]),
        version=str(payload["version"]),
        languages=tuple(str(item) for item in payload["languages"]),
        default_language=str(payload["defaultLanguage"]),
        repository=payload.get("repository"),
        legacy_names=tuple(str(item) for item in payload.get("legacyNames", [])),
        tagline=dict(payload.get("tagline", {})),
    )


BRAND = load_brand()
