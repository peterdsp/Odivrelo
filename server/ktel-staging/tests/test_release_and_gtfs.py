"""Release integrity and GTFS service-date semantics.

These are the contract tests the clients depend on. Timezone and service-date
behaviour is resolved once here so no UI reimplements a timetable engine.
"""
import io
import json
import sqlite3
import tempfile
import unittest
import zipfile
from pathlib import Path

from hodomap_ktel import branding, ktel_api, ktel_db, ktel_gtfs, ktel_release
from hodomap_ktel.ktel_ingest import import_normalized_snapshot, review_entity
from hodomap_ktel.ktel_registry import seed_registry


def _snapshot(*, operator_id="ktel-kavala", source_id="manual-review", trips):
    return {
        "operatorId": operator_id,
        "sourceId": source_id,
        "retrievedAt": "2026-07-26T08:00:00Z",
        "stopPlaces": [{"externalId": "place-a", "name": "Alpha Terminal"}],
        "stops": [
            {
                "externalId": "stop-a",
                "stopPlaceExternalId": "place-a",
                "name": "Alpha Terminal, bay 1",
                "latitude": 40.936,
                "longitude": 24.412,
                "webActive": True,
            },
            {
                "externalId": "stop-b",
                "name": "Beta Station",
                "latitude": 40.653,
                "longitude": 22.901,
                "webActive": True,
            },
        ],
        "lines": [{"externalId": "line-ab", "name": "Alpha to Beta"}],
        "journeyPatterns": [
            {
                "externalId": "pattern-ab",
                "lineExternalId": "line-ab",
                "name": "Direct",
                "stops": [
                    {"stopExternalId": "stop-a", "sequence": 1,
                     "pickupType": "allowed", "dropoffType": "not_allowed"},
                    {"stopExternalId": "stop-b", "sequence": 2,
                     "pickupType": "not_allowed", "dropoffType": "allowed"},
                ],
            }
        ],
        "trips": trips,
    }


OVERNIGHT_TRIP = {
    "externalId": "overnight",
    "lineExternalId": "line-ab",
    "patternExternalId": "pattern-ab",
    "serviceDate": "2026-07-26",
    # Departs 23:40 on the service date, arrives 01:10 the next calendar day.
    "departureAt": "2026-07-26T23:40:00+03:00",
    "approximateArrivalAt": "2026-07-27T01:10:00+03:00",
    "commercialState": "bookable",
    "stopTimes": [
        {"stopExternalId": "stop-a", "sequence": 1,
         "departureAt": "2026-07-26T23:40:00+03:00", "timeStatus": "scheduled"},
        {"stopExternalId": "stop-b", "sequence": 2,
         "arrivalAt": "2026-07-27T01:10:00+03:00", "timeStatus": "approximate"},
    ],
}


class GtfsTimeSemanticsTest(unittest.TestCase):
    def test_times_past_midnight_exceed_24_hours(self):
        self.assertEqual(
            ktel_gtfs.gtfs_time("2026-07-27T01:10:00+03:00", "2026-07-26"),
            "25:10:00",
        )

    def test_plain_daytime_departure(self):
        self.assertEqual(
            ktel_gtfs.gtfs_time("2026-07-26T09:05:00+03:00", "2026-07-26"),
            "09:05:00",
        )

    def test_spring_forward_keeps_wall_clock_offsets(self):
        # Greece moves to EEST at 03:00 local on 29 March 2026. A 05:00 local
        # departure is 05:00:00 in GTFS even though only four hours of real
        # time elapsed since midnight.
        self.assertEqual(
            ktel_gtfs.gtfs_time("2026-03-29T05:00:00+03:00", "2026-03-29"),
            "05:00:00",
        )

    def test_autumn_back_keeps_wall_clock_offsets(self):
        # Greece returns to EET at 04:00 local on 25 October 2026.
        self.assertEqual(
            ktel_gtfs.gtfs_time("2026-10-25T05:00:00+02:00", "2026-10-25"),
            "05:00:00",
        )

    def test_utc_instant_is_converted_to_athens(self):
        self.assertEqual(
            ktel_gtfs.gtfs_time("2026-07-26T06:05:00Z", "2026-07-26"),
            "09:05:00",
        )

    def test_time_before_the_service_date_is_rejected(self):
        with self.assertRaises(ktel_gtfs.GtfsExportError):
            ktel_gtfs.gtfs_time("2026-07-25T23:00:00+03:00", "2026-07-26")


