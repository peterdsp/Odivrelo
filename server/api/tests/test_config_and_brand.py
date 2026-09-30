"""Environment validation, brand indirection and the staging bootstrap."""
from __future__ import annotations

import json
from pathlib import Path

import pytest
from conftest import ADMIN_TOKEN, base_environ

from publicapi import _staging
from publicapi.brand import BRAND, Brand, load_brand
from publicapi.config import (
    DATA_MODES,
    DEFAULT_PAGE_LIMIT,
    LEGACY_PREFIX,
    MAX_PAGE_LIMIT,
    ConfigError,
    Settings,
    env_names,
    load_settings,
)

REPO_ROOT = Path(__file__).resolve().parents[3]


# --------------------------------------------------------------------------- #
# Product identity
# --------------------------------------------------------------------------- #

def test_the_brand_is_read_from_brand_json_and_nothing_is_hardcoded():
    payload = json.loads((REPO_ROOT / "brand.json").read_text(encoding="utf-8"))
    assert BRAND.name == payload["name"]
    assert BRAND.slug == payload["slug"]
    assert BRAND.domain == payload["domain"]
    assert BRAND.bundle_id == payload["bundleId"]
    assert BRAND.env_prefix == payload["envPrefix"]
    assert BRAND.contract_version == payload["contractVersion"]
    assert list(BRAND.languages) == payload["languages"]
    assert isinstance(BRAND, Brand)


def test_no_product_name_is_hardcoded_in_the_package():
    """The package is brand neutral: identity only ever arrives from brand.json."""
    package = REPO_ROOT / "server" / "api" / "publicapi"
    forbidden = {BRAND.name.lower(), BRAND.slug.lower(), BRAND.domain.lower()}
    offenders: list[str] = []
    for source in sorted(package.rglob("*.py")):
        text = source.read_text(encoding="utf-8").lower()
        for needle in forbidden:
            if needle in text:
                offenders.append(f"{source.relative_to(REPO_ROOT)}: {needle}")
    assert not offenders, offenders


def test_a_brand_file_missing_a_required_key_is_refused(tmp_path, monkeypatch):
    broken = tmp_path / "brand.json"
    broken.write_text(json.dumps({"name": "Something"}), encoding="utf-8")
    monkeypatch.setenv("BRAND_JSON_PATH", str(broken))
    load_brand.cache_clear()
    try:
        with pytest.raises(RuntimeError, match="is missing"):
            load_brand()
    finally:
        monkeypatch.delenv("BRAND_JSON_PATH", raising=False)
        load_brand.cache_clear()


def test_the_correction_url_is_derived_from_the_brand():
    url = BRAND.correction_url("journey", "kt_example")
    assert url.startswith(BRAND.url)
    assert "entity=journey:kt_example" in url


def test_the_staging_bootstrap_resolves_the_compiled_data_layer():
    assert (_staging.STAGING_PATH / "hodomap_ktel" / "__init__.py").is_file()
    assert _staging.STAGING_PATH.name == "ktel-staging"


def test_the_staging_branding_module_follows_brand_json():
    from hodomap_ktel import branding

    # The release directory name and the GTFS publisher come from here, so a
    # rename in brand.json must reach them.
    assert branding.PRODUCT_NAME == BRAND.name
    assert branding.PRODUCT_SLUG == BRAND.slug
    assert branding.PRODUCT_URL == BRAND.url
    assert branding.PUBLIC_CONTRACT_VERSION == BRAND.contract_version


# --------------------------------------------------------------------------- #
# Environment validation
# --------------------------------------------------------------------------- #

def test_an_empty_environment_lists_every_missing_variable():
    with pytest.raises(ConfigError) as error:
        load_settings({})
    message = str(error.value)
    for suffix in ("PUBLIC_DB_PATH", "RELEASE_DIR", "DATA_MODE"):
        assert f"{BRAND.env_prefix}_{suffix}" in message
    # Every problem is reported at once, not one per restart.
    assert message.count("\n  - ") == 3


def test_the_legacy_prefix_is_still_accepted(seeded):
    settings = load_settings(
        {
            f"{LEGACY_PREFIX}_PUBLIC_DB_PATH": str(seeded["publicDb"]),
            f"{LEGACY_PREFIX}_RELEASE_DIR": str(seeded["releaseDir"]),
            f"{LEGACY_PREFIX}_DATA_MODE": "demo",
        }
    )
    assert settings.public_db_path == seeded["publicDb"]
    assert settings.data_mode == "demo"


def test_the_branded_prefix_wins_over_the_legacy_one(seeded):
    settings = load_settings(
        {
            f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(seeded["publicDb"]),
            f"{LEGACY_PREFIX}_PUBLIC_DB_PATH": "/nowhere/wrong.db",
            f"{BRAND.env_prefix}_RELEASE_DIR": str(seeded["releaseDir"]),
            f"{BRAND.env_prefix}_DATA_MODE": "demo",
        }
    )
    assert settings.public_db_path == seeded["publicDb"]


