"""Error handling, pagination bounds, response hardening and bounded logging."""
from __future__ import annotations

import json

import pytest
from conftest import DAYTIME_DATE, ORAVO_EXTERNAL_ID, ORIGIN_TERMINAL_EXTERNAL_ID, base_environ
from fastapi.testclient import TestClient

from publicapi.app import create_app
from publicapi.brand import BRAND
from publicapi.config import MAX_PAGE_LIMIT
from publicapi.logging_setup import safe_query

LIST_PATHS = (
    "/v1/places",
    "/v1/operators",
    "/v1/sources",
)


# --------------------------------------------------------------------------- #
# The error shape
# --------------------------------------------------------------------------- #

def assert_error(response, status: int, code: str, field: str | None = None):
    assert response.status_code == status
    body = response.json()
    assert set(body) == {"error"}
    assert set(body["error"]) == {"code", "message", "field"}
    assert body["error"]["code"] == code
    assert body["error"]["message"]
    if field is not None:
        assert body["error"]["field"] == field
    assert response.headers["Cache-Control"] == "no-store"


@pytest.mark.parametrize("path", LIST_PATHS)
def test_limit_above_the_maximum_is_refused(client, path):
    assert_error(
        client.get(path, params={"limit": MAX_PAGE_LIMIT + 1}),
        400,
        "invalid_request",
        "limit",
    )


@pytest.mark.parametrize("path", LIST_PATHS)
def test_the_maximum_limit_itself_is_accepted(client, path):
    assert client.get(path, params={"limit": MAX_PAGE_LIMIT}).status_code == 200


@pytest.mark.parametrize("path", LIST_PATHS)
@pytest.mark.parametrize("limit", [0, -1])
def test_a_non_positive_limit_is_refused(client, path, limit):
    assert_error(client.get(path, params={"limit": limit}), 400, "invalid_request", "limit")


@pytest.mark.parametrize("path", LIST_PATHS)
def test_a_negative_offset_is_refused(client, path):
    assert_error(
        client.get(path, params={"offset": -1}), 400, "invalid_request", "offset"
    )


def test_journeys_and_stops_are_paginated_too(client, ids):
    assert_error(
        client.get(
            "/v1/journeys",
            params={
                "origin": ids[ORIGIN_TERMINAL_EXTERNAL_ID],
                "destination": ids[ORAVO_EXTERNAL_ID],
                "date": DAYTIME_DATE,
                "limit": 201,
            },
        ),
        400,
        "invalid_request",
        "limit",
    )
    assert_error(
        client.get(
            f"/v1/stops/{ids[ORAVO_EXTERNAL_ID]}", params={"limit": 201}
        ),
        400,
        "invalid_request",
        "limit",
    )


def test_pagination_actually_pages(client):
    everything = client.get("/v1/places", params={"limit": 200}).json()
    first = client.get("/v1/places", params={"limit": 2, "offset": 0}).json()
    second = client.get("/v1/places", params={"limit": 2, "offset": 2}).json()
    assert first["total"] == second["total"] == everything["total"]
    assert len(first["places"]) == 2
    assert first["places"] != second["places"]
    assert first["places"] + second["places"] == everything["places"][:4]


@pytest.mark.parametrize(
    "date", ["nope", "2026-13-01", "2026-2-3", "20261002", "2026-10-02T00:00:00"]
)
def test_an_invalid_service_date_is_refused(client, ids, date):
    assert_error(
        client.get(
            "/v1/journeys",
            params={
                "origin": ids[ORIGIN_TERMINAL_EXTERNAL_ID],
                "destination": ids[ORAVO_EXTERNAL_ID],
                "date": date,
            },
        ),
        400,
        "invalid_request",
        "date",
    )
    assert_error(
        client.get(f"/v1/stops/{ids[ORAVO_EXTERNAL_ID]}", params={"date": date}),
        400,
        "invalid_request",
        "date",
    )


def test_an_unsupported_language_is_refused(client):
    assert_error(
        client.get("/v1/places", params={"lang": "fr"}), 400, "invalid_request", "lang"
    )


