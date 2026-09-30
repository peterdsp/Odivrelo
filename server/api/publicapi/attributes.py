"""Strict reader for the ``public_attributes`` JSON column.

The staging schema stores the v1 contract's presentation-only facts (a third
display language, the boarding bay, reviewed step-free boarding, boarding
instructions, the purchase action and journey restrictions) in one nullable JSON
object per row. The write side is a reviewed import; this is the read side, and
it is deliberately strict: an unexpected shape yields the safe default rather
than leaking a raw blob into a public response.
"""
from __future__ import annotations

import json
from typing import Any, Mapping

from .brand import BRAND

PURCHASE_KINDS = ("online", "ticket_office", "phone", "onboard", "unavailable")
TIME_QUALITIES = ("scheduled", "approximate", "unknown")
POSITION_QUALITIES = ("scheduled", "predicted", "estimated", "live")
BOARDING_RULES = ("allowed", "not_allowed", "request", "unknown")


def parse(raw: str | bytes | None) -> dict[str, Any]:
    """Return the stored object, or an empty mapping for anything unusable."""
    if not raw:
        return {}
    try:
        payload = json.loads(raw)
    except (TypeError, ValueError):
        return {}
    return payload if isinstance(payload, dict) else {}


def localized(
    attributes: Mapping[str, Any],
    key: str,
    *,
    fallback: str | None = None,
) -> dict[str, str] | None:
    """Read a ``{el, en, sq}`` object, filling gaps from the supplied fallback."""
    value = attributes.get(key)
    if not isinstance(value, Mapping):
        if fallback is None:
            return None
        value = {}
    result: dict[str, str] = {}
    for language in BRAND.languages:
        candidate = value.get(language)
        if isinstance(candidate, str) and candidate.strip():
            result[language] = candidate
        elif fallback is not None:
            result[language] = fallback
    return result or None


def names(
    attributes: Mapping[str, Any],
    *,
    name_en: str | None,
    name_el: str | None,
) -> dict[str, str]:
    """Build the contract's trilingual name object for a row.

    ``el`` and ``en`` come from their own columns. ``sq`` comes from the
    attributes object when a reviewer supplied one and otherwise falls back to
    ``en``, which is the documented behaviour: the contract never omits a
    language key, and an untranslated label is more useful than a missing one.
    """
    english = (name_en or name_el or "").strip()
    greek = (name_el or english).strip()
    albanian = attributes.get("nameSq")
    if not isinstance(albanian, str) or not albanian.strip():
        albanian = english
    return {"el": greek, "en": english, "sq": albanian.strip()}


def _text(value: Any) -> str | None:
    return value.strip() if isinstance(value, str) and value.strip() else None


def _enum(value: Any, allowed: tuple[str, ...], default: str) -> str:
    return value if isinstance(value, str) and value in allowed else default


def boarding_point(
    attributes: Mapping[str, Any],
) -> dict[str, Any]:
    """Bay, step-free review state and boarding instructions for a stop."""
    step_free = attributes.get("stepFree")
    return {
        "bay": _text(attributes.get("bay")),
        "stepFree": step_free if isinstance(step_free, bool) else None,
        "reviewedAt": _text(attributes.get("boardingReviewedAt")),
        "instructions": localized(attributes, "instructions"),
    }


def municipality(attributes: Mapping[str, Any]) -> str | None:
    return _text(attributes.get("municipality"))


def purchase(
    attributes: Mapping[str, Any],
    *,
    booking_url: str | None,
) -> dict[str, Any]:
    """Resolve the purchase action for a journey.

    A stored object wins. With nothing stored, a booking URL implies an online
    sale and its absence means the journey is not sellable through this service.
    """
    stored = attributes.get("purchase")
    if not isinstance(stored, Mapping):
        stored = {}
    default_kind = "online" if booking_url else "unavailable"
    kind = _enum(stored.get("kind"), PURCHASE_KINDS, default_kind)
    url = _text(stored.get("url")) or (booking_url if kind == "online" else None)
    return {
        "kind": kind,
        "url": url,
        "phone": _text(stored.get("phone")),
        "address": _text(stored.get("address")),
        "openingHours": _text(stored.get("openingHours")),
        "label": localized(stored, "label"),
        "disclaimer": localized(stored, "disclaimer"),
    }


def restrictions(attributes: Mapping[str, Any]) -> list[dict[str, Any]]:
    stored = attributes.get("restrictions")
    if not isinstance(stored, list):
        return []
    result: list[dict[str, Any]] = []
    for item in stored:
        if not isinstance(item, Mapping):
            continue
        code = _text(item.get("code"))
        if not code:
            continue
        result.append({"code": code, "text": localized(item, "text", fallback=code)})
    return result


def contact(attributes: Mapping[str, Any]) -> dict[str, Any] | None:
    stored = attributes.get("contact")
    if not isinstance(stored, Mapping):
        return None
    block = {
        "phone": _text(stored.get("phone")),
        "email": _text(stored.get("email")),
        "address": _text(stored.get("address")),
    }
    return block if any(block.values()) else None


def position_quality(attributes: Mapping[str, Any]) -> str:
    return _enum(attributes.get("positionQuality"), POSITION_QUALITIES, "scheduled")


def fare_is_indicative(attributes: Mapping[str, Any]) -> bool:
    value = attributes.get("fareIsIndicative")
    return True if not isinstance(value, bool) else value
