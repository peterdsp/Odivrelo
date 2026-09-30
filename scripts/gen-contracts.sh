#!/usr/bin/env bash
# Validate the versioned public contracts and regenerate everything derived
# from them.
#
#   scripts/gen-contracts.sh            # validate, then write generated output
#   scripts/gen-contracts.sh --check    # validate, then fail on any drift (CI)
#
# Derived output, and therefore never edited by hand:
#   data/schemas/*.schema.json     projected from the OpenAPI components
#   data/schemas/generated/types.ts  emitted by openapi-typescript
#
# The authored sources are data/schemas/CONTRACT-v1.md (prose, authoritative)
# and data/schemas/public-api-v1.yaml (its machine-readable form).
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
SCHEMA_DIR="$ROOT/data/schemas"
SPEC="$SCHEMA_DIR/public-api-v1.yaml"
TYPES="$SCHEMA_DIR/generated/types.ts"

PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
[ -x "$PY" ] || PY="$(command -v python3)"

CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1

[ -f "$SPEC" ] || { echo "FAIL: no OpenAPI document at $SPEC" >&2; exit 1; }
mkdir -p "$SCHEMA_DIR/generated"

TOOL="$(mktemp -t derive-schemas.XXXXXX.py)"
trap 'rm -f "$TOOL"' EXIT

cat > "$TOOL" <<'PYCODE'
"""Validate the contracts and project the standalone JSON Schema documents.

An OpenAPI 3.1 schema object is a JSON Schema 2020-12 schema, so the contract is
expressed exactly once, in public-api-v1.yaml, and every standalone document is
projected from it. Hand-maintaining both copies is the one arrangement that
guarantees they eventually disagree, so it is not available here.
"""
from __future__ import annotations

import json
import os
import pathlib
import re
import sys

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate as validate_openapi

SCHEMA_DIR = pathlib.Path(os.environ["SCHEMA_DIR"])
BASE_URI = os.environ["SCHEMA_BASE_URI"]

#: file stem -> (root component, title, description)
DOCUMENTS: dict[str, tuple[str, str, str]] = {
    "offline-manifest-v1": (
        "OfflineManifest",
        "Offline release manifest, version 1",
        "The generated release manifest. It is written last, after every pack, "
        "so a reader never sees a manifest that points at a half-written file.",
    ),
    "journey-v1": (
        "Journey",
        "Journey, version 1",
        "One journey on one service date, with its stops, geometry, provenance "
        "and purchase action. A journey that departs before midnight and "
        "arrives after it keeps the earlier service date.",
    ),
    "place-v1": (
        "Place",
        "Place, version 1",
        "A stop place (terminal) or one of its boarding points.",
    ),
    "operator-v1": (
        "Operator",
        "Operator, version 1",
        "One transport operator, its coverage and the sources it is built from.",
    ),
    "coverage-v1": (
        "Coverage",
        "Coverage, version 1",
        "Coverage and freshness summary, including the explicit statement of "
        "what is not covered.",
    ),
    "error-v1": (
        "Error",
        "Error, version 1",
        "The single error shape returned by every non-2xx response.",
    ),
    # One document per offline pack body. A pack carries exactly what the
    # matching endpoint returns, so each of these is the response schema of that
    # endpoint rather than a shape invented for offline use.
    "pack-meta-v1": (
        "MetaResponse",
        "Offline pack: meta, version 1",
        "The `meta` pack, identical to the GET /v1/meta body.",
    ),
    "pack-coverage-v1": (
        "CoverageResponse",
        "Offline pack: coverage, version 1",
        "The `coverage` pack, identical to the GET /v1/coverage body.",
    ),
    "pack-sources-v1": (
        "SourcesResponse",
        "Offline pack: sources, version 1",
        "The `sources` pack, identical to the GET /v1/sources body.",
    ),
    "pack-places-v1": (
        "PlacesResponse",
        "Offline pack: places, version 1",
        "The `places` pack, identical to an unfiltered, unpaged GET /v1/places body.",
    ),
    "pack-operators-v1": (
        "OperatorsPack",
        "Offline pack: operators, version 1",
        "The `operators` pack: every operator in the GET /v1/operators/{id} "
        "shape, keyed by id.",
    ),
    "pack-stops-v1": (
        "StopsPack",
        "Offline pack: stops, version 1",
        "The `stops` pack: every stop in the GET /v1/stops/{id} shape, keyed by id.",
    ),
    "pack-journeys-v1": (
        "JourneysPack",
        "Offline pack: journeys for one service date, version 1",
        "One `journeys-<serviceDate>` pack: the GET /v1/journeys result list for "
        "the date, plus the GET /v1/journeys/{id} detail for each result.",
    ),
}

