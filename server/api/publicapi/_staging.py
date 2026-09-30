"""Make the compiled-data layer importable without vendoring or copying it.

``server/ktel-staging`` carries no packaging metadata, so there is nothing to
``pip install -e``. Creating one would mean editing a directory this service
does not own. The supported alternative from the delivery brief is taken here:
a small, explicit bootstrap that puts the staging directory on ``sys.path``
exactly once, before any ``odivrelo_ktel`` import.

Resolution order, first hit wins:

1. ``<ENV_PREFIX>_KTEL_STAGING_PATH`` (or the legacy ``PORAVIA_`` and
   ``HODOMAP_`` spellings),
   for deployments that install the staging tree somewhere else.
2. ``server/ktel-staging`` relative to this repository checkout.

The bootstrap raises rather than silently degrading, because every public
response ultimately depends on the release and GTFS code living there.
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

from .brand import BRAND

#: ``server/api/publicapi/_staging.py`` -> ``server/api/publicapi`` -> ... -> repo root.
_REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_STAGING_PATH = _REPO_ROOT / "server" / "ktel-staging"


def staging_path() -> Path:
    override = os.environ.get(
        f"{BRAND.env_prefix}_KTEL_STAGING_PATH"
    ) or os.environ.get("PORAVIA_KTEL_STAGING_PATH") or os.environ.get("HODOMAP_KTEL_STAGING_PATH")
    return Path(override).expanduser().resolve() if override else DEFAULT_STAGING_PATH


def _publish_identity() -> None:
    """Feed ``brand.json`` to the staging branding module before it is imported.

    ``odivrelo_ktel.branding`` reads ``PRODUCT_*`` from the environment once, at
    import time, and otherwise falls back to values compiled into that file. A
    rename in ``brand.json`` must reach the release directory name, the GTFS
    feed publisher and the attribution rows, so the mapping is applied here,
    before the first ``odivrelo_ktel`` import can happen. An operator who sets
    these explicitly still wins.
    """
    for name, value in (
        ("PRODUCT_NAME", BRAND.name),
        ("PRODUCT_SLUG", BRAND.slug),
        ("PRODUCT_URL", BRAND.url),
        ("PRODUCT_SUPPORT_EMAIL", BRAND.support_email),
    ):
        os.environ.setdefault(name, value)


def install() -> Path:
    """Put the staging directory on ``sys.path`` and return it."""
    _publish_identity()
    path = staging_path()
    if not (path / "odivrelo_ktel" / "__init__.py").is_file():
        raise RuntimeError(
            "the compiled-data layer was not found at "
            f"{path}. Set {BRAND.env_prefix}_KTEL_STAGING_PATH to the directory "
            "that contains the odivrelo_ktel package."
        )
    text = str(path)
    if text not in sys.path:
        sys.path.insert(0, text)
    return path


STAGING_PATH = install()
