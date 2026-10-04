"""The release guard and production path must fail closed on demo data.

These tests prove the property Section 4 of the delivery plan requires: a
production release can never silently become the Aloria demonstration dataset,
whether through a missing dataset, a failed or empty import, or a demo database
that has merely been stamped ``real``.

The real dataset used here is a minimal, genuinely shaped KTEL Evia sample
(registered operator ``ktel-evia``, source ``greek-nap-ktel``). It exists only to
exercise the real code path. It is not a product dataset and never reaches a
shipped release.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))

from publicapi import demo_seed, packs, release_guard, release_production  # noqa: E402
from publicapi.brand import BRAND  # noqa: E402
from publicapi.release_guard import ReleaseGuardError  # noqa: E402
from odivrelo_ktel import ktel_db, ktel_release  # noqa: E402
from odivrelo_ktel.ktel_ingest import import_normalized_snapshot  # noqa: E402
from odivrelo_ktel.ktel_registry import seed_registry  # noqa: E402


REAL_EVIA_SNAPSHOT = {
    "dataset": "real",
    "operatorId": "ktel-evia",
    "sourceId": "greek-nap-ktel",
    "retrievedAt": "2026-10-03T06:00:00Z",
    "importRunId": "test-real-evia",
    "stopPlaces": [
        {
            "externalId": "evia-chalkida-terminal",
            "name": "Chalkida KTEL Terminal",
            "nameEl": "Σταθμός ΚΤΕΛ Χαλκίδας",
            "defaultStopExternalId": "evia-chalkida-stop",
            "webActive": True,
        }
    ],
    "stops": [
        {
            "externalId": "evia-chalkida-stop",
            "stopPlaceExternalId": "evia-chalkida-terminal",
            "name": "Chalkida KTEL Terminal",
            "nameEl": "Σταθμός ΚΤΕΛ Χαλκίδας",
            "latitude": 38.4636,
            "longitude": 23.5939,
            "webActive": True,
        },
        {
            "externalId": "evia-eretria-stop",
            "name": "Eretria",
            "nameEl": "Ερέτρια",
            "latitude": 38.3967,
            "longitude": 23.7936,
            "webActive": True,
        },
        {
            "externalId": "evia-kymi-stop",
            "name": "Kymi",
            "nameEl": "Κύμη",
            "latitude": 38.6333,
            "longitude": 24.0986,
            "webActive": True,
        },
    ],
    "lines": [
        {
            "externalId": "evia-chalkida-kymi",
            "publicCode": "1",
            "name": "Chalkida to Kymi",
            "nameEl": "Χαλκίδα προς Κύμη",
            "dataStatus": "verified",
        }
    ],
    "journeyPatterns": [
        {
            "externalId": "evia-chalkida-kymi-outbound",
            "lineExternalId": "evia-chalkida-kymi",
            "name": "Chalkida to Kymi, all stops",
            "direction": "outbound",
            "geometryStatus": "ordered_stops_only",
            "stops": [
                {"stopExternalId": "evia-chalkida-stop", "sequence": 1,
                 "pickupType": "allowed", "dropoffType": "not_allowed"},
                {"stopExternalId": "evia-eretria-stop", "sequence": 2,
                 "pickupType": "allowed", "dropoffType": "allowed"},
                {"stopExternalId": "evia-kymi-stop", "sequence": 3,
                 "pickupType": "not_allowed", "dropoffType": "allowed"},
            ],
        }
    ],
    "serviceCalendars": [
        {
            "externalId": "evia-daily",
            "name": "Chalkida to Kymi, daily",
            "validFrom": "2026-01-01",
            "validUntil": "2026-12-31",
            "monday": True, "tuesday": True, "wednesday": True, "thursday": True,
            "friday": True, "saturday": True, "sunday": True,
            "verificationState": "operator_verified",
            "exceptions": [],
        }
    ],
    "trips": [
        {
            "externalId": "evia-chalkida-kymi-0800",
            "lineExternalId": "evia-chalkida-kymi",
            "patternExternalId": "evia-chalkida-kymi-outbound",
            "calendarExternalId": "evia-daily",
            "serviceDate": "2026-10-05",
            "departureAt": "2026-10-05T08:00:00+03:00",
            "approximateArrivalAt": "2026-10-05T10:00:00+03:00",
            "commercialState": "bookable",
            "scheduleScope": "operator_published",
            "observedAt": "2026-10-03T06:00:00Z",
            "publicAttributes": {"positionQuality": "scheduled"},
            "stopTimes": [
                {"stopExternalId": "evia-chalkida-stop", "sequence": 1,
                 "departureAt": "2026-10-05T08:00:00+03:00", "timeStatus": "scheduled"},
                {"stopExternalId": "evia-eretria-stop", "sequence": 2,
                 "arrivalAt": "2026-10-05T08:30:00+03:00",
                 "departureAt": "2026-10-05T08:32:00+03:00", "timeStatus": "scheduled"},
                {"stopExternalId": "evia-kymi-stop", "sequence": 3,
                 "arrivalAt": "2026-10-05T10:00:00+03:00", "timeStatus": "approximate"},
            ],
        }
    ],
}


def _build_real_ingest(path: Path) -> None:
    connection = ktel_db.connect(str(path))
    try:
        ktel_db.migrate(connection)
        seed_registry(connection)
        import_normalized_snapshot(connection, REAL_EVIA_SNAPSHOT)
        demo_seed.publish_reviewed(connection, reviewer="pytest")
        connection.commit()
    finally:
        connection.close()


@pytest.fixture
def demo_release(tmp_path) -> Path:
    out_dir = tmp_path / "demo"
    demo_seed.seed(
        ingest_db_path=tmp_path / "demo-ingest.db",
        public_db_path=tmp_path / "demo-public.db",
        release_out_dir=out_dir,
        reviewer="pytest",
    )
    return out_dir / BRAND.slug


@pytest.fixture
def real_release(tmp_path) -> Path:
    ingest = tmp_path / "real-ingest.db"
    _build_real_ingest(ingest)
    result = release_production.cut_release(
        ingest_db_path=ingest,
        public_db_path=tmp_path / "real-public.db",
        release_out_dir=tmp_path / "real",
    )
    return Path(result["releaseDir"])


# --------------------------------------------------------------------------- #
# The guard
# --------------------------------------------------------------------------- #

def test_demo_release_passes_demo_channel(demo_release):
    info = release_guard.assert_channel(demo_release, "demo")
    assert info["dataMode"] == "demo"


def test_demo_release_rejected_on_real_channel(demo_release):
    with pytest.raises(ReleaseGuardError, match="data mode"):
        release_guard.assert_channel(demo_release, "real")


def test_real_release_passes_real_channel(real_release):
    info = release_guard.assert_channel(real_release, "real")
    assert info["dataMode"] == "real"
    assert info["publishedJourneys"] >= 1
    assert "demo-aloria-coach" not in info["operatorIds"]


def test_missing_release_fails_closed(tmp_path):
    with pytest.raises(ReleaseGuardError, match="no release"):
        release_guard.assert_channel(tmp_path / "nothing", "real")


def test_tampered_pack_is_rejected(real_release):
    manifest = json.loads((real_release / "manifest.json").read_text())
    meta_path = real_release / manifest["files"]["meta"]["path"]
    meta_path.write_text(meta_path.read_text() + "\n", encoding="utf-8")
    with pytest.raises(Exception):
        release_guard.assert_channel(real_release, "real")


# --------------------------------------------------------------------------- #
# The production entry point
# --------------------------------------------------------------------------- #

def test_production_refuses_missing_dataset(tmp_path):
    with pytest.raises(ReleaseGuardError, match="no reviewed dataset"):
        release_production.cut_release(
            ingest_db_path=tmp_path / "absent.db",
            public_db_path=tmp_path / "public.db",
            release_out_dir=tmp_path / "out",
        )


def test_production_refuses_empty_import(tmp_path):
    ingest = tmp_path / "empty.db"
    connection = ktel_db.connect(str(ingest))
    try:
        ktel_db.migrate(connection)
        seed_registry(connection)
        connection.commit()
    finally:
        connection.close()
    with pytest.raises(ReleaseGuardError, match="no published trips"):
        release_production.cut_release(
            ingest_db_path=ingest,
            public_db_path=tmp_path / "public.db",
            release_out_dir=tmp_path / "out",
        )


def test_production_refuses_demo_operator(tmp_path):
    """A demo database stamped through the production path must be refused."""
    ingest = tmp_path / "demo-ingest.db"
    # Reuse the demo seeder to produce a published demo database, then try to
    # ship it as production.
    demo_seed.seed(
        ingest_db_path=ingest,
        public_db_path=tmp_path / "demo-public.db",
        release_out_dir=tmp_path / "demo-out",
        reviewer="pytest",
    )
    with pytest.raises(ReleaseGuardError, match="demonstration operator"):
        release_production.cut_release(
            ingest_db_path=ingest,
            public_db_path=tmp_path / "prod-public.db",
            release_out_dir=tmp_path / "prod-out",
        )
