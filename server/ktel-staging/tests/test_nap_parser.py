"""Tests for the NAP 2020 workbook parser and its candidate import.

The parser turns the real ODbL NAP workbook into a reviewable candidate dataset.
These tests pin the Greek day-prose normalization, the two cell layouts, the
operator mapping, and the fact that the imported dataset is always candidate,
city-level and never published.
"""
import tempfile
import unittest
from pathlib import Path

from odivrelo_ktel import ktel_db, nap_parser as N
from odivrelo_ktel.ktel_ingest import import_normalized_snapshot
from odivrelo_ktel.ktel_registry import load_registry, seed_registry


def days(raw):
    return tuple(int(x) for x in N.parse_days(raw).days)


class ParseDaysTest(unittest.TestCase):
    def test_single_and_ranges(self):
        self.assertEqual(days("ΚΥΡ"), (0, 0, 0, 0, 0, 0, 1))
        self.assertEqual(days("ΣΑΒ"), (0, 0, 0, 0, 0, 1, 0))
        self.assertEqual(days("ΔΕΥ ΜΕΧΡΙ ΠΑΡ"), (1, 1, 1, 1, 1, 0, 0))
        self.assertEqual(days("ΔΕΥ-ΚΥΡ"), (1, 1, 1, 1, 1, 1, 1))
        self.assertEqual(days("ΔΕΥ-ΠΑΡ"), (1, 1, 1, 1, 1, 0, 0))

    def test_unions_and_weekend(self):
        self.assertEqual(days("ΠΑΡ ΚΑΙ ΚΥΡ"), (0, 0, 0, 0, 1, 0, 1))
        self.assertEqual(days("ΠΑΡ,ΚΥΡ"), (0, 0, 0, 0, 1, 0, 1))
        self.assertEqual(days("ΣΚ"), (0, 0, 0, 0, 0, 1, 1))
        self.assertEqual(days("ΜΟΝΟ ΣΚ"), (0, 0, 0, 0, 0, 1, 1))

    def test_except(self):
        self.assertEqual(days("ΕΚΤΟΣ ΚΥΡ"), (1, 1, 1, 1, 1, 1, 0))
        self.assertEqual(days("ΕΚΤΟΣ ΣΚ"), (1, 1, 1, 1, 1, 0, 0))
        self.assertEqual(days("ΕΚΤΟΣ ΔΕΥ ΚΑΙ ΣΑΒ"), (0, 1, 1, 1, 1, 0, 1))

    def test_latin_lookalike_tau(self):
        # The workbook has "TΡ" with a Latin T; it must still parse as Tuesday.
        self.assertEqual(days("TΡ/ΠΕΜ/ΣΑΒ"), (0, 1, 0, 1, 0, 1, 0))

    def test_kathimerina_is_ambiguous_daily(self):
        parsed = N.parse_days("ΚΑΘΗΜΕΡΙΝΑ")
        self.assertTrue(parsed.ambiguous)
        self.assertEqual(tuple(int(x) for x in parsed.days), (1, 1, 1, 1, 1, 1, 1))

    def test_empty_is_ambiguous_no_days(self):
        parsed = N.parse_days("ΚΕΝΟ")
        self.assertTrue(parsed.ambiguous)
        self.assertFalse(parsed.any_day)


class ParseTimesTest(unittest.TestCase):
    def test_single_and_multi(self):
        self.assertEqual(N.parse_times("06:00:00"), ["06:00"])
        self.assertEqual(N.parse_times("7:45,16:00"), ["07:45", "16:00"])
        self.assertEqual(N.parse_times(None), [])
        self.assertEqual(N.parse_times(""), [])

    def test_excel_time_value(self):
        import datetime
        self.assertEqual(N.parse_times(datetime.time(6, 30)), ["06:30"])


class MapOperatorTest(unittest.TestCase):
    def setUp(self):
        self.index = N.load_operator_index(load_registry()["operators"])

    def test_exact_and_alias(self):
        self.assertEqual(N.map_operator("Ν.ΕΥΒΟΙΑΣ", self.index), "ktel-evia")
        self.assertEqual(
            N.map_operator("Ν.ΗΡΑΚΛΕΙΟΥ-ΛΑΣΙΘΕΙΟΥ", self.index),
            "ktel-heraklion-lasithi",
        )

    def test_short_fragment_does_not_false_match(self):
        # ΙΟΥ inside ΗΡΑΚΛΕΙΟΥ must not resolve to ktel-ios.
        self.assertNotEqual(
            N.map_operator("Ν.ΗΡΑΚΛΕΙΟΥ-ΛΑΣΙΘΕΙΟΥ", self.index), "ktel-ios"
        )


class ParseSheetAndImportTest(unittest.TestCase):
    #: A tiny two-layout-A sheet: merged ΑΠΟ/ΠΡΟΣ, one time per row.
    SHEET = [
        (None, None, None, None, "ΤΙΜΗ", None),
        ("ΔΡΟΜΟΛΟΓΙΑ", "ΑΠΟ", "ΠΡΟΣ", "ΩΡΑ ΑΝΑΧ/ΣΗΣ", "ΩΡΑ ΑΦΙΞΗΣ", "ΗΜΕΡΕΣ"),
        (None, "ΧΑΛΚΙΔΑ", "ΑΘΗΝΑ", "06:00", "07:30", "ΚΑΘΗΜΕΡΙΝΑ"),
        (None, None, None, "08:00", None, "ΕΚΤΟΣ ΚΥΡ"),
    ]

    def test_parse_sheet_counts(self):
        result = N.parse_sheet("Ν.ΕΥΒΟΙΑΣ", self.SHEET)
        self.assertEqual(result.rows, 2)
        self.assertEqual(len(result.trips), 2)
        self.assertEqual(len(result.pairs), 1)
        self.assertIn(N.strip_accents("ΧΑΛΚΙΔΑ"), result.cities)

    def test_build_and_import_is_candidate_city_level(self):
        result = N.parse_sheet("Ν.ΕΥΒΟΙΑΣ", self.SHEET)
        snapshot = N.build_snapshot(result, "ktel-evia", "2026-09-30T00:00:00Z")
        self.assertEqual(snapshot["dataset"], "real")
        self.assertEqual(snapshot["sourceId"], "greek-nap-ktel")

        with tempfile.TemporaryDirectory() as tmp:
            conn = ktel_db.connect(str(Path(tmp) / "nap.db"))
            try:
                ktel_db.migrate(conn)
                seed_registry(conn)
                imported = import_normalized_snapshot(conn, snapshot)
                conn.commit()
                self.assertEqual(imported["trips"], 2)
                # City stops carry no coordinate, so they stay candidate.
                self.assertEqual(
                    conn.execute(
                        "SELECT COUNT(*) FROM ktel_stops "
                        "WHERE coordinate_status='missing'"
                    ).fetchone()[0],
                    conn.execute("SELECT COUNT(*) FROM ktel_stops").fetchone()[0],
                )
                # Nothing is published by the import.
                self.assertEqual(
                    conn.execute(
                        "SELECT COUNT(*) FROM ktel_trips "
                        "WHERE publication_state != 'candidate'"
                    ).fetchone()[0],
                    0,
                )
                # Trips carry the 2020 vintage and the NAP source.
                scope, source = conn.execute(
                    "SELECT schedule_scope, source_id FROM ktel_trips LIMIT 1"
                ).fetchone()
                self.assertEqual(scope, "nap_published")
                self.assertEqual(source, "greek-nap-ktel")
            finally:
                conn.close()


if __name__ == "__main__":
    unittest.main()