def test_env_names_reports_both_spellings():
    branded, legacy = env_names("PUBLIC_DB_PATH")
    assert branded == f"{BRAND.env_prefix}_PUBLIC_DB_PATH"
    assert legacy == f"{LEGACY_PREFIX}_PUBLIC_DB_PATH"


def test_the_data_mode_has_no_default(seeded):
    environment = {
        f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(seeded["publicDb"]),
        f"{BRAND.env_prefix}_RELEASE_DIR": str(seeded["releaseDir"]),
    }
    with pytest.raises(ConfigError) as error:
        load_settings(environment)
    assert "DATA_MODE is required" in str(error.value)
    assert "no default" in str(error.value)


@pytest.mark.parametrize("mode", DATA_MODES)
def test_both_data_modes_are_accepted(seeded, mode):
    settings = load_settings(
        base_environ(seeded, **{f"{BRAND.env_prefix}_DATA_MODE": mode})
    )
    assert settings.data_mode == mode


def test_an_invalid_data_mode_is_refused(seeded):
    with pytest.raises(ConfigError, match="is invalid"):
        load_settings(
            base_environ(seeded, **{f"{BRAND.env_prefix}_DATA_MODE": "probably"})
        )


def test_the_release_directory_must_be_named_after_the_brand(seeded, tmp_path):
    wrong = tmp_path / "not-the-slug"
    wrong.mkdir()
    with pytest.raises(ConfigError, match=f"'{BRAND.slug}' directory"):
        load_settings(
            base_environ(seeded, **{f"{BRAND.env_prefix}_RELEASE_DIR": str(wrong)})
        )


@pytest.mark.parametrize("value", ["maybe", "2", ""])
def test_an_invalid_admin_flag_is_refused_or_ignored(seeded, value):
    environment = base_environ(
        seeded, **{f"{BRAND.env_prefix}_ADMIN_ENABLED": value}
    )
    if value == "":
        # An empty value is absent, so the default applies.
        assert load_settings(environment).admin_enabled is False
    else:
        with pytest.raises(ConfigError, match="must be a boolean"):
            load_settings(environment)


@pytest.mark.parametrize("value", ["true", "TRUE", "1", "yes", "on"])
def test_truthy_admin_flags_are_accepted(seeded, value, tmp_path):
    settings = load_settings(
        base_environ(
            seeded,
            **{
                f"{BRAND.env_prefix}_ADMIN_ENABLED": value,
                f"{BRAND.env_prefix}_INGEST_DB_PATH": str(seeded["ingestDb"]),
                f"{BRAND.env_prefix}_ADMIN_DB_PATH": str(tmp_path / "admin.db"),
                "ADMIN_API_TOKEN": ADMIN_TOKEN,
            },
        )
    )
    assert settings.admin_enabled is True
    assert settings.admin_token == ADMIN_TOKEN


def test_an_invalid_hsts_max_age_is_refused(seeded):
    with pytest.raises(ConfigError, match="non-negative integer"):
        load_settings(
            base_environ(seeded, **{f"{BRAND.env_prefix}_HSTS_MAX_AGE": "forever"})
        )
    with pytest.raises(ConfigError, match="non-negative integer"):
        load_settings(
            base_environ(seeded, **{f"{BRAND.env_prefix}_HSTS_MAX_AGE": "-1"})
        )


def test_an_invalid_log_level_is_refused(seeded):
    with pytest.raises(ConfigError, match="not a valid logging level"):
        load_settings(
            base_environ(seeded, **{f"{BRAND.env_prefix}_LOG_LEVEL": "chatty"})
        )


def test_the_admin_token_is_absent_when_the_admin_surface_is_off(seeded):
    settings = load_settings(base_environ(seeded, ADMIN_API_TOKEN=ADMIN_TOKEN))
    assert settings.admin_enabled is False
    # A token in the environment of a public deployment is not held in memory.
    assert settings.admin_token is None


def test_the_settings_repr_never_leaks_the_token(seeded, tmp_path):
    settings = load_settings(
        base_environ(
            seeded,
            **{
                f"{BRAND.env_prefix}_ADMIN_ENABLED": "true",
                f"{BRAND.env_prefix}_INGEST_DB_PATH": str(seeded["ingestDb"]),
                f"{BRAND.env_prefix}_ADMIN_DB_PATH": str(tmp_path / "admin.db"),
                "ADMIN_API_TOKEN": ADMIN_TOKEN,
            },
        )
    )
    assert ADMIN_TOKEN not in repr(settings)
    assert ADMIN_TOKEN not in str(settings)
    assert isinstance(settings, Settings)


def test_derived_paths_point_where_the_release_generator_writes(seeded):
    settings = load_settings(base_environ(seeded))
    assert settings.manifest_path.name == "manifest.json"
    assert settings.packs_dir.name == "packs"
    assert settings.release_dir.name == BRAND.slug
    # The generator writes <out>/<slug>/, so the parent is the out directory.
    assert settings.release_out_dir == seeded["releaseDir"].parent


def test_the_page_limits_match_the_contract():
    assert MAX_PAGE_LIMIT == 200
    assert 1 <= DEFAULT_PAGE_LIMIT <= MAX_PAGE_LIMIT
