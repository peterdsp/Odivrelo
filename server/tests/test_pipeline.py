import os
import io
import json
import tempfile
import unittest
import urllib.error
from email.message import Message
from pathlib import Path

from odivrelo_pipeline.acquisition import (
    AcquisitionSettings,
    SourceAcquirer,
    discover_candidate_links,
)
from odivrelo_pipeline import acquisition, cli
from odivrelo_pipeline.registry import Registry, SourceTarget
from odivrelo_pipeline.state import StateStore


class FakeResponse:
    def __init__(self, body, content_type="text/html", status=200):
        self.body = io.BytesIO(body)
        self.status = status
        self.headers = Message()
        self.headers["Content-Type"] = content_type
        self.headers["Content-Length"] = str(len(body))
        self.headers["ETag"] = '"fixture-v1"'

    def read(self, size=-1):
        return self.body.read(size)

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return None


class FakeOpener:
    def __init__(self, response):
        self.response = response
        self.requests = []

    def open(self, request, timeout):
        self.requests.append((request, timeout))
        if isinstance(self.response, Exception):
            raise self.response
        return self.response


class PipelineTestCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.store = StateStore(self.root / "state.db")

    def tearDown(self):
        self.store.close()
        self.temporary.cleanup()

    def target(self, **overrides):
        values = {
            "source_id": "fixture-source",
            "operator_id": "ktel-fixture",
            "label": "Fixture",
            "url": "https://example.test/timetable",
            "source_kind": "operator_directory",
            "cadence": "daily",
            "enabled": True,
            "rights_status": "unknown",
            "retention_mode": "metadata_only",
            "max_bytes": 4096,
            "expected_content_types": ("text/html",),
        }
        values.update(overrides)
        return SourceTarget(**values)

    def acquirer(self, response):
        return SourceAcquirer(
            store=self.store,
            artifact_root=self.root / "artifacts",
            settings=AcquisitionSettings(
                max_requests=3,
                minimum_host_interval_seconds=0,
                jitter_seconds=0,
            ),
            opener=FakeOpener(response),
        )

    def test_metadata_only_source_is_hashed_but_not_retained(self):
        run_id = "run-metadata"
        self.store.start_run(run_id, 1)
        result = self.acquirer(FakeResponse(b"<html>fixture</html>")).acquire(
            run_id,
            self.target(),
        )
        self.assertEqual(result["outcome"], "changed")
        self.assertIsNone(result["artifactPath"])
        self.assertFalse((self.root / "artifacts").exists())
        self.assertEqual(self.store.status()["pendingReviews"], 1)

    def test_permitted_persistent_source_is_content_addressed(self):
        run_id = "run-permitted"
        self.store.start_run(run_id, 1)
        result = self.acquirer(
            FakeResponse(b'{"routes":[]}', "application/json")
        ).acquire(
            run_id,
            self.target(
                rights_status="permitted",
                retention_mode="raw_persistent",
                expected_content_types=("application/json",),
            ),
        )
        self.assertEqual(result["outcome"], "changed")
        self.assertTrue(Path(result["artifactPath"]).exists())

    def test_ticketing_source_is_disabled_without_terms_gate(self):
        run_id = "run-ticketing"
        self.store.start_run(run_id, 1)
        result = self.acquirer(FakeResponse(b"{}")).acquire(
            run_id,
            self.target(source_kind="ticketing"),
        )
        self.assertEqual(result["outcome"], "skipped")
        self.assertIn("terms approval", result["reason"])

    def test_oversized_response_is_failed(self):
        run_id = "run-large"
        self.store.start_run(run_id, 1)
        result = self.acquirer(FakeResponse(b"x" * 5000)).acquire(
            run_id,
            self.target(max_bytes=1024),
        )
        self.assertEqual(result["outcome"], "failed")
        self.assertIn("maxBytes", result["error"])

    def test_registry_requires_all_62_federation_numbers(self):
        registry_path = self.root / "registry.json"
        registry_path.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "verifiedAt": "2026-07-26",
                    "operators": [],
                    "sources": [],
                }
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "1 through 62"):
            Registry.load(registry_path)

    def test_repository_registry_has_exact_national_set(self):
        registry_path = (
            Path(__file__).resolve().parents[2]
            / "data"
            / "operators"
            / "registry.json"
        )
        registry = Registry.load(registry_path)
        self.assertEqual(len(registry.operators), 62)
        self.assertEqual(
            [item.federation_number for item in registry.operators],
            list(range(1, 63)),
        )
        self.assertEqual(len(registry.enabled_sources()), 64)
        self.assertFalse(
            any(
                source.source_kind == "ticketing"
                for source in registry.enabled_sources()
            )
        )

    def test_not_modified_response_preserves_previous_digest(self):
        first_run = "run-first"
        self.store.start_run(first_run, 1)
        self.acquirer(FakeResponse(b"<html>fixture</html>")).acquire(
            first_run,
            self.target(),
        )
        before = self.store.source_state("fixture-source")

        headers = Message()
        headers["ETag"] = '"fixture-v1"'
        not_modified = urllib.error.HTTPError(
            "https://example.test/timetable",
            304,
            "Not Modified",
            headers,
            None,
        )
        second_run = "run-second"
        self.store.start_run(second_run, 1)
        result = self.acquirer(not_modified).acquire(
            second_run,
            self.target(),
        )
        after = self.store.source_state("fixture-source")
        self.assertEqual(result["outcome"], "not_modified")
        self.assertEqual(after["content_sha256"], before["content_sha256"])

    def test_directory_discovers_external_candidate_links(self):
        body = b"""
        <html><body>
          <a href="https://operator.example/timetables">Timetables</a>
          <a href="/internal">Internal</a>
          <a href="https://www.facebook.com/operator">Social</a>
          <a href="https://ibooked.gr/weather/test">Weather</a>
        </body></html>
        """
        links = discover_candidate_links(
            "https://ktelbus.com/ktel-fixture/",
            body,
        )
        self.assertEqual(
            links,
            ["https://operator.example/timetables"],
        )


