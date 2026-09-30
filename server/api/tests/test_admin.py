"""The private administrative surface: absent by default, guarded, audited."""
from __future__ import annotations

import json

import pytest
from conftest import ADMIN_TOKEN, base_environ
from fastapi.testclient import TestClient

from publicapi.app import create_app
from publicapi.brand import BRAND
from publicapi.config import ConfigError, load_settings

AUTH = {"Authorization": f"Bearer {ADMIN_TOKEN}"}

ADMIN_PATHS = (
    ("get", "/admin/review/queue"),
    ("get", "/admin/audit"),
    ("get", "/admin/corrections"),
    ("get", "/admin/takedowns"),
    ("get", "/admin/releases"),
)


# --------------------------------------------------------------------------- #
# Mounting
# --------------------------------------------------------------------------- #

def test_admin_routes_are_absent_when_the_flag_is_off(client):
    for method, path in ADMIN_PATHS:
        response = getattr(client, method)(path, headers=AUTH)
        # Not 401: the route does not exist at all on a public deployment.
        assert response.status_code == 404, path
        assert response.json()["error"]["code"] == "not_found"


def test_no_admin_route_is_in_the_public_openapi_document(client):
    document = client.get("/openapi.json").json()
    assert not [path for path in document["paths"] if path.startswith("/admin")]


def test_the_admin_router_is_not_even_imported_by_a_public_app(seeded):
    app = create_app(environ=base_environ(seeded))
    mounted = {getattr(route, "path", "") for route in app.routes}
    assert not [path for path in mounted if path.startswith("/admin")]


def test_admin_routes_are_present_when_the_flag_is_on(admin_client):
    for method, path in ADMIN_PATHS:
        response = getattr(admin_client, method)(path, headers=AUTH)
        assert response.status_code == 200, path


def test_the_service_refuses_to_start_with_admin_enabled_and_no_token(seeded):
    environment = base_environ(
        seeded,
        **{
            f"{BRAND.env_prefix}_ADMIN_ENABLED": "true",
            f"{BRAND.env_prefix}_INGEST_DB_PATH": str(seeded["ingestDb"]),
            f"{BRAND.env_prefix}_ADMIN_DB_PATH": str(seeded["root"] / "admin.db"),
        },
    )
    with pytest.raises(ConfigError, match="ADMIN_API_TOKEN is required"):
        load_settings(environment)


def test_the_service_refuses_a_short_admin_token(seeded):
    environment = base_environ(
        seeded,
        **{
            f"{BRAND.env_prefix}_ADMIN_ENABLED": "true",
            f"{BRAND.env_prefix}_INGEST_DB_PATH": str(seeded["ingestDb"]),
            f"{BRAND.env_prefix}_ADMIN_DB_PATH": str(seeded["root"] / "admin.db"),
            "ADMIN_API_TOKEN": "short",
        },
    )
    with pytest.raises(ConfigError, match="at least 32 characters"):
        load_settings(environment)


def test_admin_enabled_requires_both_database_paths(seeded):
    environment = base_environ(
        seeded,
        **{
            f"{BRAND.env_prefix}_ADMIN_ENABLED": "true",
            "ADMIN_API_TOKEN": ADMIN_TOKEN,
        },
    )
    with pytest.raises(ConfigError) as error:
        load_settings(environment)
    assert "INGEST_DB_PATH" in str(error.value)
    assert "ADMIN_DB_PATH" in str(error.value)


# --------------------------------------------------------------------------- #
# Authentication
# --------------------------------------------------------------------------- #

@pytest.mark.parametrize(
    "headers",
    [
        {},
        {"Authorization": "Bearer"},
        {"Authorization": "Bearer wrong-token-of-exactly-the-right-length-000000"},
        {"Authorization": f"Basic {ADMIN_TOKEN}"},
        {"Authorization": f"bearer {ADMIN_TOKEN}x"},
    ],
    ids=["missing", "empty", "wrong", "wrong-scheme", "near-miss"],
)
def test_admin_rejects_a_missing_or_wrong_token(admin_client, headers):
    response = admin_client.get("/admin/audit", headers=headers)
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "unauthorized"
    assert response.headers["WWW-Authenticate"] == "Bearer"
    assert response.headers["Cache-Control"] == "no-store"


def test_a_lowercase_bearer_scheme_is_accepted(admin_client):
    response = admin_client.get(
        "/admin/audit", headers={"Authorization": f"bearer {ADMIN_TOKEN}"}
    )
    assert response.status_code == 200


def test_every_admin_response_is_uncacheable(admin_client):
    for method, path in ADMIN_PATHS:
        response = getattr(admin_client, method)(path, headers=AUTH)
        assert response.headers["Cache-Control"] == "no-store", path
        assert response.headers["Pragma"] == "no-cache", path


