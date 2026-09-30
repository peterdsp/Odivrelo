"""Happy path for every public endpoint, against the seeded demonstration release."""
from __future__ import annotations

import io
import zipfile

import pytest
from conftest import (
    BAY_A1_EXTERNAL_ID,
    DAYTIME_DATE,
    ORAVO_EXTERNAL_ID,
    ORIGIN_TERMINAL_EXTERNAL_ID,
)

from publicapi.brand import BRAND

DEMO_OPERATOR = "demo-aloria-coach"


def envelope_is_sound(payload: dict, release_id: str) -> None:
    assert payload["contractVersion"] == BRAND.contract_version
    assert payload["releaseId"] == release_id
    assert payload["publishedAt"]
    assert payload["dataMode"] == "demo"


def test_healthz_reports_liveness_without_touching_the_database(client):
    response = client.get("/healthz")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["product"] == BRAND.name
    assert body["contractVersion"] == BRAND.contract_version
    assert response.headers["Cache-Control"] == "no-store"


def test_readyz_passes_on_a_complete_release(client, seeded):
    response = client.get("/readyz")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready"
    assert body["releaseId"] == seeded["releaseId"]
    assert body["problems"] == []
    assert body["checks"] == {
        "publicDatabase": "ok",
        "integrity": "ok",
        "publishedRelease": "ok",
        "dataMode": "ok",
        "manifest": "ok",
        "releaseAgreement": "ok",
    }


def test_meta_describes_the_release_and_the_product(client, seeded):
    response = client.get("/v1/meta")
    assert response.status_code == 200
    body = response.json()
    envelope_is_sound(body, seeded["releaseId"])
    assert body["product"]["name"] == BRAND.name
    assert body["product"]["version"] == BRAND.version
    assert body["languages"] == list(BRAND.languages)
    assert body["coverage"]["state"] == "demo"
    assert body["coverage"]["operatorCount"] == 1
    assert body["offlineManifestUrl"] == "/v1/offline/manifest"
    assert body["gtfsUrl"] == "/v1/gtfs"
    # Attribution only ever lists sources whose reuse rights are documented.
    assert body["attribution"]
    assert all(entry["name"] for entry in body["attribution"])


def test_places_search_is_case_and_accent_insensitive(client, seeded, ids):
    plain = client.get("/v1/places", params={"q": "aloria"})
    assert plain.status_code == 200
    body = plain.json()
    envelope_is_sound(body, seeded["releaseId"])
    assert body["total"] == 3
    kinds = {place["kind"] for place in body["places"]}
    assert kinds == {"stop_place", "stop"}

    terminal = next(
        place for place in body["places"] if place["kind"] == "stop_place"
    )
    assert terminal["id"] == ids[ORIGIN_TERMINAL_EXTERNAL_ID]
    assert terminal["boardingPointCount"] == 2
    assert terminal["parentId"] is None
    assert terminal["coverage"] == "demo"
    assert set(terminal["name"]) == set(BRAND.languages)

    # The same term with different case and Greek accents finds the same rows.
    upper = client.get("/v1/places", params={"q": "ALORIA"})
    assert upper.json()["total"] == 3
    accented = client.get("/v1/places", params={"q": "Αλορια"})
    assert accented.json()["total"] == 3


def test_places_search_without_a_term_returns_every_place(client):
    body = client.get("/v1/places").json()
    # One terminal plus five published stops.
    assert body["total"] == 6


def test_journeys_returns_the_demonstration_services(client, seeded, ids):
    response = client.get(
        "/v1/journeys",
        params={
            "origin": ids[ORIGIN_TERMINAL_EXTERNAL_ID],
            "destination": ids[ORAVO_EXTERNAL_ID],
            "date": DAYTIME_DATE,
        },
    )
    assert response.status_code == 200
    body = response.json()
    envelope_is_sound(body, seeded["releaseId"])
    assert body["query"] == {
        "originId": ids[ORIGIN_TERMINAL_EXTERNAL_ID],
        "destinationId": ids[ORAVO_EXTERNAL_ID],
        "date": DAYTIME_DATE,
    }
    assert body["coverage"] == "demo"
    assert body["unavailableReason"] is None
    assert len(body["results"]) == 3

    first = body["results"][0]
    assert first["departure"]["at"] == f"{DAYTIME_DATE}T09:00:00+03:00"
    assert first["arrival"]["at"] == f"{DAYTIME_DATE}T12:10:00+03:00"
    assert first["durationMinutes"] == 190
    assert first["intermediateStopCount"] == 2
    assert first["serviceDate"] == DAYTIME_DATE
    assert first["crossesMidnight"] is False
    assert first["positionQuality"] == "scheduled"
    assert first["fare"] == {"amount": 18.0, "currency": "EUR", "isIndicative": True}
    assert first["freshness"]["state"] in {"fresh", "aging", "stale"}
    assert first["confidence"] == "reviewed"
    assert first["operator"]["id"] == DEMO_OPERATOR
    assert first["operator"]["name"]["sq"] == "Autobusët Aloria (demonstrim)"
    assert first["operator"]["logoAvailable"] is False


