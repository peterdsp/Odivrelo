"""The gates that decide what may become public, and the ones that must not bend.

Three independent gates stand between an imported row and a public response:

* review: nothing is published until a reviewer publishes it;
* rights: review is not enough, the owning source must permit reuse;
* coordinates: a stop with an implausible position is quarantined.

The demonstration dataset relaxes exactly one thing, the Greek bounding box, and
only when the snapshot declares ``dataset='demo'``. Every test below exists to
prove that relaxation did not weaken anything else.
"""
from __future__ import annotations

import sqlite3

import pytest
from conftest import DAYTIME_DATE, ORAVO_EXTERNAL_ID, ORIGIN_TERMINAL_EXTERNAL_ID

from publicapi import demo_seed
from publicapi.release import demo_rows_present

from odivrelo_ktel import ktel_db
from odivrelo_ktel.ktel_ingest import import_normalized_snapshot, review_entity
from odivrelo_ktel.ktel_registry import coordinate_status, seed_registry

#: A coordinate in the open Atlantic near 0N 0E: valid on the globe, nowhere
#: near Greece, and used by the Aloria fixture.
ALORIA_LATITUDE = 0.4213
ALORIA_LONGITUDE = -0.5121


# --------------------------------------------------------------------------- #
# The coordinate gate
# --------------------------------------------------------------------------- #

def test_real_dataset_still_rejects_coordinates_outside_greece():
    # The default is the real-data path, and it must not have moved.
    assert coordinate_status(ALORIA_LATITUDE, ALORIA_LONGITUDE) == "outside_greece"
    assert coordinate_status(51.5, -0.1) == "outside_greece"
    assert coordinate_status(40.936, 24.412) == "valid"


def test_demo_dataset_accepts_the_same_coordinates():
    assert (
        coordinate_status(ALORIA_LATITUDE, ALORIA_LONGITUDE, dataset="demo") == "valid"
    )


@pytest.mark.parametrize(
    ("latitude", "longitude", "expected"),
    [
        (None, None, "missing"),
        (0, 0, "placeholder"),
        (900, 23, "out_of_range"),
        ("not-a-number", 23, "out_of_range"),
    ],
)
def test_demo_dataset_relaxes_only_the_greek_bounding_box(latitude, longitude, expected):
    # Every other coordinate gate still applies to a demonstration dataset.
    assert coordinate_status(latitude, longitude, dataset="demo") == expected
    assert coordinate_status(latitude, longitude) == expected


def test_an_unknown_dataset_kind_is_refused():
    with pytest.raises(ValueError, match="unknown dataset kind"):
        coordinate_status(40.0, 24.0, dataset="pretend")


def _fresh_ingest(tmp_path) -> sqlite3.Connection:
    connection = ktel_db.connect(str(tmp_path / "ingest.db"))
    ktel_db.migrate(connection)
    seed_registry(connection)
    return connection


def _snapshot(**overrides) -> dict:
    payload = {
        "operatorId": "ktel-kavala",
        "sourceId": "manual-review",
        "retrievedAt": "2026-09-20T06:00:00Z",
        "stops": [
            {
                "externalId": "stop-atlantic",
                "name": "A stop nowhere near Greece",
                "latitude": ALORIA_LATITUDE,
                "longitude": ALORIA_LONGITUDE,
            }
        ],
    }
    payload.update(overrides)
    return payload


def test_real_import_quarantines_an_out_of_country_stop(tmp_path):
    connection = _fresh_ingest(tmp_path)
    try:
        result = import_normalized_snapshot(connection, _snapshot())
        assert result["quarantined"] == 1
        row = connection.execute(
            "SELECT id, coordinate_status, publication_state FROM ktel_stops "
            "WHERE external_id='stop-atlantic'"
        ).fetchone()
        assert row["coordinate_status"] == "outside_greece"
        assert row["publication_state"] == "quarantined"

        # The review gate must refuse to publish it, whatever a reviewer wants.
        with pytest.raises(ValueError, match="quarantined coordinates"):
            review_entity(
                connection,
                entity_kind="stop",
                entity_id=row["id"],
                action="publish",
                reviewer="test",
            )
    finally:
        connection.close()