def test_the_admin_token_is_never_echoed_or_logged(admin_client, caplog):
    import logging

    with caplog.at_level(logging.INFO, logger="publicapi"):
        ok = admin_client.get("/admin/audit", headers=AUTH)
        rejected = admin_client.get(
            "/admin/audit", headers={"Authorization": "Bearer nope"}
        )
    assert ok.status_code == 200
    assert rejected.status_code == 401
    assert ADMIN_TOKEN not in ok.text
    assert ADMIN_TOKEN not in rejected.text
    for record in caplog.records:
        assert ADMIN_TOKEN not in record.getMessage()
        assert ADMIN_TOKEN not in json.dumps(
            {
                key: str(value)
                for key, value in record.__dict__.items()
                if not key.startswith("_")
            }
        )


# --------------------------------------------------------------------------- #
# Review queue and decisions
# --------------------------------------------------------------------------- #

def test_the_review_queue_shows_the_held_back_candidate(admin_client):
    body = admin_client.get(
        "/admin/review/queue", params={"entity_kind": "stop", "state": "candidate"},
        headers=AUTH,
    ).json()
    assert body["total"] == 1
    assert body["queue"][0]["entityKind"] == "stop"
    assert body["queue"][0]["state"] == "candidate"


def test_the_review_queue_refuses_an_unknown_entity_kind(admin_client):
    response = admin_client.get(
        "/admin/review/queue", params={"entity_kind": "spaceship"}, headers=AUTH
    )
    assert response.status_code == 400
    assert response.json()["error"]["field"] == "entity_kind"


