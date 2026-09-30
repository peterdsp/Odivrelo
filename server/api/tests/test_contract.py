"""Contract drift gate.

Two documents describe the same surface: the committed OpenAPI document that
clients generate types from, and the document FastAPI derives from the running
routes. If they drift, a client is generated against a promise the service no
longer keeps, so the drift is a test failure rather than a review comment.

Live responses are additionally validated against the committed JSON Schema
documents, so the schemas describe what is actually served.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest
import yaml
from conftest import DAYTIME_DATE, ORAVO_EXTERNAL_ID, ORIGIN_TERMINAL_EXTERNAL_ID
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate as validate_openapi

from publicapi.brand import BRAND

REPO_ROOT = Path(__file__).resolve().parents[3]
SCHEMA_DIR = REPO_ROOT / "data" / "schemas"
SPEC_PATH = SCHEMA_DIR / "public-api-v1.yaml"
GENERATED_TYPES = SCHEMA_DIR / "generated" / "types.ts"


@pytest.fixture(scope="module")
def committed() -> dict:
    return yaml.safe_load(SPEC_PATH.read_text(encoding="utf-8"))


@pytest.fixture
def served(client) -> dict:
    return client.get("/openapi.json").json()


def test_the_committed_document_is_a_valid_openapi_31_document(committed):
    validate_openapi(committed)
    assert committed["openapi"].startswith("3.1")
    assert committed["info"]["version"] == BRAND.contract_version


def test_the_served_document_is_a_valid_openapi_31_document(served):
    validate_openapi(served)
    assert served["openapi"].startswith("3.1")
    assert served["info"]["version"] == BRAND.contract_version


def test_the_contract_version_matches_the_brand_and_the_contract_document():
    prose = (SCHEMA_DIR / "CONTRACT-v1.md").read_text(encoding="utf-8")
    assert f"`{BRAND.contract_version}`" in prose
    assert BRAND.contract_version == "1.0.0"


def test_every_documented_path_and_method_is_served(committed, served):
    documented = {
        (path, method)
        for path, operations in committed["paths"].items()
        for method in operations
        if method in {"get", "post", "put", "patch", "delete"}
    }
    available = {
        (path, method)
        for path, operations in served["paths"].items()
        for method in operations
        if method in {"get", "post", "put", "patch", "delete"}
    }
    missing = documented - available
    assert not missing, f"documented but not served: {sorted(missing)}"


def test_every_served_public_path_is_documented(committed, served):
    documented = {
        (path, method)
        for path, operations in committed["paths"].items()
        for method in operations
        if method in {"get", "post", "put", "patch", "delete"}
    }
    available = {
        (path, method)
        for path, operations in served["paths"].items()
        for method in operations
        if method in {"get", "post", "put", "patch", "delete"}
    }
    undocumented = {
        entry for entry in available - documented if not entry[0].startswith("/admin")
    }
    assert not undocumented, f"served but not documented: {sorted(undocumented)}"


def _parameters(document: dict, path: str, method: str) -> dict[str, dict]:
    """Resolve an operation's parameters, following component references."""
    operation = document["paths"][path][method]
    resolved: dict[str, dict] = {}
    for parameter in operation.get("parameters", []):
        reference = parameter.get("$ref")
        if reference:
            name = reference.rsplit("/", 1)[-1]
            parameter = document["components"]["parameters"][name]
        resolved[parameter["name"]] = parameter
    return resolved


def test_every_documented_parameter_is_accepted_with_the_same_shape(committed, served):
    problems: list[str] = []
    for path, operations in committed["paths"].items():
        for method in operations:
            if method not in {"get", "post"}:
                continue
            documented = _parameters(committed, path, method)
            available = _parameters(served, path, method)
            for name, parameter in documented.items():
                if name not in available:
                    problems.append(f"{method.upper()} {path}: {name} is not accepted")
                    continue
                if parameter["in"] != available[name]["in"]:
                    problems.append(
                        f"{method.upper()} {path}: {name} is in "
                        f"{available[name]['in']}, documented as {parameter['in']}"
                    )
                if bool(parameter.get("required")) != bool(
                    available[name].get("required")
                ):
                    problems.append(
                        f"{method.upper()} {path}: {name} required flag differs"
                    )
    assert not problems, "\n".join(problems)


def test_no_served_parameter_is_undocumented(committed, served):
    problems: list[str] = []
    for path, operations in served["paths"].items():
        if path.startswith("/admin"):
            continue
        for method in operations:
            if method not in {"get", "post"}:
                continue
            if path not in committed["paths"] or method not in committed["paths"][path]:
                continue
            documented = _parameters(committed, path, method)
            for name in _parameters(served, path, method):
                if name not in documented:
                    problems.append(f"{method.upper()} {path}: {name} is undocumented")
    assert not problems, "\n".join(problems)


def test_every_documented_status_code_is_reachable(committed, served):
    problems: list[str] = []
    for path, operations in committed["paths"].items():
        for method, operation in operations.items():
            if method not in {"get", "post"}:
                continue
            available = set(served["paths"][path][method]["responses"])
            for status in operation["responses"]:
                if status not in available:
                    problems.append(f"{method.upper()} {path}: {status} is not declared")
    assert not problems, "\n".join(problems)


