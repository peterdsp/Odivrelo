import json
import hashlib
import sqlite3
import tempfile
import unittest
from datetime import date
from pathlib import Path

from poravia_ktel import branding, ktel_api, ktel_db, ktel_gtfs, ktel_release
from poravia_ktel.ktel_ingest import import_normalized_snapshot, review_entity
from poravia_ktel.ktel_publish import compile_public_database
from poravia_ktel.ktel_registry import (
    coordinate_status,
    coverage_summary,
    load_registry,
    operator_rows,
    seed_registry,
)
from poravia_ktel.ktel_ticketweb import (
    JsonDiskCache,
    RequestBudget,
    TicketWebReadClient,
    bounded_execution_plan,
)


class KtelTestCase(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.db_path = str(Path(self.temp_dir.name) / "ktel.db")
        self.conn = ktel_db.connect(self.db_path)
        ktel_db.migrate(self.conn)
        seed_registry(self.conn)

    def tearDown(self):
        self.conn.close()
        self.temp_dir.cleanup()

    def test_registry_has_exact_official_set_and_verified_ticketweb_subset(self):
        payload = load_registry()
        official = [
            item for item in payload["operators"]
            if item["federationNumber"] is not None
        ]
        self.assertEqual(len(official), 62)
        self.assertEqual(
            [item["federationNumber"] for item in official],
            list(range(1, 63)),
        )

        summary = coverage_summary(self.conn)
        self.assertEqual(summary["officialOperatorCount"], 62)
        self.assertEqual(summary["ticketwebVerifiedOfficialOperators"], 26)
        self.assertEqual(summary["ticketwebUnverifiedOfficialOperators"], 36)
        self.assertEqual(summary["ticketwebAdditionalTenants"], 2)
        self.assertEqual(
            summary["ticketwebInventoryRecords"],
            {
                "stopGroups": 5378,
                "stops": 19872,
                "webActiveStops": 511,
                "normalizedOrDeduplicated": False,
            },
        )

    def test_migration_and_seed_are_idempotent(self):
        # The expected version is the highest numbered file in
        # ktel_migrations/, so a new migration has to be added deliberately
        # here as well as on disk.
        latest = max(
            int(path.stem.split("_", 1)[0])
            for path in ktel_db.KTEL_MIGRATIONS_DIR.glob("[0-9]*.sql")
        )
        self.assertEqual(latest, 2)
        self.assertEqual(ktel_db.migrate(self.conn), 2)
        seed_registry(self.conn)
        rows = operator_rows(self.conn, official_only=False)
        self.assertEqual(len(rows), 64)

    def test_coordinate_quarantine(self):
        self.assertEqual(coordinate_status(None, None), "missing")
        self.assertEqual(coordinate_status(0, 0), "placeholder")
        self.assertEqual(coordinate_status(900, 23), "out_of_range")
        self.assertEqual(coordinate_status(51.5, -0.1), "outside_greece")
        self.assertEqual(coordinate_status(40.936, 24.412), "valid")

    def test_bounded_plan_uses_only_reachable_edges_and_hard_cap(self):
        plan = list(bounded_execution_plan(
            tenant="kav",
            reachable_by_origin={
                "kavala": ["thessaloniki", "drama", "kavala"],
                "internal": ["unused"],
            },
            start_date=date(2026, 7, 26),
            days=2,
            max_origins=2,
            max_destinations_per_origin=2,
            max_queries=3,
        ))
        self.assertEqual(len(plan), 3)
        allowed_edges = {
            ("kavala", "thessaloniki"),
            ("kavala", "drama"),
            ("internal", "unused"),
        }
        self.assertTrue(
            {
                (item.origin_external_id, item.destination_external_id)
                for item in plan
            }.issubset(allowed_edges)
        )
        self.assertNotIn(
            ("kavala", "unused"),
            {
                (item.origin_external_id, item.destination_external_id)
                for item in plan
            },
        )

    def test_candidate_data_is_hidden_until_explicit_review(self):
        snapshot = {
            "operatorId": "ktel-kavala",
            "sourceId": "ticketweb",
            "retrievedAt": "2026-07-26T08:00:00Z",
            "stopPlaces": [
                {
                    "externalId": "place-kav",
                    "name": "Kavala",
                }
            ],
            "stops": [
                {
                    "externalId": "stop-kav",
                    "stopPlaceExternalId": "place-kav",
                    "name": "Kavala Central Bus Station",
                    "latitude": 40.936,
                    "longitude": 24.412,
                    "webActive": True,
                },
                {
                    "externalId": "stop-bad",
                    "name": "Invalid point",
                    "latitude": 999999,
                    "longitude": 999999,
                    "publicationState": "published",
                },
                {
                    "externalId": "stop-thess",
                    "name": "Thessaloniki Bus Station",
                    "latitude": 40.653,
                    "longitude": 22.901,
                    "webActive": True,
                },
            ],
            "lines": [
                {
                    "externalId": "line-kav-thess",
                    "name": "Kavala to Thessaloniki",
                }
            ],
            "journeyPatterns": [
                {
                    "externalId": "pattern-express",
                    "lineExternalId": "line-kav-thess",
                    "name": "Express",
                    "stops": [
                        {"stopExternalId": "stop-kav", "sequence": 1},
                        {"stopExternalId": "stop-thess", "sequence": 2},
                    ],
                }
            ],
            "trips": [
                {
                    "externalId": "execution-0900",
                    "lineExternalId": "line-kav-thess",
                    "patternExternalId": "pattern-express",
                    "serviceDate": "2026-07-26",
                    "departureAt": "2026-07-26T09:00:00+03:00",
                    "approximateArrivalAt": "2026-07-26T11:00:00+03:00",
                    "commercialState": "bookable",
                    "fareAmount": 18.0,
                    "fareCurrency": "EUR",
                    "stopTimes": [
                        {
                            "stopExternalId": "stop-kav",
                            "sequence": 1,
                            "departureAt": "2026-07-26T09:00:00+03:00",
                            "timeStatus": "scheduled",
                        },
                        {
                            "stopExternalId": "stop-thess",
                            "sequence": 2,
                            "arrivalAt": "2026-07-26T11:00:00+03:00",
                            "timeStatus": "approximate",
                        },
                    ],
                }
            ],
        }
        imported = import_normalized_snapshot(self.conn, snapshot)
        self.assertEqual(imported["quarantined"], 1)
        self.assertEqual(ktel_api.stops_payload(self.conn)["total"], 0)
        self.assertEqual(
            ktel_api.routes_payload(self.conn)["total"], 0
        )
        self.assertEqual(
            len(ktel_api.trips_payload(
                self.conn, service_date="2026-07-26"
            )["trips"]),
            0,
        )

        ids = {
            row["external_id"]: row["id"]
            for row in self.conn.execute(
                "SELECT id, external_id FROM ktel_stops"
            )
        }
        line_id = self.conn.execute(
            "SELECT id FROM ktel_lines WHERE external_id='line-kav-thess'"
        ).fetchone()["id"]
        pattern_id = self.conn.execute(
            "SELECT id FROM ktel_journey_patterns "
            "WHERE external_id='pattern-express'"
        ).fetchone()["id"]
        trip_id = self.conn.execute(
            "SELECT id FROM ktel_trips WHERE external_id='execution-0900'"
        ).fetchone()["id"]
        for kind, entity_id in (
            ("stop", ids["stop-kav"]),
            ("stop", ids["stop-thess"]),
            ("line", line_id),
            ("journey_pattern", pattern_id),
            ("trip", trip_id),
        ):
            review_entity(
                self.conn,
                entity_kind=kind,
                entity_id=entity_id,
                action="publish",
                reviewer="test",
            )

        stops = ktel_api.stops_payload(
            self.conn, operator_id="ktel-kavala"
        )
        self.assertEqual(stops["total"], 2)
        route = ktel_api.route_detail_payload(self.conn, pattern_id)
        self.assertEqual(len(route["route"]["stops"]), 2)
        journeys = ktel_api.trips_payload(
            self.conn,
            service_date="2026-07-26",
            origin_stop_id=ids["stop-kav"],
            destination_stop_id=ids["stop-thess"],
        )
        self.assertEqual(len(journeys["trips"]), 1)
        self.assertEqual(journeys["trips"][0]["fareAmount"], 18.0)

        public_db_path = str(Path(self.temp_dir.name) / "ktel-public.db")
        rights_blocked_publication = compile_public_database(
            ingest_db_path=self.db_path,
            public_db_path=public_db_path,
        )
        with ktel_db.connect(public_db_path, read_only=True) as public:
            self.assertEqual(
                ktel_api.release_id(public),
                rights_blocked_publication["releaseId"],
            )
            self.assertEqual(
                ktel_api.stops_payload(public, operator_id="ktel-kavala")["total"],
                0,
            )
            raw_table_rows = public.execute(
                "SELECT COUNT(*) AS n FROM ktel_source_records"
            ).fetchone()["n"]
            self.assertEqual(raw_table_rows, 0)

        self.conn.execute(
            "UPDATE ktel_sources SET rights_status='allowed' "
            "WHERE id='ticketweb'"
        )
        publication = compile_public_database(
            ingest_db_path=self.db_path,
            public_db_path=public_db_path,
        )
        with ktel_db.connect(public_db_path, read_only=True) as public:
            self.assertEqual(
                ktel_api.release_id(public), publication["releaseId"]
            )
            self.assertEqual(
                ktel_api.stops_payload(public, operator_id="ktel-kavala")["total"],
                2,
            )

        with self.assertRaises(ValueError):
            review_entity(
                self.conn,
                entity_kind="stop",
                entity_id=ids["stop-bad"],
                action="publish",
                reviewer="test",
            )

    def test_client_rejects_mutating_endpoint_names(self):
        client = TicketWebReadClient(
            url_template="https://example.invalid/{tenant}",
            authorization="test-only",
            client_key=None,
            cache=JsonDiskCache(Path(self.temp_dir.name) / "cache", 60),
            budget=RequestBudget(max_requests=1, minimum_interval_seconds=0),
        )
        with self.assertRaises(ValueError):
            client.request("kav", "reservation", {})

    def test_snapshot_manifest_points_to_immutable_hashed_packs(self):
        out_dir = Path(self.temp_dir.name) / "out"
        public_db = str(Path(self.temp_dir.name) / "ktel-public.db")
        result = ktel_release.generate_public_release(
            out_dir, self.db_path, public_db
        )
        manifest = json.loads(
            (out_dir / branding.PRODUCT_SLUG / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(manifest["releaseId"], result["releaseId"])
        for metadata in manifest["files"].values():
            self.assertTrue(metadata["path"].startswith("packs/"))
            path = out_dir / branding.PRODUCT_SLUG / metadata["path"]
            self.assertTrue(path.exists())
            self.assertEqual(
                hashlib.sha256(path.read_bytes()).hexdigest(),
                metadata["sha256"],
            )
        second = ktel_release.generate_public_release(
            out_dir, self.db_path, public_db
        )
        second_manifest = json.loads(
            (out_dir / branding.PRODUCT_SLUG / "manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(second["releaseId"], result["releaseId"])
        self.assertEqual(second_manifest, manifest)


if __name__ == "__main__":
    unittest.main()