@pytest.mark.parametrize("language", ["el", "en", "sq"])
def test_every_brand_language_is_accepted(client, language):
    assert client.get("/v1/places", params={"lang": language}).status_code == 200
    assert language in BRAND.languages


@pytest.mark.parametrize(
    "path",
    [
        "/v1/journeys/kt_nope",
        "/v1/operators/nope",
        "/v1/stops/ks_nope",
        "/v1/offline/packs/stops-0000000000000000.json",
    ],
)
def test_an_unknown_id_is_a_404(client, path):
    assert_error(client.get(path), 404, "not_found")


def test_a_traversal_attempt_on_a_pack_name_is_a_404(client):
    for candidate in ("../manifest.json", "..%2Fmanifest.json", "manifest.json"):
        response = client.get(f"/v1/offline/packs/{candidate}")
        assert response.status_code == 404, candidate


def test_origin_equal_to_destination_is_reported_not_searched(client, ids):
    body = client.get(
        "/v1/journeys",
        params={
            "origin": ids[ORAVO_EXTERNAL_ID],
            "destination": ids[ORAVO_EXTERNAL_ID],
            "date": DAYTIME_DATE,
        },
    ).json()
    assert body["results"] == []
    assert body["unavailableReason"] == "origin_equals_destination"


def test_an_unknown_origin_is_a_404(client, ids):
    assert_error(
        client.get(
            "/v1/journeys",
            params={
                "origin": "ks_nope",
                "destination": ids[ORAVO_EXTERNAL_ID],
                "date": DAYTIME_DATE,
            },
        ),
        404,
        "not_found",
    )


def test_a_missing_required_parameter_is_refused(client, ids):
    assert_error(
        client.get("/v1/journeys", params={"origin": ids[ORAVO_EXTERNAL_ID]}),
        400,
        "invalid_request",
        "destination",
    )


def test_a_release_mismatch_is_a_409_by_query_or_header(client, seeded):
    by_query = client.get("/v1/meta", params={"release": "0000000000000000"})
    assert_error(by_query, 409, "release_mismatch", "release")
    by_header = client.get("/v1/meta", headers={"X-Release-Id": "0000000000000000"})
    assert_error(by_header, 409, "release_mismatch")
    # The release the service is actually serving is accepted.
    assert (
        client.get("/v1/meta", params={"release": seeded["releaseId"]}).status_code
        == 200
    )


def test_an_unknown_path_returns_the_contract_error_shape(client):
    assert_error(client.get("/v1/nope"), 404, "not_found")


def test_a_wrong_method_returns_the_contract_error_shape(client):
    response = client.post("/v1/meta")
    assert response.status_code == 405
    assert response.json()["error"]["code"] == "invalid_request"


# --------------------------------------------------------------------------- #
# Response hardening
# --------------------------------------------------------------------------- #

SECURITY_HEADERS = {
    "Content-Security-Policy": "default-src 'none'",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "X-Frame-Options": "DENY",
    "Cross-Origin-Resource-Policy": "same-origin",
}


@pytest.mark.parametrize("path", ["/healthz", "/readyz", "/v1/meta", "/v1/gtfs", "/v1/nope"])
def test_security_headers_are_on_every_response(client, path):
    response = client.get(path)
    for header, expected in SECURITY_HEADERS.items():
        assert expected in response.headers[header], (path, header)
    hsts = response.headers["Strict-Transport-Security"]
    assert hsts.startswith("max-age=")
    assert "includeSubDomains" in hsts


def test_the_csp_permits_nothing_a_json_api_does_not_need(client):
    policy = client.get("/v1/meta").headers["Content-Security-Policy"]
    assert "default-src 'none'" in policy
    assert "frame-ancestors 'none'" in policy
    assert "base-uri 'none'" in policy
    assert "form-action 'none'" in policy
    assert "unsafe-inline" not in policy
    assert "*" not in policy


def test_hsts_can_be_switched_off_for_a_plain_http_mount(seeded):
    app = create_app(
        environ=base_environ(seeded, **{f"{BRAND.env_prefix}_HSTS_MAX_AGE": "0"})
    )
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/healthz")
    assert "Strict-Transport-Security" not in response.headers