def test_demo_import_accepts_the_same_stop(tmp_path):
    connection = _fresh_ingest(tmp_path)
    try:
        result = import_normalized_snapshot(
            connection, _snapshot(dataset="demo")
        )
        assert result["quarantined"] == 0
        row = connection.execute(
            "SELECT id, coordinate_status, publication_state FROM ktel_stops "
            "WHERE external_id='stop-atlantic'"
        ).fetchone()
        assert row["coordinate_status"] == "valid"
        assert row["publication_state"] == "candidate"
        review_entity(
            connection,
            entity_kind="stop",
            entity_id=row["id"],
            action="publish",
            reviewer="test",
        )
    finally:
        connection.close()


# --------------------------------------------------------------------------- #
# Demonstration operator registration
# --------------------------------------------------------------------------- #

def test_an_unregistered_real_operator_id_is_still_rejected(tmp_path):
    connection = _fresh_ingest(tmp_path)
    try:
        with pytest.raises(ValueError, match="unknown ktel_operators id"):
            import_normalized_snapshot(
                connection, _snapshot(operatorId="ktel-does-not-exist")
            )
    finally:
        connection.close()


def test_demo_operators_are_refused_on_a_real_dataset(tmp_path):
    connection = _fresh_ingest(tmp_path)
    try:
        with pytest.raises(ValueError, match="only accepted in a snapshot flagged"):
            import_normalized_snapshot(
                connection,
                _snapshot(
                    operatorId="demo-somewhere",
                    demoOperators=[
                        {
                            "id": "demo-somewhere",
                            "nameEn": "Nowhere Lines",
                            "nameEl": "Nowhere Lines",
                        }
                    ],
                ),
            )
    finally:
        connection.close()


def test_a_demo_operator_id_must_carry_the_demo_prefix(tmp_path):
    connection = _fresh_ingest(tmp_path)
    try:
        with pytest.raises(ValueError, match="must start with"):
            import_normalized_snapshot(
                connection,
                _snapshot(
                    dataset="demo",
                    operatorId="ktel-kavala",
                    demoOperators=[
                        {
                            "id": "ktel-kavala",
                            "nameEn": "Impersonating a real operator",
                            "nameEl": "Impersonating a real operator",
                        }
                    ],
                ),
            )
    finally:
        connection.close()


def test_a_demo_operator_may_not_claim_a_federation_number(tmp_path):
    connection = _fresh_ingest(tmp_path)
    try:
        with pytest.raises(ValueError, match="must not claim a federation number"):
            import_normalized_snapshot(
                connection,
                _snapshot(
                    dataset="demo",
                    operatorId="demo-pretender",
                    demoOperators=[
                        {
                            "id": "demo-pretender",
                            "nameEn": "Pretender Lines",
                            "nameEl": "Pretender Lines",
                            "federationNumber": 7,
                        }
                    ],
                ),
            )
    finally:
        connection.close()


def test_the_registered_demo_operator_never_takes_a_federation_number(seeded):
    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        row = connection.execute(
            "SELECT federation_number, federation_status FROM ktel_operators "
            "WHERE id=?",
            (demo_seed.DEMO_OPERATOR_ID,),
        ).fetchone()
        assert row["federation_number"] is None
        assert row["federation_status"] == "not_listed"
        # The 62 real federation numbers are untouched.
        assert connection.execute(
            "SELECT COUNT(*) AS n FROM ktel_operators "
            "WHERE federation_number IS NOT NULL"
        ).fetchone()["n"] == 62
    finally:
        connection.close()


def test_both_fixtures_declare_the_identical_demo_operator():
    snapshot = demo_seed.load_fixture(demo_seed.SNAPSHOT_FILE)
    pending = demo_seed.load_fixture(demo_seed.RIGHTS_PENDING_FILE)
    assert snapshot["demoOperators"] == pending["demoOperators"]
    assert [item["id"] for item in snapshot["demoOperators"]] == [
        demo_seed.DEMO_OPERATOR_ID
    ]


def test_the_seeder_refuses_a_fixture_that_is_not_flagged_demo(tmp_path):
    unflagged = tmp_path / "unflagged.json"
    unflagged.write_text('{"operatorId": "ktel-kavala"}', encoding="utf-8")
    with pytest.raises(ValueError, match="not flagged as a demonstration dataset"):
        demo_seed.load_fixture(unflagged)


# --------------------------------------------------------------------------- #
# The rights gate
# --------------------------------------------------------------------------- #

