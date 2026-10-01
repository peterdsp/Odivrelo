"""Segment scoping: a journey is headlined by the leg the traveller searched.

The reported defect was that a result arrived at Veraki at 10:05 in the list and
then at Oravo at 12:10 in the detail, because the detail ignored the searched
pair. These tests pin the leg from the list through the detail so the two can
never disagree again.
"""
from __future__ import annotations

from conftest import (
    BAY_A1_EXTERNAL_ID,
    DAYTIME_DATE,
    MISTONA_EXTERNAL_ID,
    ORAVO_EXTERNAL_ID,
    ORIGIN_TERMINAL_EXTERNAL_ID,
    VERAKI_EXTERNAL_ID,
    trip_of,
)


def _search(client, origin, destination, date=DAYTIME_DATE, **extra):
    response = client.get(
        "/v1/journeys",
        params={"origin": origin, "destination": destination, "date": date, **extra},
    )
    assert response.status_code == 200, response.text
    return response.json()["results"]


def test_search_result_scopes_to_the_requested_alighting_stop(client, ids):
    results = _search(client, ids[ORIGIN_TERMINAL_EXTERNAL_ID], ids[VERAKI_EXTERNAL_ID])
    assert results, "the daytime services should serve Aloria to Veraki"
    first = results[0]
    # The leg stops at Veraki, not at the end of the run.
    assert first["arrival"]["stopId"] == ids[VERAKI_EXTERNAL_ID]
    assert first["arrival"]["at"] == f"{DAYTIME_DATE}T10:05:00+03:00"
    assert first["durationMinutes"] == 65
    assert first["intermediateStopCount"] == 0
    # The id carries the trip and the exact leg.
    assert first["id"].startswith(trip_of(first["id"]) + "~")
    assert first["id"].endswith(f"~{ids[VERAKI_EXTERNAL_ID]}")


def test_detail_follows_the_leg_in_the_result_id(client, ids):
    """The detail for a search result agrees with that result, leg for leg."""
    result = _search(client, ids[ORIGIN_TERMINAL_EXTERNAL_ID], ids[VERAKI_EXTERNAL_ID])[0]
    detail = client.get(
        f"/v1/journeys/{result['id']}", params={"date": DAYTIME_DATE}
    ).json()["journey"]

    assert detail["id"] == result["id"]
    assert detail["arrival"]["stopId"] == ids[VERAKI_EXTERNAL_ID]
    assert detail["arrival"]["at"] == f"{DAYTIME_DATE}T10:05:00+03:00"
    assert detail["durationMinutes"] == 65
    assert detail["selectedSegment"] == {
        "boardStopId": ids[BAY_A1_EXTERNAL_ID],
        "alightStopId": ids[VERAKI_EXTERNAL_ID],
    }
    # The whole run is still present, with the leg marked and the rest flagged as
    # served but outside the chosen leg.
    roles = {stop["stopId"]: stop["segmentRole"] for stop in detail["stops"]}
    assert roles[ids[BAY_A1_EXTERNAL_ID]] == "board"
    assert roles[ids[VERAKI_EXTERNAL_ID]] == "alight"
    assert roles[ids[MISTONA_EXTERNAL_ID]] == "afterAlight"
    assert roles[ids[ORAVO_EXTERNAL_ID]] == "afterAlight"
    # The boarding point is where the traveller gets on, not the run's first stop.
    assert detail["boardingPoint"]["stopId"] == ids[BAY_A1_EXTERNAL_ID]


def test_intermediate_boarding_marks_earlier_stops_before_board(client, ids):
    """Boarding at an intermediate stop flags the earlier ones as beforeBoard."""
    results = _search(client, ids[VERAKI_EXTERNAL_ID], ids[ORAVO_EXTERNAL_ID])
    assert results, "a leg from Veraki onward should exist"
    detail = client.get(
        f"/v1/journeys/{results[0]['id']}", params={"date": DAYTIME_DATE}
    ).json()["journey"]
    roles = {stop["stopId"]: stop["segmentRole"] for stop in detail["stops"]}
    assert roles[ids[BAY_A1_EXTERNAL_ID]] == "beforeBoard"
    assert roles[ids[VERAKI_EXTERNAL_ID]] == "board"
    assert roles[ids[ORAVO_EXTERNAL_ID]] == "alight"
    assert detail["departure"]["stopId"] == ids[VERAKI_EXTERNAL_ID]
    assert detail["boardingPoint"]["stopId"] == ids[VERAKI_EXTERNAL_ID]


def test_a_malformed_journey_id_is_rejected(client):
    response = client.get("/v1/journeys/one~two", params={"date": DAYTIME_DATE})
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_request"


def test_a_stale_segment_on_a_known_trip_falls_back_to_the_whole_run(client, ids):
    """A segment that cannot be placed degrades to the whole journey, not an error."""
    trip = trip_of(
        _search(client, ids[ORIGIN_TERMINAL_EXTERNAL_ID], ids[VERAKI_EXTERNAL_ID])[0]["id"]
    )
    # Board and alight ids that are not on this trip's stop list.
    bogus = f"{trip}~ks_deadbeefdeadbeefdeadbeef~ks_feedfacefeedfacefeedface"
    detail = client.get(f"/v1/journeys/{bogus}", params={"date": DAYTIME_DATE}).json()[
        "journey"
    ]
    assert detail["arrival"]["stopId"] == ids[ORAVO_EXTERNAL_ID]
    assert detail["selectedSegment"]["alightStopId"] == ids[ORAVO_EXTERNAL_ID]