def test_journey_detail_carries_stops_geometry_and_provenance(client, seeded, ids):
    journey_id = ids["trip-daytime"]
    response = client.get(
        f"/v1/journeys/{journey_id}", params={"date": DAYTIME_DATE}
    )
    assert response.status_code == 200
    body = response.json()
    envelope_is_sound(body, seeded["releaseId"])
    journey = body["journey"]

    assert journey["id"] == journey_id
    assert [stop["sequence"] for stop in journey["stops"]] == [1, 2, 3, 4]
    assert journey["stops"][0]["departureAt"] == f"{DAYTIME_DATE}T09:00:00+03:00"
    assert journey["stops"][0]["pickup"] == "allowed"
    assert journey["stops"][0]["dropoff"] == "not_allowed"
    # The intermediate stop that may be alighted at but not boarded.
    mistona = journey["stops"][2]
    assert mistona["pickup"] == "not_allowed"
    assert mistona["dropoff"] == "allowed"
    assert mistona["timeQuality"] == "approximate"

    boarding = journey["boardingPoint"]
    assert boarding["stopId"] == ids[BAY_A1_EXTERNAL_ID]
    assert boarding["bay"] == "A1"
    assert boarding["stepFree"] is True
    assert boarding["reviewState"] == "published"
    assert boarding["reviewedAt"] == "2026-09-10T08:00:00Z"
    assert set(boarding["instructions"]) == set(BRAND.languages)
    assert set(boarding["terminalName"]) == set(BRAND.languages)

    geometry = journey["geometry"]
    assert geometry["type"] == "LineString"
    assert geometry["confidence"] == "ordered_stops_only"
    assert len(geometry["coordinates"]) == 4
    assert geometry["attribution"]

    assert journey["provenance"][0]["sourceId"] == "manual-review"
    assert journey["provenance"][0]["rightsStatus"] == "allowed"
    assert journey["purchase"]["kind"] == "online"
    assert journey["correctionUrl"].startswith(BRAND.url)
    assert journey["restrictions"] == []


def test_operators_lists_only_operators_with_published_content(client, seeded):
    body = client.get("/v1/operators").json()
    envelope_is_sound(body, seeded["releaseId"])
    # The registry holds 64 operators. Only one carries published rows.
    assert body["total"] == 1
    operator = body["operators"][0]
    assert operator["id"] == DEMO_OPERATOR
    assert operator["federationNumber"] is None
    assert operator["coverage"] == {"state": "demo", "routeCount": 2, "stopCount": 5}
    assert operator["contact"]["email"] == BRAND.support_email
    assert operator["correctionUrl"].startswith(BRAND.url)
    assert [source["rightsStatus"] for source in operator["sources"]] == ["allowed"]


def test_operator_detail_matches_the_list_entry(client, seeded):
    listed = client.get("/v1/operators").json()["operators"][0]
    detail = client.get(f"/v1/operators/{DEMO_OPERATOR}").json()["operator"]
    assert detail == listed


def test_stop_detail_is_a_station_page(client, seeded, ids):
    stop_id = ids[BAY_A1_EXTERNAL_ID]
    body = client.get(f"/v1/stops/{stop_id}", params={"date": DAYTIME_DATE}).json()
    envelope_is_sound(body, seeded["releaseId"])
    stop = body["stop"]
    assert stop["id"] == stop_id
    assert stop["kind"] == "stop"
    assert stop["bay"] == "A1"
    assert stop["stepFree"] is True
    assert stop["terminal"]["id"] == ids[ORIGIN_TERMINAL_EXTERNAL_ID]
    assert {point["bay"] for point in stop["boardingPoints"]} == {"A1", "A2"}
    assert stop["operatorIds"] == [DEMO_OPERATOR]
    assert stop["serviceDate"] == DAYTIME_DATE
    assert stop["coverage"] == "demo"
    assert stop["provenance"][0]["sourceId"] == "manual-review"
    assert stop["correctionUrl"].startswith(BRAND.url)
    # Two southbound services call at bay A1 on this date; the overnight uses A2.
    assert len(stop["departures"]) == 2
    assert all(
        departure["departure"]["stopId"] == stop_id
        for departure in stop["departures"]
    )