REF_PATTERN = re.compile(r"#/components/schemas/(\w+)")


def referenced(node: object, found: set[str]) -> set[str]:
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str):
            match = REF_PATTERN.fullmatch(ref)
            if match:
                found.add(match.group(1))
        for value in node.values():
            referenced(value, found)
    elif isinstance(node, list):
        for value in node:
            referenced(value, found)
    return found


def rewrite(node: object) -> object:
    if isinstance(node, dict):
        return {
            key: (
                value.replace("#/components/schemas/", "#/$defs/")
                if key == "$ref" and isinstance(value, str)
                else rewrite(value)
            )
            for key, value in node.items()
        }
    if isinstance(node, list):
        return [rewrite(value) for value in node]
    return node


def closure(components: dict[str, object], root: str) -> set[str]:
    needed: set[str] = set()
    frontier = {root}
    while frontier:
        name = frontier.pop()
        if name in needed:
            continue
        needed.add(name)
        frontier |= referenced(components[name], set()) - needed
    return needed


def build(components: dict[str, object], stem: str) -> dict[str, object]:
    root, title, description = DOCUMENTS[stem]
    defs = {
        name: rewrite(components[name])
        for name in sorted(closure(components, root))
        if name != root
    }
    document: dict[str, object] = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": f"{BASE_URI}/{stem}.schema.json",
        "title": title,
        "description": description,
        **rewrite(components[root]),  # type: ignore[dict-item]
    }
    if defs:
        document["$defs"] = defs
    return document


def main() -> int:
    check = "--check" in sys.argv
    spec = yaml.safe_load((SCHEMA_DIR / "public-api-v1.yaml").read_text(encoding="utf-8"))
    validate_openapi(spec)
    print(
        "OpenAPI 3.1 document is valid: "
        f"{len(spec['paths'])} paths, {len(spec['components']['schemas'])} schemas"
    )

    components = spec["components"]["schemas"]
    drift: list[str] = []
    for stem in DOCUMENTS:
        document = build(components, stem)
        Draft202012Validator.check_schema(document)
        body = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
        target = SCHEMA_DIR / f"{stem}.schema.json"
        if check:
            current = target.read_text(encoding="utf-8") if target.is_file() else ""
            if current != body:
                drift.append(target.name)
            else:
                print(f"unchanged {target.name}")
        else:
            target.write_text(body, encoding="utf-8")
            print(f"wrote {target.name}")

    if drift:
        print(
            "FAIL: committed JSON Schema output differs from a fresh generation: "
            + ", ".join(drift)
            + "\n      run scripts/gen-contracts.sh and commit the result",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
PYCODE

export SCHEMA_DIR
export SCHEMA_BASE_URI="https://$("$PY" -c 'import json,sys;print(json.load(open("brand.json"))["domain"])')/schemas/v1"

if [ "$CHECK" -eq 1 ]; then
  "$PY" "$TOOL" --check
else
  "$PY" "$TOOL"
fi

# TypeScript types. openapi-typescript is deterministic for a given input and
# version, which is what makes the drift comparison below meaningful.
FRESH="$(mktemp -t types.XXXXXX.ts)"
trap 'rm -f "$TOOL" "$FRESH"' EXIT

HEADER="// Generated by scripts/gen-contracts.sh from data/schemas/public-api-v1.yaml.
// Do not edit. Run scripts/gen-contracts.sh after changing the OpenAPI document.
"

{
  printf '%s' "$HEADER"
  npx --yes openapi-typescript@7 "$SPEC" --root-types --alphabetize
} > "$FRESH"

if [ "$CHECK" -eq 1 ]; then
  if [ ! -f "$TYPES" ]; then
    echo "FAIL: $TYPES is missing; run scripts/gen-contracts.sh" >&2
    exit 1
  fi
  if ! diff -u "$TYPES" "$FRESH" > /dev/null; then
    echo "FAIL: committed TypeScript types differ from a fresh generation." >&2
    echo "      run scripts/gen-contracts.sh and commit the result." >&2
    diff -u "$TYPES" "$FRESH" | head -60 >&2
    exit 1
  fi
  echo "unchanged generated/types.ts"
  echo "Contracts are valid and generated output matches the committed files."
else
  mv "$FRESH" "$TYPES"
  echo "wrote generated/types.ts"
  echo "Contracts are valid and generated output is up to date."
fi
