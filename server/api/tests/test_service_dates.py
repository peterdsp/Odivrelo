"""Service-date semantics: past midnight, daylight saving, calendar exceptions.

These are the behaviours the clients are forbidden from recomputing, so they are
pinned here against the served responses rather than against a helper function.
"""
from __future__ import annotations

from conftest import (
    trip_of,
    AUTUMN_BACK_DATE,
    BAY_A1_EXTERNAL_ID,
    BAY_A2_EXTERNAL_ID,
    CALENDAR_ADDED_DATE,
    CALENDAR_NORMAL_DATE,
    CALENDAR_REMOVED_DATE,
    DAYTIME_DATE,
    ORAVO_EXTERNAL_ID,
    ORIGIN_TERMINAL_EXTERNAL_ID,
    SPRING_FORWARD_DATE,
)


def search(client, ids, date, **extra):
    response = client.get(
        "/v1/journeys",
        params={
            "origin": ids[ORIGIN_TERMINAL_EXTERNAL_ID],
            "destination": ids[ORAVO_EXTERNAL_ID],
            "date": date,
            **extra,
        },
    )
    assert response.status_code == 200
    return response.json()


def test_overnight_journey_keeps_its_service_date_and_reports_crossing(client, ids):
    body = search(client, ids, DAYTIME_DATE)
    overnight = next(
        result for result in body["results"] if trip_of(result["id"]) == ids["trip-overnight"]
    )
    assert overnight["crossesMidnight"] is True
    # The service date stays on the departure day even though arrival is the next.
    assert overnight["serviceDate"] == DAYTIME_DATE
    assert overnight["departure"]["at"] == f"{DAYTIME_DATE}T23:40:00+03:00"
    assert overnight["arrival"]["at"] == "2026-10-03T01:20:00+03:00"
    assert overnight["durationMinutes"] == 100
    assert overnight["departure"]["stopId"] == ids[BAY_A2_EXTERNAL_ID]


def test_overnight_detail_keeps_the_requested_service_date(client, ids):
    body = client.get(
        f"/v1/journeys/{ids['trip-overnight']}", params={"date": DAYTIME_DATE}
    ).json()
    journey = body["journey"]
    assert journey["serviceDate"] == DAYTIME_DATE
    assert journey["crossesMidnight"] is True
    assert journey["stops"][-1]["arrivalAt"] == "2026-10-03T01:20:00+03:00"


def test_daytime_journey_does_not_cross_midnight(client, ids):
    body = search(client, ids, DAYTIME_DATE)
    daytime = next(
        result for result in body["results"] if trip_of(result["id"]) == ids["trip-daytime"]
    )
    assert daytime["crossesMidnight"] is False


def test_spring_forward_date_keeps_the_local_wall_clock(client, ids):
    body = search(client, ids, SPRING_FORWARD_DATE)
    assert len(body["results"]) == 1
    journey = body["results"][0]
    assert trip_of(journey["id"]) == ids["trip-sunday-dst"]
    assert journey["serviceDate"] == SPRING_FORWARD_DATE
    # Greece moves to EEST at 03:00 local on 29 March 2026, so 09:00 local is +03:00.
    assert journey["departure"]["at"] == f"{SPRING_FORWARD_DATE}T09:00:00+03:00"
    assert journey["arrival"]["at"] == f"{SPRING_FORWARD_DATE}T12:10:00+03:00"
    assert journey["durationMinutes"] == 190


def test_autumn_back_date_keeps_the_local_wall_clock(client, ids):
    body = search(client, ids, AUTUMN_BACK_DATE)
    assert len(body["results"]) == 1
    journey = body["results"][0]
    assert trip_of(journey["id"]) == ids["trip-sunday-dst"]
    assert journey["serviceDate"] == AUTUMN_BACK_DATE
    # Greece returns to EET at 04:00 local on 25 October 2026: the same journey
    # still departs at 09:00 local, now at +02:00.
    assert journey["departure"]["at"] == f"{AUTUMN_BACK_DATE}T09:00:00+02:00"
    assert journey["arrival"]["at"] == f"{AUTUMN_BACK_DATE}T12:10:00+02:00"
    assert journey["durationMinutes"] == 190


def test_both_transition_dates_report_the_same_local_departure_time(client, ids):
    spring = search(client, ids, SPRING_FORWARD_DATE)["results"][0]
    autumn = search(client, ids, AUTUMN_BACK_DATE)["results"][0]
    assert spring["departure"]["at"][11:19] == autumn["departure"]["at"][11:19] == "09:00:00"
    assert spring["departure"]["at"][-6:] == "+03:00"
    assert autumn["departure"]["at"][-6:] == "+02:00"


def test_dst_journey_stop_times_are_all_projected(client, ids):
    journey = client.get(
        f"/v1/journeys/{ids['trip-sunday-dst']}", params={"date": AUTUMN_BACK_DATE}
    ).json()["journey"]
    assert [stop["departureAt"] for stop in journey["stops"][:1]] == [
        f"{AUTUMN_BACK_DATE}T09:00:00+02:00"
    ]
    assert journey["stops"][1]["arrivalAt"] == f"{AUTUMN_BACK_DATE}T10:05:00+02:00"
    assert journey["stops"][-1]["arrivalAt"] == f"{AUTUMN_BACK_DATE}T12:10:00+02:00"