def test_coverage_states_what_is_not_covered(client, seeded):
    body = client.get("/v1/coverage").json()
    envelope_is_sound(body, seeded["releaseId"])
    coverage = body["coverage"]
    assert coverage["state"] == "demo"
    assert coverage["operatorCount"] == 1
    assert coverage["stopCount"] == 5
    assert coverage["journeyCount"] == 4
    assert coverage["serviceDates"] == {"from": "2026-03-29", "to": "2026-10-02"}
    assert "Aloria" in coverage["note"]
    assert any("Aloria is an invented region" in item for item in coverage["notCovered"])
    assert coverage["absenceSemantics"]
    assert coverage["operators"][0]["operatorId"] == DEMO_OPERATOR


def test_sources_publishes_rights_status_per_source(client, seeded):
    body = client.get("/v1/sources").json()
    envelope_is_sound(body, seeded["releaseId"])
    assert body["total"] == 5
    by_id = {source["id"]: source for source in body["sources"]}
    assert by_id["manual-review"]["rightsStatus"] == "allowed"
    assert by_id["ticketweb"]["rightsStatus"] == "permission_pending"


def test_offline_manifest_mirrors_the_generated_manifest(client, seeded):
    import json

    served = client.get("/v1/offline/manifest").json()
    on_disk = json.loads(
        (seeded["releaseDir"] / "manifest.json").read_text(encoding="utf-8")
    )
    assert served == on_disk
    assert served["releaseId"] == seeded["releaseId"]
    assert served["contractVersion"] == BRAND.contract_version
    assert served["product"] == BRAND.name
    # The shape the web client and the apps both decode: files keyed by logical
    # pack name, each with a packs/<name>-<digest>.<ext> path.
    for entry in served["files"].values():
        assert entry["path"].startswith("packs/")
        assert len(entry["sha256"]) == 64
        assert entry["bytes"] > 0
        assert entry["mediaType"] in {"application/json", "application/zip"}


def test_every_pack_in_the_manifest_is_served_byte_for_byte(client, seeded):
    manifest = client.get("/v1/offline/manifest").json()
    for entry in manifest["files"].values():
        filename = entry["path"].rsplit("/", 1)[-1]
        response = client.get(f"/v1/offline/packs/{filename}")
        assert response.status_code == 200, filename
        on_disk = (seeded["releaseDir"] / entry["path"]).read_bytes()
        assert response.content == on_disk
        assert len(response.content) == entry["bytes"]


def test_gtfs_feed_is_the_release_zip(client, seeded):
    response = client.get("/v1/gtfs")
    assert response.status_code == 200
    assert response.headers["content-type"] == "application/zip"
    with zipfile.ZipFile(io.BytesIO(response.content)) as archive:
        names = set(archive.namelist())
        assert {"agency.txt", "stops.txt", "trips.txt", "stop_times.txt"} <= names
        stop_times = archive.read("stop_times.txt").decode("utf-8")
        feed_info = archive.read("feed_info.txt").decode("utf-8")
    # The overnight arrival is expressed past 24:00 on its own service day.
    assert "25:20:00" in stop_times
    assert seeded["releaseId"] in feed_info
    assert BRAND.name in feed_info


@pytest.mark.parametrize(
    "path",
    [
        "/v1/meta",
        "/v1/places",
        "/v1/operators",
        "/v1/coverage",
        "/v1/sources",
        "/v1/offline/manifest",
    ],
)
def test_release_scoped_json_revalidates_with_an_etag(client, path):
    first = client.get(path)
    assert first.status_code == 200
    tag = first.headers["ETag"]
    assert tag.startswith('"')
    assert "max-age" in first.headers["Cache-Control"]
    assert "immutable" not in first.headers["Cache-Control"]
    again = client.get(path, headers={"If-None-Match": tag})
    assert again.status_code == 304