def test_publishing_a_candidate_is_recorded_in_the_audit_trail(admin_client):
    queued = admin_client.get(
        "/admin/review/queue", params={"entity_kind": "stop"}, headers=AUTH
    ).json()["queue"][0]

    response = admin_client.post(
        f"/admin/review/stop/{queued['entityId']}",
        json={"action": "publish", "reason": "Reviewed against the fixture."},
        headers=AUTH,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["reviewed"] is True
    assert body["audit"]["action"] == "review.publish"
    assert body["audit"]["outcome"] == "succeeded"
    assert body["audit"]["subjectId"] == queued["entityId"]

    trail = admin_client.get("/admin/audit", headers=AUTH).json()
    assert trail["total"] >= 1
    assert trail["events"][0]["action"] == "review.publish"
    assert trail["events"][0]["detail"]["reason"] == "Reviewed against the fixture."


def test_reviewing_an_unknown_entity_is_a_404_and_is_still_audited(admin_client):
    response = admin_client.post(
        "/admin/review/stop/ks_does_not_exist",
        json={"action": "publish"},
        headers=AUTH,
    )
    assert response.status_code == 404
    trail = admin_client.get("/admin/audit", headers=AUTH).json()
    assert trail["events"][0]["outcome"] == "refused"


def test_an_unsupported_review_action_is_refused(admin_client):
    response = admin_client.post(
        "/admin/review/stop/anything", json={"action": "bulldoze"}, headers=AUTH
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_request"


# --------------------------------------------------------------------------- #
# Corrections
# --------------------------------------------------------------------------- #

def test_a_correction_can_be_recorded_listed_and_resolved(admin_client, ids):
    created = admin_client.post(
        "/admin/corrections",
        json={
            "entityKind": "stop",
            "entityId": ids["stop-oravo-junction"],
            "summary": "The bay label is wrong.",
            "detail": "Reported by a passenger at the terminal.",
            "reporterContact": "passenger@example.invalid",
        },
        headers=AUTH,
    )
    assert created.status_code == 201
    correction = created.json()["correction"]
    assert correction["status"] == "open"

    listed = admin_client.get(
        "/admin/corrections", params={"status": "open"}, headers=AUTH
    ).json()
    assert listed["total"] == 1
    assert listed["corrections"][0]["id"] == correction["id"]

    resolved = admin_client.post(
        f"/admin/corrections/{correction['id']}",
        json={"status": "accepted", "resolution": "Bay label corrected."},
        headers=AUTH,
    )
    assert resolved.status_code == 200
    assert resolved.json()["correction"]["status"] == "accepted"
    assert resolved.json()["correction"]["resolvedAt"]

    # Resolving twice is refused: the second call has nothing open to act on.
    again = admin_client.post(
        f"/admin/corrections/{correction['id']}",
        json={"status": "rejected"},
        headers=AUTH,
    )
    assert again.status_code == 404


def test_a_correction_needs_a_summary(admin_client):
    response = admin_client.post(
        "/admin/corrections",
        json={"entityKind": "stop", "entityId": "x", "summary": ""},
        headers=AUTH,
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_request"


# --------------------------------------------------------------------------- #
# Takedown
# --------------------------------------------------------------------------- #

def test_a_takedown_withdraws_the_entity_and_records_the_request(admin_client, ids):
    import sqlite3

    stop_id = ids["stop-mistona-village"]
    response = admin_client.post(
        "/admin/takedown",
        json={
            "entityKind": "stop",
            "entityId": stop_id,
            "requester": "Operator legal contact",
            "reason": "Reuse permission withdrawn.",
        },
        headers=AUTH,
    )
    assert response.status_code == 201
    assert response.json()["takedown"]["status"] == "actioned"

    connection = sqlite3.connect(
        f"file:{admin_client.paths['ingest']}?mode=ro", uri=True
    )
    connection.row_factory = sqlite3.Row
    try:
        row = connection.execute(
            "SELECT publication_state FROM ktel_stops WHERE id=?", (stop_id,)
        ).fetchone()
        assert row["publication_state"] == "withdrawn"
        event = connection.execute(
            "SELECT action, reason FROM ktel_review_events WHERE entity_id=? "
            "ORDER BY id DESC LIMIT 1",
            (stop_id,),
        ).fetchone()
        assert event["action"] == "withdraw"
        assert event["reason"].startswith("takedown:")
    finally:
        connection.close()

    listed = admin_client.get("/admin/takedowns", headers=AUTH).json()
    assert listed["total"] == 1
    assert listed["takedowns"][0]["entityId"] == stop_id


def test_a_takedown_for_an_unknown_entity_is_refused_and_recorded(admin_client):
    response = admin_client.post(
        "/admin/takedown",
        json={
            "entityKind": "stop",
            "entityId": "ks_nope",
            "requester": "Someone",
            "reason": "Testing the refusal path.",
        },
        headers=AUTH,
    )
    assert response.status_code == 404
    listed = admin_client.get("/admin/takedowns", headers=AUTH).json()
    assert listed["takedowns"][0]["status"] == "refused"


# --------------------------------------------------------------------------- #
# Publish and rollback
# --------------------------------------------------------------------------- #

def test_publishing_is_idempotent_for_an_unchanged_database(admin_client):
    before = admin_client.get("/admin/releases", headers=AUTH).json()
    first = admin_client.post("/admin/releases/publish", headers=AUTH)
    assert first.status_code == 200
    assert first.json()["releaseId"] == before["release"]["releaseId"]


def test_a_takedown_then_publish_produces_a_new_release_that_rollback_reverts(
    admin_client, ids
):
    original = admin_client.get("/admin/releases", headers=AUTH).json()["release"][
        "releaseId"
    ]

    admin_client.post(
        "/admin/takedown",
        json={
            "entityKind": "trip",
            "entityId": ids["trip-overnight"],
            "requester": "Operator legal contact",
            "reason": "Overnight service suspended.",
        },
        headers=AUTH,
    )
    published = admin_client.post("/admin/releases/publish", headers=AUTH)
    assert published.status_code == 200
    new_release = published.json()["releaseId"]
    assert new_release != original
    assert admin_client.get("/readyz").status_code == 200

    # The withdrawn journey is gone from the new release.
    detail = admin_client.get(f"/v1/journeys/{ids['trip-overnight']}")
    assert detail.status_code == 404

    rolled_back = admin_client.post("/admin/releases/rollback", headers=AUTH)
    assert rolled_back.status_code == 200
    assert rolled_back.json()["releaseId"] == original
    assert rolled_back.json()["databaseRestored"] is True
    # Rolling back the manifest alone would leave a mixed snapshot, so the
    # database is swapped back with it and readiness stays green.
    assert admin_client.get("/readyz").status_code == 200
    assert (
        admin_client.get("/v1/offline/manifest").json()["releaseId"] == original
    )
    assert admin_client.get(f"/v1/journeys/{ids['trip-overnight']}").status_code == 200

    trail = admin_client.get("/admin/audit", headers=AUTH).json()
    actions = [event["action"] for event in trail["events"]]
    assert "release.rollback" in actions
    assert "release.publish" in actions
    assert "takedown" in actions


def test_rollback_without_a_previous_manifest_is_refused(admin_client):
    response = admin_client.post("/admin/releases/rollback", headers=AUTH)
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "unavailable"
    trail = admin_client.get("/admin/audit", headers=AUTH).json()
    assert trail["events"][0]["outcome"] == "refused"


def test_admin_pagination_is_bounded(admin_client):
    response = admin_client.get(
        "/admin/audit", params={"limit": 201}, headers=AUTH
    )
    assert response.status_code == 400
    assert response.json()["error"]["field"] == "limit"
