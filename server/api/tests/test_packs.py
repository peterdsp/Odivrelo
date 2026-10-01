"""One generator, one shape.

A pack must carry exactly what the matching endpoint would have returned. If it
does not, an offline client and an online client disagree by construction, which
is the failure this whole arrangement exists to prevent. These tests compare the
two directly rather than checking that both look plausible.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

import pytest
from conftest import base_environ
from jsonschema import Draft202012Validator

from publicapi import packs
from publicapi.brand import BRAND

SCHEMA_DIR = Path(__file__).resolve().parents[3] / "data" / "schemas"


@pytest.fixture(scope="module")
def manifest(seeded) -> dict:
    return json.loads(
        (seeded["releaseDir"] / "manifest.json").read_text(encoding="utf-8")
    )


@pytest.fixture(scope="module")
def pack_bodies(seeded, manifest) -> dict:
    """Decode every JSON pack straight off disk, keyed by logical pack name."""
    bodies = {}
    for name, entry in manifest["files"].items():
        if entry["mediaType"] != "application/json":
            continue
        bodies[name] = json.loads(
            (seeded["releaseDir"] / entry["path"]).read_text(encoding="utf-8")
        )
    return bodies


@pytest.fixture(scope="module")
def service_dates(seeded) -> list[str]:
    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        from publicapi import repository

        return repository.pack_service_dates(connection)
    finally:
        connection.close()


def validator(stem: str) -> Draft202012Validator:
    document = json.loads(
        (SCHEMA_DIR / f"{stem}.schema.json").read_text(encoding="utf-8")
    )
    Draft202012Validator.check_schema(document)
    return Draft202012Validator(document)


# --------------------------------------------------------------------------- #
# The canonical set
# --------------------------------------------------------------------------- #

def test_the_manifest_names_exactly_the_canonical_set(manifest, service_dates):
    assert set(manifest["files"]) == packs.canonical_names(service_dates)


def test_no_staging_shaped_pack_survives(manifest):
    # The staging generator emitted these. A release must no longer carry them,
    # or a client would be reading a shape no endpoint returns.
    for retired in ("registry", "routes", "trips"):
        assert retired not in manifest["files"]
    assert not [name for name in manifest["files"] if name.startswith("trips-")]


def test_every_manifest_name_is_canonical(manifest):
    for name in manifest["files"]:
        assert packs.is_canonical(name), name


def test_one_journey_pack_exists_per_materialised_service_date(
    manifest, service_dates
):
    journey_packs = {
        name for name in manifest["files"] if name.startswith("journeys-")
    }
    assert journey_packs == {
        packs.journey_pack_name(date) for date in service_dates
    }
    # The fixture materialises the two daylight-saving dates it declares, the
    # weekday template date, and the date an added calendar exception names.
    assert "journeys-2026-10-02" in journey_packs
    assert "journeys-2026-03-29" in journey_packs
    assert "journeys-2026-04-11" in journey_packs


def test_the_gtfs_pack_is_still_a_zip(manifest):
    assert manifest["files"]["gtfs"]["mediaType"] == "application/zip"
    assert manifest["files"]["gtfs"]["path"].endswith(".zip")


def test_the_release_mechanism_is_unchanged(manifest, seeded):
    """Content addressing, digests and byte lengths still hold for every pack."""
    import hashlib

    assert manifest["contractVersion"] == BRAND.contract_version
    assert manifest["product"] == BRAND.name
    assert manifest["releaseId"] == seeded["releaseId"]
    for name, entry in manifest["files"].items():
        body = (seeded["releaseDir"] / entry["path"]).read_bytes()
        digest = hashlib.sha256(body).hexdigest()
        assert digest == entry["sha256"], name
        assert len(body) == entry["bytes"], name
        # The file name carries the first 16 hex characters of its own digest.
        stem = entry["path"].rsplit("/", 1)[-1].rsplit(".", 1)[0]
        assert stem == f"{name}-{digest[:16]}", name


def test_the_contract_document_lists_exactly_these_pack_names(service_dates):
    prose = (SCHEMA_DIR / "CONTRACT-v1.md").read_text(encoding="utf-8")
    for name in packs.FIXED_PACK_NAMES:
        assert f"`{name}`" in prose, name
    assert "`journeys-<serviceDate>`" in prose
    for retired in ("`registry`", "`routes`", "`trips-<date>`"):
        assert retired not in prose, retired


# --------------------------------------------------------------------------- #
# Schema conformance
# --------------------------------------------------------------------------- #

def test_the_manifest_validates_against_its_schema(manifest):
    validator("offline-manifest-v1").validate(manifest)


@pytest.mark.parametrize("name", sorted(packs.SCHEMA_FOR_PACK))
def test_every_fixed_pack_validates_against_its_schema(pack_bodies, name):
    validator(packs.SCHEMA_FOR_PACK[name]).validate(pack_bodies[name])


def test_every_journey_pack_validates_against_its_schema(pack_bodies):
    journeys = validator(packs.JOURNEY_PACK_SCHEMA)
    found = 0
    for name, body in pack_bodies.items():
        if not name.startswith("journeys-"):
            continue
        journeys.validate(body)
        found += 1
    assert found >= 3


def test_every_pack_carries_the_release_envelope(pack_bodies, seeded):
    for name, body in pack_bodies.items():
        assert body["contractVersion"] == BRAND.contract_version, name
        assert body["releaseId"] == seeded["releaseId"], name
        assert body["publishedAt"], name
        assert body["dataMode"] == "demo", name


# --------------------------------------------------------------------------- #
# Pack contents equal endpoint output
# --------------------------------------------------------------------------- #

def canonical(payload) -> bytes:
    return packs.canonical_json(payload)


def test_the_meta_pack_equals_the_meta_endpoint(client, pack_bodies):
    assert canonical(pack_bodies["meta"]) == canonical(client.get("/v1/meta").json())


def test_the_coverage_pack_equals_the_coverage_endpoint(client, pack_bodies):
    served = client.get("/v1/coverage").json()
    # Freshness is relative to now, so compare everything else exactly and the
    # freshness block structurally.
    pack = pack_bodies["coverage"]
    assert pack["coverage"]["freshness"].keys() == served["coverage"]["freshness"].keys()
    for payload in (pack, served):
        payload["coverage"].pop("freshness")
    assert canonical(pack) == canonical(served)


def test_the_sources_pack_equals_the_sources_endpoint(client, pack_bodies):
    assert canonical(pack_bodies["sources"]) == canonical(
        client.get("/v1/sources", params={"limit": 200}).json()
    )


def test_the_places_pack_equals_the_places_endpoint(client, pack_bodies):
    assert canonical(pack_bodies["places"]) == canonical(
        client.get("/v1/places", params={"limit": 200}).json()
    )


def test_every_operator_in_the_pack_equals_its_endpoint(client, pack_bodies):
    operators = pack_bodies["operators"]["operators"]
    assert operators
    for operator_id, operator in operators.items():
        served = client.get(f"/v1/operators/{operator_id}").json()["operator"]
        assert canonical(operator) == canonical(served), operator_id


def test_every_stop_in_the_pack_equals_its_endpoint(client, pack_bodies):
    body = pack_bodies["stops"]
    service_date = body["serviceDate"]
    assert body["stops"]
    for stop_id, stop in body["stops"].items():
        served = client.get(
            f"/v1/stops/{stop_id}", params={"date": service_date, "limit": 200}
        ).json()["stop"]
        assert _without_freshness(stop) == _without_freshness(served), stop_id


def _without_freshness(payload):
    """Strip the now-relative freshness blocks so the rest compares exactly."""
    import copy

    clone = copy.deepcopy(payload)
    for departure in clone.get("departures", []):
        departure.pop("freshness", None)
    clone.pop("freshness", None)
    return canonical(clone)


def test_every_journey_detail_in_a_pack_equals_its_endpoint(client, pack_bodies):
    checked = 0
    for name, body in pack_bodies.items():
        if not name.startswith("journeys-"):
            continue
        service_date = body["serviceDate"]
        for journey_id, detail in body["journeys"].items():
            served = client.get(
                f"/v1/journeys/{journey_id}", params={"date": service_date}
            ).json()["journey"]
            detail.pop("freshness", None)
            served.pop("freshness", None)
            assert canonical(detail) == canonical(served), (name, journey_id)
            checked += 1
    assert checked >= 5


def test_every_journey_result_in_a_pack_equals_a_search_for_the_same_pair(
    client, pack_bodies
):
    """A pack result is the whole journey, which is what searching it returns.

    The served id now also names the boarded leg, so it is the pack's trip id plus
    the exact pair searched. Everything else is compared byte for byte.
    """
    checked = 0
    for name, body in pack_bodies.items():
        if not name.startswith("journeys-"):
            continue
        service_date = body["serviceDate"]
        for result in body["results"]:
            served = client.get(
                "/v1/journeys",
                params={
                    "origin": result["departure"]["stopId"],
                    "destination": result["arrival"]["stopId"],
                    "date": service_date,
                },
            ).json()["results"]
            expected_id = (
                f"{result['id']}~{result['departure']['stopId']}"
                f"~{result['arrival']['stopId']}"
            )
            match = next(
                (item for item in served if item["id"] == expected_id), None
            )
            assert match is not None, (name, expected_id)
            result.pop("freshness", None)
            match.pop("freshness", None)
            match["id"] = result["id"]
            assert canonical(result) == canonical(match), (name, result["id"])
            checked += 1
    assert checked >= 5


def test_the_journey_pack_holds_a_detail_for_every_result(pack_bodies):
    for name, body in pack_bodies.items():
        if not name.startswith("journeys-"):
            continue
        assert {result["id"] for result in body["results"]} == set(body["journeys"])


def test_the_overnight_journey_is_in_its_departure_date_pack(pack_bodies, ids):
    body = pack_bodies["journeys-2026-10-02"]
    overnight = body["journeys"][ids["trip-overnight"]]
    assert overnight["serviceDate"] == "2026-10-02"
    assert overnight["crossesMidnight"] is True
    assert "journeys-2026-10-03" not in pack_bodies


def test_both_daylight_saving_dates_have_their_own_pack(pack_bodies, ids):
    spring = pack_bodies["journeys-2026-03-29"]["results"]
    assert [item["departure"]["at"] for item in spring] == [
        "2026-03-29T09:00:00+03:00"
    ]
    # 2026-10-25 is resolved from the Sunday calendar on request rather than
    # materialised, because the release only materialises declared dates.
    assert "journeys-2026-10-25" not in pack_bodies


def test_the_added_calendar_exception_date_is_materialised(pack_bodies, ids):
    added = pack_bodies["journeys-2026-04-11"]
    assert [item["id"] for item in added["results"]] == [
        ids["trip-weekday-afternoon"]
    ]
    assert added["results"][0]["serviceDate"] == "2026-04-11"
    assert added["results"][0]["departure"]["at"] == "2026-04-11T14:30:00+03:00"


def test_the_removed_calendar_exception_date_has_no_pack(pack_bodies):
    assert "journeys-2026-04-06" not in pack_bodies


def test_no_rights_pending_or_candidate_row_reaches_any_pack(pack_bodies):
    everything = json.dumps(pack_bodies, ensure_ascii=False)
    assert "Kalvara" not in everything
    assert "rights pending" not in everything.lower()
    assert "Draft stop awaiting review" not in everything


# --------------------------------------------------------------------------- #
# The served manifest and packs are the same bytes
# --------------------------------------------------------------------------- #

def test_the_api_serves_the_same_manifest_and_the_same_pack_bytes(
    client, seeded, manifest
):
    assert client.get("/v1/offline/manifest").json() == manifest
    for name, entry in manifest["files"].items():
        filename = entry["path"].rsplit("/", 1)[-1]
        response = client.get(f"/v1/offline/packs/{filename}")
        assert response.status_code == 200, name
        assert response.content == (seeded["releaseDir"] / entry["path"]).read_bytes()
        assert response.headers["ETag"] == f'"{entry["sha256"]}"'


@pytest.mark.parametrize(
    ("name", "canonical_name"),
    [
        ("meta", True),
        ("coverage", True),
        ("sources", True),
        ("places", True),
        ("operators", True),
        ("stops", True),
        ("gtfs", True),
        ("journeys-2026-10-02", True),
        ("journeys-not-a-date", False),
        ("journeys-20261002", False),
        ("registry", False),
        ("routes", False),
        ("trips", False),
        ("trips-2026-10-02", False),
    ],
)
def test_only_canonical_pack_names_are_recognised(name, canonical_name):
    assert packs.is_canonical(name) is canonical_name


def test_the_builder_emits_nothing_outside_the_canonical_set(seeded):
    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        payloads = packs.build_payloads(
            connection, seeded["releaseId"], data_mode="demo"
        )
    finally:
        connection.close()
    assert all(packs.is_canonical(name) for name in payloads)
    assert set(packs.FIXED_PACK_NAMES) <= set(payloads)


def test_the_generator_refuses_to_publish_a_non_canonical_name(monkeypatch, seeded):
    """A stray pack name is a hard failure, not a silently published extra."""
    original = packs.build_bodies

    def with_a_stray_pack(*args, **kwargs):
        bodies = original(*args, **kwargs)
        bodies["registry"] = {"smuggled": True}
        return bodies

    monkeypatch.setattr(packs, "build_bodies", with_a_stray_pack)
    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        with pytest.raises(ValueError, match="non-canonical pack name"):
            packs.build_payloads(connection, seeded["releaseId"], data_mode="demo")
    finally:
        connection.close()


def test_the_payload_provider_refuses_an_unknown_data_mode():
    with pytest.raises(ValueError, match="unknown data mode"):
        packs.payload_provider(data_mode="pretend")


def test_the_provider_is_what_the_seeder_passes_to_the_release_generator(seeded):
    """The seeder must not fall back to the staging payload shapes."""
    import inspect

    from publicapi import demo_seed

    source = inspect.getsource(demo_seed.seed)
    assert "payload_provider=packs.payload_provider" in source