class ReleaseTestCase(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        root = Path(self.temp_dir.name)
        self.db_path = str(root / "ingest.db")
        self.public_db = str(root / "public.db")
        self.out_dir = root / "out"
        self.conn = ktel_db.connect(self.db_path)
        ktel_db.migrate(self.conn)
        seed_registry(self.conn)
        import_normalized_snapshot(self.conn, _snapshot(trips=[OVERNIGHT_TRIP]))
        self._publish_everything()
        self.conn.commit()

    def _publish_everything(self):
        for kind, table in (
            ("stop_place", "ktel_stop_places"),
            ("stop", "ktel_stops"),
            ("line", "ktel_lines"),
            ("journey_pattern", "ktel_journey_patterns"),
            ("trip", "ktel_trips"),
        ):
            for row in self.conn.execute(
                f"SELECT id FROM {table} WHERE publication_state='candidate'"
            ).fetchall():
                review_entity(
                    self.conn,
                    entity_kind=kind,
                    entity_id=row["id"],
                    action="publish",
                    reviewer="test",
                )

    def tearDown(self):
        self.conn.close()
        self.temp_dir.cleanup()

    def _generate(self):
        return ktel_release.generate_public_release(
            self.out_dir, self.db_path, self.public_db
        )

    def _root(self):
        return self.out_dir / branding.PRODUCT_SLUG

    def test_gtfs_pack_round_trips_and_carries_overnight_time(self):
        result = self._generate()
        manifest = json.loads(
            (self._root() / "manifest.json").read_text(encoding="utf-8")
        )
        gtfs_path = self._root() / manifest["files"]["gtfs"]["path"]
        with zipfile.ZipFile(io.BytesIO(gtfs_path.read_bytes())) as archive:
            names = archive.namelist()
            self.assertIn("feed_info.txt", names)
            self.assertIn("stop_times.txt", names)
            stop_times = archive.read("stop_times.txt").decode("utf-8")
            feed_info = archive.read("feed_info.txt").decode("utf-8")
            calendar_dates = archive.read("calendar_dates.txt").decode("utf-8")
        self.assertIn("25:10:00", stop_times)
        self.assertIn("23:40:00", stop_times)
        self.assertIn(result["releaseId"], feed_info)
        self.assertIn(branding.PRODUCT_NAME, feed_info)
        self.assertIn("20260726,1", calendar_dates)

    def test_pattern_boarding_rules_reach_the_feed(self):
        self._generate()
        manifest = json.loads(
            (self._root() / "manifest.json").read_text(encoding="utf-8")
        )
        gtfs_path = self._root() / manifest["files"]["gtfs"]["path"]
        with zipfile.ZipFile(io.BytesIO(gtfs_path.read_bytes())) as archive:
            rows = archive.read("stop_times.txt").decode("utf-8").splitlines()
        header = rows[0].split(",")
        pickup = header.index("pickup_type")
        dropoff = header.index("drop_off_type")
        first = rows[1].split(",")
        second = rows[2].split(",")
        self.assertEqual(first[pickup], "0")
        self.assertEqual(first[dropoff], "1")
        self.assertEqual(second[pickup], "1")
        self.assertEqual(second[dropoff], "0")

    def test_generating_twice_is_byte_identical(self):
        first = self._generate()
        first_manifest = (self._root() / "manifest.json").read_bytes()
        second = self._generate()
        self.assertEqual(first["releaseId"], second["releaseId"])
        self.assertEqual(
            first_manifest, (self._root() / "manifest.json").read_bytes()
        )

    def test_verify_detects_a_corrupt_pack(self):
        self._generate()
        manifest = json.loads(
            (self._root() / "manifest.json").read_text(encoding="utf-8")
        )
        victim = self._root() / manifest["files"]["registry"]["path"]
        victim.write_bytes(b'{"tampered":true}')
        with self.assertRaises(ktel_release.ReleaseConsistencyError):
            ktel_release.verify_release(self._root())

    def test_verify_detects_a_missing_pack(self):
        self._generate()
        manifest = json.loads(
            (self._root() / "manifest.json").read_text(encoding="utf-8")
        )
        (self._root() / manifest["files"]["stops"]["path"]).unlink()
        with self.assertRaises(ktel_release.ReleaseConsistencyError):
            ktel_release.verify_release(self._root())

    def test_rollback_restores_the_previous_manifest(self):
        first = self._generate()
        import_normalized_snapshot(
            self.conn,
            _snapshot(
                trips=[
                    OVERNIGHT_TRIP,
                    {
                        "externalId": "morning",
                        "lineExternalId": "line-ab",
                        "patternExternalId": "pattern-ab",
                        "serviceDate": "2026-07-27",
                        "departureAt": "2026-07-27T07:15:00+03:00",
                        "approximateArrivalAt": "2026-07-27T09:00:00+03:00",
                        "commercialState": "bookable",
                        "stopTimes": [
                            {"stopExternalId": "stop-a", "sequence": 1,
                             "departureAt": "2026-07-27T07:15:00+03:00",
                             "timeStatus": "scheduled"},
                            {"stopExternalId": "stop-b", "sequence": 2,
                             "arrivalAt": "2026-07-27T09:00:00+03:00",
                             "timeStatus": "approximate"},
                        ],
                    },
                ]
            ),
        )
        self._publish_everything()
        self.conn.commit()
        second = self._generate()
        self.assertNotEqual(first["releaseId"], second["releaseId"])
        self.assertTrue((self._root() / "manifest-previous.json").exists())

        restored = ktel_release.rollback_release(self._root())
        self.assertEqual(restored["releaseId"], first["releaseId"])
        # Packs are immutable, so the rolled-back manifest still verifies.
        ktel_release.verify_release(self._root())

    def test_release_without_a_published_database_is_refused(self):
        empty_public = str(Path(self.temp_dir.name) / "empty-public.db")
        with ktel_db.connect(empty_public) as public:
            ktel_db.migrate(public)
        with self.assertRaises(ktel_release.ReleaseConsistencyError):
            ktel_release.generate_public_release(
                self.out_dir,
                self.db_path,
                empty_public,
                compile_first=False,
            )

    def test_public_payloads_exclude_unreviewed_rows(self):
        import_normalized_snapshot(
            self.conn,
            {
                "operatorId": "ktel-kavala",
                "sourceId": "manual-review",
                "retrievedAt": "2026-07-26T08:00:00Z",
                "stops": [
                    {
                        "externalId": "stop-secret",
                        "name": "Unreviewed candidate stop",
                        "latitude": 39.0,
                        "longitude": 22.0,
                    }
                ],
            },
        )
        self.conn.commit()
        self._generate()
        stops = self._pack("stops")
        names = {row["name"] for row in stops["stops"]}
        self.assertNotIn("Unreviewed candidate stop", names)
        self.assertIn("Alpha Terminal, bay 1", names)

    def test_rights_pending_source_never_reaches_a_public_release(self):
        # The same reviewer approves a row sourced from a permission-pending
        # provider. Review is not enough: the rights gate must still exclude it.
        import_normalized_snapshot(
            self.conn,
            {
                "operatorId": "ktel-kavala",
                "sourceId": "ticketweb",
                "retrievedAt": "2026-07-26T08:00:00Z",
                "stops": [
                    {
                        "externalId": "stop-rights-pending",
                        "name": "Rights pending stop",
                        "latitude": 39.5,
                        "longitude": 22.5,
                    }
                ],
            },
        )
        self._publish_everything()
        self.conn.commit()
        approved = self.conn.execute(
            "SELECT publication_state FROM ktel_stops "
            "WHERE external_id='stop-rights-pending'"
        ).fetchone()["publication_state"]
        self.assertEqual(approved, "published")

        self._generate()
        names = {row["name"] for row in self._pack("stops")["stops"]}
        self.assertNotIn("Rights pending stop", names)

    def _pack(self, name):
        manifest = json.loads(
            (self._root() / "manifest.json").read_text(encoding="utf-8")
        )
        return json.loads(
            (self._root() / manifest["files"][name]["path"]).read_text(
                encoding="utf-8"
            )
        )


if __name__ == "__main__":
    unittest.main()