def test_cors_is_off_unless_an_explicit_allowlist_is_configured(client):
    response = client.get("/v1/meta", headers={"Origin": "https://example.invalid"})
    assert "access-control-allow-origin" not in {
        key.lower() for key in response.headers
    }


def test_cors_allows_only_the_configured_origins(seeded):
    allowed = "https://web.example.invalid"
    app = create_app(
        environ=base_environ(
            seeded, **{f"{BRAND.env_prefix}_CORS_ALLOWED_ORIGINS": allowed}
        )
    )
    with TestClient(app, raise_server_exceptions=False) as client:
        permitted = client.get("/v1/meta", headers={"Origin": allowed})
        refused = client.get("/v1/meta", headers={"Origin": "https://evil.invalid"})
    assert permitted.headers["access-control-allow-origin"] == allowed
    assert "access-control-allow-origin" not in {
        key.lower() for key in refused.headers
    }


def test_a_wildcard_cors_allowlist_is_refused_at_startup(seeded):
    from publicapi.config import ConfigError, load_settings

    with pytest.raises(ConfigError, match="explicit allowlist"):
        load_settings(
            base_environ(seeded, **{f"{BRAND.env_prefix}_CORS_ALLOWED_ORIGINS": "*"})
        )


def test_a_request_id_is_returned_and_echoed(client):
    generated = client.get("/healthz")
    assert generated.headers["X-Request-Id"]
    supplied = client.get("/healthz", headers={"X-Request-Id": "abc123"})
    assert supplied.headers["X-Request-Id"] == "abc123"


# --------------------------------------------------------------------------- #
# Logging
# --------------------------------------------------------------------------- #

def test_every_log_line_is_one_bounded_json_object(client, log_stream):
    client.get("/v1/places", params={"q": "aloria"})
    lines = log_stream.lines()
    assert lines
    for line in lines:
        payload = json.loads(line)
        assert set(payload) >= {"ts", "level", "logger", "message"}
        assert len(line.encode("utf-8")) <= 8192
    request_line = json.loads(lines[-1])
    assert request_line["event"] == "http_request"
    assert request_line["status"] == 200
    assert request_line["path"] == "/v1/places"
    assert request_line["query"] == {"q": "aloria"}


def test_a_hostile_query_string_cannot_blow_up_the_log(client, log_stream):
    client.get("/v1/places", params={"q": "x" * 5000})
    lines = log_stream.lines()
    assert lines
    for line in lines:
        assert len(line.encode("utf-8")) <= 8192
        assert "x" * 200 not in line


def test_request_bodies_are_never_logged(admin_client, log_stream):
    from conftest import ADMIN_TOKEN

    secret_detail = "a-passenger-name-that-must-not-be-logged"
    response = admin_client.post(
        "/admin/corrections",
        json={
            "entityKind": "stop",
            "entityId": "ks_whatever",
            "summary": "Summary",
            "detail": secret_detail,
        },
        headers={"Authorization": f"Bearer {ADMIN_TOKEN}"},
    )
    assert response.status_code == 201
    logged = log_stream.text()
    assert logged
    assert secret_detail not in logged
    assert "Summary" not in logged


def test_the_admin_token_never_reaches_the_log(admin_client, log_stream):
    from conftest import ADMIN_TOKEN

    admin_client.get("/admin/audit", headers={"Authorization": f"Bearer {ADMIN_TOKEN}"})
    admin_client.get("/admin/audit", headers={"Authorization": "Bearer wrong"})
    logged = log_stream.text()
    assert ADMIN_TOKEN not in logged
    # The rejection itself is still recorded, without the credential.
    assert "admin_auth_rejected" in logged


def test_secret_looking_fields_are_redacted():
    redacted = safe_query(
        [("token", "abcd"), ("authorization", "Bearer x"), ("q", "aloria")]
    )
    assert redacted["token"] == "[redacted]"
    assert redacted["authorization"] == "[redacted]"
    assert redacted["q"] == "aloria"


def test_the_query_log_is_capped_in_both_directions():
    pairs = [(f"key{index}", "value" * 100) for index in range(100)]
    redacted = safe_query(pairs)
    assert len(redacted) <= 20
    assert all(len(value) <= 128 for value in redacted.values())