def test_calendar_exception_removal_hides_the_journey(client, ids):
    body = search(client, ids, CALENDAR_REMOVED_DATE)
    assert body["results"] == []
    assert body["unavailableReason"] == "no_service_on_date"


def test_the_same_journey_runs_on_an_ordinary_calendar_weekday(client, ids):
    body = search(client, ids, CALENDAR_NORMAL_DATE)
    assert [trip_of(result["id"]) for result in body["results"]] == [
        ids["trip-weekday-afternoon"]
    ]
    assert body["results"][0]["departure"]["at"] == (
        f"{CALENDAR_NORMAL_DATE}T14:30:00+03:00"
    )


def test_calendar_exception_addition_shows_the_journey(client, ids):
    # 11 April 2026 is a Saturday, which the weekday calendar excludes. The added
    # exception is what puts the journey back on that date.
    body = search(client, ids, CALENDAR_ADDED_DATE)
    assert [trip_of(result["id"]) for result in body["results"]] == [
        ids["trip-weekday-afternoon"]
    ]
    assert body["results"][0]["serviceDate"] == CALENDAR_ADDED_DATE
    assert body["results"][0]["departure"]["at"] == (
        f"{CALENDAR_ADDED_DATE}T14:30:00+03:00"
    )


def test_journey_detail_refuses_a_date_the_journey_does_not_run(client, ids):
    response = client.get(
        f"/v1/journeys/{ids['trip-weekday-afternoon']}",
        params={"date": CALENDAR_REMOVED_DATE},
    )
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "not_found"
    assert response.json()["error"]["field"] == "date"


def test_accessible_filter_keeps_only_reviewed_step_free_boarding(client, ids):
    everything = search(client, ids, DAYTIME_DATE)
    accessible = search(client, ids, DAYTIME_DATE, accessible="true")
    assert len(everything["results"]) == 3
    # The overnight service boards at bay A2, which has no reviewed step-free
    # boarding point, so it drops out.
    assert ids["trip-overnight"] in {trip_of(r["id"]) for r in everything["results"]}
    assert ids["trip-overnight"] not in {trip_of(r["id"]) for r in accessible["results"]}
    assert all(
        result["departure"]["stopId"] == ids[BAY_A1_EXTERNAL_ID]
        for result in accessible["results"]
    )


def test_purchase_actions_cover_online_and_ticket_office_only(client, ids):
    body = search(client, ids, DAYTIME_DATE)
    by_id = {trip_of(result["id"]): result for result in body["results"]}
    assert by_id[ids["trip-daytime"]]["purchase"]["kind"] == "online"
    assert by_id[ids["trip-daytime"]]["purchase"]["url"]

    overnight = by_id[ids["trip-overnight"]]["purchase"]
    assert overnight["kind"] == "ticket_office"
    assert overnight["url"] is None

    detail = client.get(
        f"/v1/journeys/{ids['trip-overnight']}", params={"date": DAYTIME_DATE}
    ).json()["journey"]
    assert detail["purchase"]["kind"] == "ticket_office"
    assert detail["purchase"]["url"] is None
    assert detail["purchase"]["phone"]
    assert detail["purchase"]["openingHours"]
    assert detail["purchase"]["disclaimer"]["en"]
    # The overnight service carries a restriction the client must show.
    assert [item["code"] for item in detail["restrictions"]] == [
        "overnight_unaccompanied_minors"
    ]


def test_intermediate_stop_forbids_boarding(client, ids):
    journey = client.get(
        f"/v1/journeys/{ids['trip-daytime']}", params={"date": DAYTIME_DATE}
    ).json()["journey"]
    mistona = next(
        stop
        for stop in journey["stops"]
        if stop["stopId"] == ids["stop-mistona-village"]
    )
    assert mistona["pickup"] == "not_allowed"
    assert mistona["dropoff"] == "allowed"
    # A journey cannot be searched from a stop it does not pick up at.
    body = client.get(
        "/v1/journeys",
        params={
            "origin": ids["stop-mistona-village"],
            "destination": ids[ORAVO_EXTERNAL_ID],
            "date": DAYTIME_DATE,
        },
    ).json()
    # The stop is still on the pattern, so the leg exists; the boarding rule is
    # published alongside it rather than silently removing the row.
    for result in body["results"]:
        detail = client.get(
            f"/v1/journeys/{result['id']}", params={"date": DAYTIME_DATE}
        ).json()["journey"]
        rule = next(
            stop["pickup"]
            for stop in detail["stops"]
            if stop["stopId"] == ids["stop-mistona-village"]
        )
        assert rule == "not_allowed"


def test_journey_detail_without_a_date_uses_the_journeys_own_service_date(client, ids):
    body = client.get(f"/v1/journeys/{ids['trip-sunday-dst']}").json()
    assert body["journey"]["serviceDate"] == SPRING_FORWARD_DATE
