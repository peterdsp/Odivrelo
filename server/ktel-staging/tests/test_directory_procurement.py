import json
import unittest

from odivrelo_ktel.ktel_directory import (
    discover_operator_urls,
    parse_operator_page,
    reconcile_ticketweb_tenants,
)
from odivrelo_ktel.ktel_ticketweb import inspect_flutter_bundle, normalize_journey_response


class DirectoryProcurementTest(unittest.TestCase):
    def test_discovery_uses_sitemap_and_same_site_links(self):
        pages = {
            "https://ktelbus.com/sitemap.xml": (200, {}, b"<urlset><url><loc>https://ktelbus.com/ktel-foo/</loc></url></urlset>"),
            "https://ktelbus.com/robots.txt": (200, {}, b"Sitemap: https://ktelbus.com/sitemap.xml"),
            "https://ktelbus.com/": (200, {}, b'<a href="/ktel-bar/">bar</a><a href="https://example.com/ktel-no/">no</a>'),
            "https://ktelbus.com/ktel-foo": (200, {}, b"foo"),
            "https://ktelbus.com/ktel-bar": (200, {}, b"bar"),
        }

        def fetch(url):
            return pages.get(url, (404, {}, b""))

        result = discover_operator_urls(fetch=fetch, max_pages=20)
        self.assertEqual(result["operatorUrls"], ["https://ktelbus.com/ktel-bar", "https://ktelbus.com/ktel-foo"])
        self.assertNotIn("https://example.com/ktel-no", result["visitedUrls"])

    def test_parse_preserves_missing_fields_and_field_provenance(self):
        page = """<html><title>ΚΤΕΛ Φωκίδας</title><body>
        Διεύθυνση: Οδός Αθηνάς 1 | Πόλη: Άμφισσα | 33100
        Τηλέφωνο: 22650 12345 Αθήνα: 210 1234567
        <a href='mailto:info@example.gr'>email</a><a href='https://ktelfokidas.gr'>site</a>
        </body></html>"""
        result = parse_operator_page("https://ktelbus.com/ktel-fokidas/", page, retrieved_at="2026-10-06T10:00:00Z")
        self.assertEqual(result["fields"]["nameEl"], "Φωκίδας")
        self.assertEqual(result["fields"]["postalCode"], "33100")
        self.assertEqual(result["fields"]["retrievedAt"], "2026-10-06T10:00:00Z")
        self.assertEqual(result["provenance"]["email"]["originalValue"], "info@example.gr")
        self.assertIsNone(result["fields"]["fax"])

    def test_reconciliation_keeps_unmatched_and_ambiguous_records(self):
        result = reconcile_ticketweb_tenants(
            [{"id": "op-a", "slug": "achaia", "nameEn": "KTEL Achaia"}, {"id": "op-b", "slug": "arcadia", "nameEn": "KTEL Arcadia"}],
            [{"tenant": "ach", "operatorId": "op-a"}, {"tenant": "mystery"}],
        )
        self.assertEqual(len(result["matched"]), 1)
        self.assertEqual(result["unmatchedTenants"][0]["tenant"], "mystery")
        self.assertEqual(len(result["operatorsWithoutTicketWeb"]), 1)

    def test_flutter_inspection_rejects_mutations_and_journey_requires_direct_rows(self):
        report = inspect_flutter_bundle('"booking/stopGroups" "booking/executions" "reservation/create"', base_url="https://ktelbus.gr/x/ticketweb/")
        self.assertIn("booking/stopGroups", report["readOnlyEndpoints"])
        self.assertTrue(any("reservation" in path for path in report["rejectedMutatingPaths"]))
        self.assertEqual(normalize_journey_response({"data": {"message": "no rows"}}, tenant="x", source_url="u", retrieved_at="t"), [])

    def test_journey_normalization_retains_source_and_availability(self):
        rows = normalize_journey_response({"executions": [{"id": 7, "origin": {"id": 1}, "destination": {"id": 2}, "departureAt": "2026-10-06T09:00:00+03:00", "availability": "unknown"}]}, tenant="x", source_url="u", retrieved_at="t")
        self.assertEqual(rows[0]["externalId"], "7")
        self.assertEqual(rows[0]["source"]["verificationState"], "direct_response")
        self.assertEqual(rows[0]["availability"], "unknown")


if __name__ == "__main__":
    unittest.main()