if __name__ == "__main__":
    unittest.main()


class BrandMigrationCompatibilityTestCase(unittest.TestCase):
    """The rename must not silently orphan an existing Pi deployment."""

    def setUp(self):
        self._saved = {
            name: os.environ.pop(name, None)
            for name in (
                "ODIVRELO_MAX_REQUESTS",
                "PORAVIA_MAX_REQUESTS",
                "HODOMAP_MAX_REQUESTS",
                "SYRMOS_MAX_REQUESTS",
                "ODIVRELO_TICKETWEB_TERMS_APPROVED",
                "PORAVIA_TICKETWEB_TERMS_APPROVED",
                "HODOMAP_TICKETWEB_TERMS_APPROVED",
                "SYRMOS_TICKETWEB_TERMS_APPROVED",
            )
        }

    def tearDown(self):
        for name, value in self._saved.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value

    def test_legacy_env_names_are_still_read(self):
        self.assertEqual(cli.env("MAX_REQUESTS", "100"), "100")
        os.environ["SYRMOS_MAX_REQUESTS"] = "11"
        self.assertEqual(cli.env("MAX_REQUESTS", "100"), "11")
        os.environ["HODOMAP_MAX_REQUESTS"] = "22"
        self.assertEqual(cli.env("MAX_REQUESTS", "100"), "22")
        os.environ["PORAVIA_MAX_REQUESTS"] = "44"
        self.assertEqual(cli.env("MAX_REQUESTS", "100"), "44")

    def test_current_env_name_wins_over_legacy(self):
        os.environ["HODOMAP_MAX_REQUESTS"] = "22"
        os.environ["ODIVRELO_MAX_REQUESTS"] = "33"
        self.assertEqual(cli.env("MAX_REQUESTS", "100"), "33")

    def test_ticketweb_gate_is_off_unless_explicitly_approved(self):
        self.assertFalse(acquisition._ticketweb_terms_approved())
        for name in (
            "SYRMOS_TICKETWEB_TERMS_APPROVED",
            "HODOMAP_TICKETWEB_TERMS_APPROVED",
            "PORAVIA_TICKETWEB_TERMS_APPROVED",
            "ODIVRELO_TICKETWEB_TERMS_APPROVED",
        ):
            os.environ[name] = "0"
            self.assertFalse(acquisition._ticketweb_terms_approved())
            os.environ[name] = "1"
            self.assertTrue(acquisition._ticketweb_terms_approved())
            del os.environ[name]