def test_a_rights_pending_entity_is_approved_in_the_ingestion_database(seeded):
    """Review said yes. That is the premise of the next test, not a defect."""
    connection = sqlite3.connect(f"file:{seeded['ingestDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        stop = connection.execute(
            "SELECT publication_state, source_id FROM ktel_stops "
            "WHERE external_id='stop-kalvara-pier-pending'"
        ).fetchone()
        assert stop["publication_state"] == "published"
        assert stop["source_id"] == "ticketweb"
        trip = connection.execute(
            "SELECT publication_state FROM ktel_trips WHERE external_id='trip-pending'"
        ).fetchone()
        assert trip["publication_state"] == "published"
        rights = connection.execute(
            "SELECT rights_status FROM ktel_sources WHERE id='ticketweb'"
        ).fetchone()
        assert rights["rights_status"] == "permission_pending"
    finally:
        connection.close()


def test_the_rights_gate_keeps_the_approved_entity_out_of_the_public_database(seeded):
    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    try:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM ktel_stops WHERE source_id='ticketweb'"
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM ktel_trips WHERE source_id='ticketweb'"
            ).fetchone()[0]
            == 0
        )
    finally:
        connection.close()


def _every_public_body(client, ids) -> str:
    paths = [
        "/v1/meta",
        "/v1/places",
        "/v1/places?q=kalvara",
        "/v1/places?q=pending",
        "/v1/operators",
        "/v1/coverage",
        "/v1/sources",
        "/v1/offline/manifest",
        f"/v1/journeys?origin={ids[ORIGIN_TERMINAL_EXTERNAL_ID]}"
        f"&destination={ids[ORAVO_EXTERNAL_ID]}&date={DAYTIME_DATE}",
    ]
    for external_id in ("trip-daytime", "trip-overnight"):
        paths.append(f"/v1/journeys/{ids[external_id]}?date={DAYTIME_DATE}")
    manifest = client.get("/v1/offline/manifest").json()
    for entry in manifest["files"].values():
        if entry["mediaType"] == "application/json":
            paths.append(f"/v1/offline/packs/{entry['path'].rsplit('/', 1)[-1]}")
    combined = []
    for path in paths:
        response = client.get(path)
        assert response.status_code == 200, path
        combined.append(response.text)
    return "\n".join(combined)


def test_a_rights_pending_entity_appears_in_no_public_response(client, ids):
    everything = _every_public_body(client, ids)
    assert "Kalvara" not in everything
    assert "stop-kalvara-pier-pending" not in everything
    assert "rights pending" not in everything.lower()
    # The source itself is still published, with its status stated honestly.
    sources = client.get("/v1/sources").json()["sources"]
    assert any(
        source["id"] == "ticketweb" and source["rightsStatus"] == "permission_pending"
        for source in sources
    )


def test_a_candidate_entity_appears_in_no_public_response(client, ids, seeded):
    connection = sqlite3.connect(f"file:{seeded['ingestDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        row = connection.execute(
            "SELECT publication_state FROM ktel_stops "
            "WHERE external_id='stop-unreviewed-candidate'"
        ).fetchone()
        assert row["publication_state"] == "candidate"
    finally:
        connection.close()

    everything = _every_public_body(client, ids)
    assert "Draft stop awaiting review" not in everything
    assert "stop-unreviewed-candidate" not in everything
    assert client.get("/v1/places", params={"q": "draft"}).json()["total"] == 0


def test_the_compiled_release_holds_only_published_rows(seeded):
    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    try:
        for table in (
            "ktel_stop_places",
            "ktel_stops",
            "ktel_lines",
            "ktel_journey_patterns",
            "ktel_service_calendars",
            "ktel_trips",
        ):
            unpublished = connection.execute(
                f"SELECT COUNT(*) FROM {table} WHERE publication_state <> 'published'"
            ).fetchone()[0]
            assert unpublished == 0, table
    finally:
        connection.close()


# --------------------------------------------------------------------------- #
# Data mode consistency
# --------------------------------------------------------------------------- #

def test_the_demo_release_is_detected_as_failing_the_real_coordinate_gate(seeded):
    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        assert demo_rows_present(connection) is True
    finally:
        connection.close()


def test_readyz_refuses_to_serve_the_demo_release_as_real_data(seeded):
    from fastapi.testclient import TestClient

    from conftest import base_environ
    from publicapi.app import create_app
    from publicapi.brand import BRAND

    app = create_app(
        environ=base_environ(seeded, **{f"{BRAND.env_prefix}_DATA_MODE": "real"})
    )
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/readyz")
    assert response.status_code == 503
    body = response.json()
    assert body["status"] == "not_ready"
    assert body["checks"]["dataMode"] == "failed"
    assert any("real data" in problem for problem in body["problems"])
