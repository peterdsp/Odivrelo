"""Calendar ingestion is atomic, review-gated and replaces obsolete exceptions."""
import copy
import tempfile
import unittest
from pathlib import Path

from odivrelo_ktel import ktel_db
from odivrelo_ktel.ktel_ingest import import_normalized_snapshot, review_entity
from odivrelo_ktel.ktel_registry import seed_registry


class CalendarImportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.conn = ktel_db.connect(str(Path(self.temp.name) / 'ingest.db'))
        ktel_db.migrate(self.conn)
        seed_registry(self.conn)
        # Test-only synthetic records; never an operator observation.
        self.payload = {
            'operatorId': 'ktel-kavala', 'sourceId': 'manual-review',
            'serviceCalendars': [{
                'externalId': 'weekdays', 'name': 'Test calendar',
                'validFrom': '2026-10-01', 'validUntil': '2026-10-31',
                'monday': True, 'tuesday': True,
                'exceptions': [{'serviceDate': '2026-10-05', 'exceptionType': 'removed'}],
            }],
            'trips': [{
                'externalId': 'test-trip', 'serviceDate': '2026-10-05',
                'departureAt': '2026-10-05T08:00:00+03:00',
                'calendarExternalId': 'weekdays',
            }],
        }

    def tearDown(self):
        self.conn.close()
        self.temp.cleanup()

    def test_import_binds_calendar_as_candidate_and_retains_source(self):
        result = import_normalized_snapshot(self.conn, self.payload)
        calendar = self.conn.execute('SELECT * FROM ktel_service_calendars').fetchone()
        trip = self.conn.execute('SELECT * FROM ktel_trips').fetchone()
        self.assertEqual(result['serviceCalendars'], 1)
        self.assertEqual(calendar['publication_state'], 'candidate')
        self.assertEqual(calendar['source_id'], 'manual-review')
        self.assertEqual(trip['calendar_id'], calendar['id'])
        self.assertEqual(self.conn.execute(
            "SELECT COUNT(*) FROM ktel_source_records WHERE record_kind='service_calendar'"
        ).fetchone()[0], 1)

    def test_unknown_calendar_rolls_back_the_entire_snapshot(self):
        self.payload['trips'][0]['calendarExternalId'] = 'missing'
        with self.assertRaisesRegex(ValueError, 'unknown calendar'):
            import_normalized_snapshot(self.conn, self.payload)
        for table in ('ktel_trips', 'ktel_service_calendars', 'ktel_calendar_exceptions',
                      'ktel_import_runs', 'ktel_source_records'):
            self.assertEqual(self.conn.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0], 0)

    def test_reimport_removes_old_exceptions_and_requires_review_again(self):
        import_normalized_snapshot(self.conn, self.payload)
        calendar_id = self.conn.execute('SELECT id FROM ktel_service_calendars').fetchone()[0]
        review_entity(self.conn, entity_kind='service_calendar', entity_id=calendar_id,
                      action='publish', reviewer='test-reviewer', reason='Test fixture only')
        self.conn.commit()
        replacement = copy.deepcopy(self.payload)
        replacement['serviceCalendars'][0]['exceptions'] = []
        replacement['serviceCalendars'][0]['monday'] = False
        import_normalized_snapshot(self.conn, replacement)
        self.assertEqual(self.conn.execute('SELECT COUNT(*) FROM ktel_calendar_exceptions').fetchone()[0], 0)
        row = self.conn.execute('SELECT * FROM ktel_service_calendars').fetchone()
        self.assertEqual(row['id'], calendar_id)
        self.assertEqual(row['monday'], 0)
        self.assertEqual(row['publication_state'], 'candidate')

    def test_date_specific_reimport_clears_previous_calendar_binding(self):
        import_normalized_snapshot(self.conn, self.payload)
        self.payload.pop('serviceCalendars')
        self.payload['trips'][0].pop('calendarExternalId')
        import_normalized_snapshot(self.conn, self.payload)
        self.assertIsNone(self.conn.execute('SELECT calendar_id FROM ktel_trips').fetchone()[0])

    def test_changed_calendar_withholds_previously_published_trip(self):
        from odivrelo_ktel.ktel_publish import compile_public_database
        import_normalized_snapshot(self.conn, self.payload)
        for kind, table in [('service_calendar', 'ktel_service_calendars'), ('trip', 'ktel_trips')]:
            entity_id = self.conn.execute(f'SELECT id FROM {table}').fetchone()[0]
            review_entity(self.conn, entity_kind=kind, entity_id=entity_id,
                          action='publish', reviewer='test-reviewer', reason='Test fixture only')
        self.conn.commit()
        self.payload['trips'] = []
        self.payload['serviceCalendars'][0]['monday'] = False
        import_normalized_snapshot(self.conn, self.payload)
        public_path = str(Path(self.temp.name) / 'public.db')
        compile_public_database(
            ingest_db_path=str(Path(self.temp.name) / 'ingest.db'),
            public_db_path=public_path,
        )
        public = ktel_db.connect(public_path)
        try:
            self.assertEqual(public.execute('SELECT COUNT(*) FROM ktel_trips').fetchone()[0], 0)
        finally:
            public.close()
