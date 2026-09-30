"""Environment validation. The service fails fast and says exactly what is wrong.

Variable names are derived from ``brand.json.envPrefix``. The documented legacy
``PORAVIA_*`` and ``HODOMAP_*`` spellings, from the product's former names, are
accepted as a fallback for every variable so the Pi deployment can migrate
without a flag day.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Mapping

from .brand import BRAND

#: The most recent former prefix, reported in error messages.
LEGACY_PREFIX = "PORAVIA"
#: Every former prefix still honoured, newest first.
LEGACY_PREFIXES = (LEGACY_PREFIX, "HODOMAP")

#: Hard upper bound for every list endpoint, per the v1 contract.
MAX_PAGE_LIMIT = 200
DEFAULT_PAGE_LIMIT = 50

DATA_MODES = ("real", "demo")


class ConfigError(RuntimeError):
    """Raised at startup with every problem listed at once."""


def env_names(suffix: str) -> tuple[str, str]:
    """Return the branded and legacy spellings of one variable."""
    return f"{BRAND.env_prefix}_{suffix}", f"{LEGACY_PREFIX}_{suffix}"


def _lookup(environ: Mapping[str, str], suffix: str) -> str | None:
    branded, _ = env_names(suffix)
    names = (branded, *(f"{prefix}_{suffix}" for prefix in LEGACY_PREFIXES))
    for name in names:
        value = environ.get(name)
        if value is not None and value.strip() != "":
            return value.strip()
    return None


def _flag(environ: Mapping[str, str], suffix: str, default: bool) -> bool | None:
    raw = _lookup(environ, suffix)
    if raw is None:
        return default
    lowered = raw.lower()
    if lowered in {"1", "true", "yes", "on"}:
        return True
    if lowered in {"0", "false", "no", "off"}:
        return False
    return None


@dataclass(frozen=True)
class Settings:
    """Validated runtime configuration. Never holds a secret in ``repr``."""

    public_db_path: Path
    release_dir: Path
    data_mode: str
    admin_enabled: bool
    ingest_db_path: Path | None
    admin_db_path: Path | None
    cors_allowed_origins: tuple[str, ...]
    hsts_max_age: int
    log_level: str
    commit: str
    built_at: str
    release_json_max_age: int
    _admin_token: str | None = field(default=None, repr=False)

    @property
    def admin_token(self) -> str | None:
        return self._admin_token

    @property
    def manifest_path(self) -> Path:
        return self.release_dir / "manifest.json"

    @property
    def packs_dir(self) -> Path:
        return self.release_dir / "packs"

    @property
    def release_out_dir(self) -> Path:
        """Parent that :func:`generate_public_release` writes ``<slug>/`` into."""
        return self.release_dir.parent

    def __repr__(self) -> str:  # pragma: no cover - trivial
        return (
            f"Settings(public_db_path={self.public_db_path!s}, "
            f"release_dir={self.release_dir!s}, data_mode={self.data_mode!r}, "
            f"admin_enabled={self.admin_enabled!r})"
        )


def load_settings(environ: Mapping[str, str] | None = None) -> Settings:
    """Validate the environment or raise :class:`ConfigError` listing every fault."""
    environ = os.environ if environ is None else environ
    problems: list[str] = []

    def required(suffix: str) -> str | None:
        value = _lookup(environ, suffix)
        if value is None:
            branded, legacy = env_names(suffix)
            problems.append(f"{branded} is required (legacy fallback: {legacy})")
        return value

    public_db = required("PUBLIC_DB_PATH")
    release_dir = required("RELEASE_DIR")

    data_mode = _lookup(environ, "DATA_MODE")
    if data_mode is None:
        branded, legacy = env_names("DATA_MODE")
        problems.append(
            f"{branded} is required and must be one of {', '.join(DATA_MODES)}; "
            "there is no default, because serving invented timetables as real "
            "data is the single most damaging mistake this service can make "
            f"(legacy fallback: {legacy})"
        )
    elif data_mode not in DATA_MODES:
        branded, _ = env_names("DATA_MODE")
        problems.append(
            f"{branded}={data_mode!r} is invalid; expected one of {', '.join(DATA_MODES)}"
        )

    admin_enabled = _flag(environ, "ADMIN_ENABLED", False)
    if admin_enabled is None:
        branded, _ = env_names("ADMIN_ENABLED")
        problems.append(f"{branded} must be a boolean (true/false)")
        admin_enabled = False

    admin_token = (
        environ.get("ADMIN_API_TOKEN")
        or _lookup(environ, "ADMIN_API_TOKEN")
        or None
    )
    if admin_token is not None:
        admin_token = admin_token.strip() or None

    ingest_db = _lookup(environ, "INGEST_DB_PATH")
    admin_db = _lookup(environ, "ADMIN_DB_PATH")
    if admin_enabled:
        if not admin_token:
            problems.append(
                "ADMIN_API_TOKEN is required when the administrative surface is "
                "enabled; the service refuses to start with an open admin origin"
            )
        elif len(admin_token) < 32:
            problems.append(
                "ADMIN_API_TOKEN must be at least 32 characters; the value is "
                "never logged, so length is the only guard available here"
            )
        if not ingest_db:
            branded, _ = env_names("INGEST_DB_PATH")
            problems.append(f"{branded} is required when the admin surface is enabled")
        if not admin_db:
            branded, _ = env_names("ADMIN_DB_PATH")
            problems.append(f"{branded} is required when the admin surface is enabled")

    raw_origins = _lookup(environ, "CORS_ALLOWED_ORIGINS") or ""
    origins = tuple(
        origin.strip() for origin in raw_origins.split(",") if origin.strip()
    )
    if "*" in origins:
        branded, _ = env_names("CORS_ALLOWED_ORIGINS")
        problems.append(
            f"{branded} must be an explicit allowlist; '*' is refused because "
            "the administrative surface is credentialed"
        )

    hsts_raw = _lookup(environ, "HSTS_MAX_AGE") or "63072000"
    try:
        hsts_max_age = int(hsts_raw)
        if hsts_max_age < 0:
            raise ValueError
    except ValueError:
        branded, _ = env_names("HSTS_MAX_AGE")
        problems.append(f"{branded}={hsts_raw!r} must be a non-negative integer")
        hsts_max_age = 63072000

    json_max_age_raw = _lookup(environ, "RELEASE_JSON_MAX_AGE") or "60"
    try:
        release_json_max_age = int(json_max_age_raw)
        if release_json_max_age < 0:
            raise ValueError
    except ValueError:
        branded, _ = env_names("RELEASE_JSON_MAX_AGE")
        problems.append(
            f"{branded}={json_max_age_raw!r} must be a non-negative integer"
        )
        release_json_max_age = 60

    log_level = (_lookup(environ, "LOG_LEVEL") or "INFO").upper()
    if log_level not in {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}:
        branded, _ = env_names("LOG_LEVEL")
        problems.append(f"{branded}={log_level!r} is not a valid logging level")
        log_level = "INFO"

    if problems:
        raise ConfigError(
            "the service cannot start; fix the following environment problems:\n  - "
            + "\n  - ".join(problems)
        )

    assert public_db is not None and release_dir is not None and data_mode is not None
    resolved_release_dir = Path(release_dir).expanduser().resolve()
    if resolved_release_dir.name != BRAND.slug:
        raise ConfigError(
            f"{env_names('RELEASE_DIR')[0]} must point at the '{BRAND.slug}' "
            f"directory written by the release generator, not {resolved_release_dir}"
        )

    return Settings(
        public_db_path=Path(public_db).expanduser().resolve(),
        release_dir=resolved_release_dir,
        data_mode=data_mode,
        admin_enabled=bool(admin_enabled),
        ingest_db_path=Path(ingest_db).expanduser().resolve() if ingest_db else None,
        admin_db_path=Path(admin_db).expanduser().resolve() if admin_db else None,
        cors_allowed_origins=origins,
        hsts_max_age=hsts_max_age,
        log_level=log_level,
        commit=_lookup(environ, "COMMIT") or "unknown",
        built_at=_lookup(environ, "BUILT_AT") or "unknown",
        release_json_max_age=release_json_max_age,
        _admin_token=admin_token if admin_enabled else None,
    )
