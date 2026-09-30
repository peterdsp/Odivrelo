"""Brand-neutral public API service for the published transport data contract.

The package name deliberately carries no product name: identity is read from
``brand.json`` at import time, so a rename never touches Python imports.
"""
from __future__ import annotations

from .brand import BRAND

__all__ = ["BRAND", "create_app"]

CONTRACT_VERSION = BRAND.contract_version


def create_app(*args, **kwargs):
    """Lazy re-export so importing the package does not require a valid env."""
    from .app import create_app as factory

    return factory(*args, **kwargs)
