"""Single source of product identity for the staging pipeline.

Keeping the brand in one module means a rename touches one file instead of
every generated artifact, feed header and user-visible label.
"""
from __future__ import annotations

import os

PRODUCT_NAME = os.environ.get("PRODUCT_NAME", "Poravia")
PRODUCT_SLUG = os.environ.get("PRODUCT_SLUG", "poravia")
PRODUCT_URL = os.environ.get("PRODUCT_URL", "https://poravia.peterdsp.dev")
PRODUCT_SUPPORT_EMAIL = os.environ.get(
    "PRODUCT_SUPPORT_EMAIL", "info@peterdsp.dev"
)

#: Contract version of the published public data shapes. Bump on a breaking
#: change to the pack payloads or the manifest layout.
PUBLIC_CONTRACT_VERSION = "1.0.0"