def test_every_contract_enumeration_is_present_and_exact(committed):
    schemas = committed["components"]["schemas"]
    assert schemas["RightsStatus"]["enum"] == [
        "allowed",
        "permission_pending",
        "prohibited",
        "unknown",
    ]
    assert schemas["ReviewState"]["enum"] == [
        "candidate",
        "verified",
        "published",
        "stale",
        "withdrawn",
        "quarantined",
    ]
    assert schemas["TimeQuality"]["enum"] == ["scheduled", "approximate", "unknown"]
    assert schemas["PositionQuality"]["enum"] == [
        "scheduled",
        "predicted",
        "estimated",
        "live",
    ]
    assert schemas["GeometryConfidence"]["enum"] == [
        "unverified",
        "ordered_stops_only",
        "osm_candidate",
        "reviewed",
        "rejected",
    ]
    assert schemas["PurchaseKind"]["enum"] == [
        "online",
        "ticket_office",
        "phone",
        "onboard",
        "unavailable",
    ]
    assert schemas["CoverageState"]["enum"] == [
        "covered",
        "partial",
        "not_covered",
        "demo",
    ]
    assert schemas["DataMode"]["enum"] == ["real", "demo"]
    assert schemas["Language"]["enum"] == list(BRAND.languages)
    assert schemas["Error"]["properties"]["error"]["properties"]["code"]["enum"] == [
        "not_found",
        "invalid_request",
        "unavailable",
        "release_mismatch",
        "unauthorized",
    ]


def test_the_generated_types_are_committed_and_derived_from_the_document():
    body = GENERATED_TYPES.read_text(encoding="utf-8")
    assert body.startswith("// Generated by scripts/gen-contracts.sh")
    assert "export interface paths" in body
    assert "export interface components" in body
    for path in (
        "/v1/meta",
        "/v1/places",
        "/v1/journeys",
        "/v1/journeys/{journey_id}",
        "/v1/operators",
        "/v1/operators/{operator_id}",
        "/v1/stops/{stop_id}",
        "/v1/coverage",
        "/v1/sources",
        "/v1/offline/manifest",
        "/v1/offline/packs/{filename}",
        "/v1/gtfs",
        "/healthz",
        "/readyz",
    ):
        assert f'"{path}"' in body, path


# --------------------------------------------------------------------------- #
# Live responses against the committed JSON Schema documents
# --------------------------------------------------------------------------- #

def _validator(stem: str) -> Draft202012Validator:
    document = json.loads(
        (SCHEMA_DIR / f"{stem}.schema.json").read_text(encoding="utf-8")
    )
    Draft202012Validator.check_schema(document)
    return Draft202012Validator(document)


@pytest.mark.parametrize(
    "stem",
    [
        "offline-manifest-v1",
        "journey-v1",
        "place-v1",
        "operator-v1",
        "coverage-v1",
        "error-v1",
    ],
)
def test_every_json_schema_document_is_draft_2020_12(stem):
    document = json.loads(
        (SCHEMA_DIR / f"{stem}.schema.json").read_text(encoding="utf-8")
    )
    assert document["$schema"] == "https://json-schema.org/draft/2020-12/schema"
    assert document["$id"].endswith(f"{stem}.schema.json")
    Draft202012Validator.check_schema(document)


def test_the_served_manifest_validates_against_its_schema(client):
    _validator("offline-manifest-v1").validate(
        client.get("/v1/offline/manifest").json()
    )


def test_every_served_place_validates_against_its_schema(client):
    validator = _validator("place-v1")
    for place in client.get("/v1/places", params={"limit": 200}).json()["places"]:
        validator.validate(place)


def test_every_served_journey_validates_against_its_schema(client, ids):
    validator = _validator("journey-v1")
    results = client.get(
        "/v1/journeys",
        params={
            "origin": ids[ORIGIN_TERMINAL_EXTERNAL_ID],
            "destination": ids[ORAVO_EXTERNAL_ID],
            "date": DAYTIME_DATE,
        },
    ).json()["results"]
    assert results
    for result in results:
        detail = client.get(
            f"/v1/journeys/{result['id']}", params={"date": DAYTIME_DATE}
        ).json()["journey"]
        validator.validate(detail)


def test_every_served_operator_validates_against_its_schema(client):
    validator = _validator("operator-v1")
    operators = client.get("/v1/operators").json()["operators"]
    assert operators
    for operator in operators:
        validator.validate(operator)
        validator.validate(
            client.get(f"/v1/operators/{operator['id']}").json()["operator"]
        )


def test_the_served_coverage_validates_against_its_schema(client):
    _validator("coverage-v1").validate(client.get("/v1/coverage").json()["coverage"])


@pytest.mark.parametrize(
    "path",
    [
        "/v1/journeys/kt_nope",
        "/v1/places?limit=999",
        "/v1/nope",
        "/v1/meta?release=0000000000000000",
    ],
)
def test_every_error_body_validates_against_its_schema(client, path):
    response = client.get(path)
    assert response.status_code >= 400
    _validator("error-v1").validate(response.json())
