import { describe, expect, it } from 'vitest';
import { StaticPackSource } from './StaticPackSource';
import { HttpApiSource } from './HttpApiSource';
import { ContractError } from './contract';
import { composeJourneyId, parseJourneyId, scopedDetail, tripIdOf } from './segments';
import { makeApiFetch, makeStaticFetch, readManifest, readPack, releaseIsPresent, serviceDates } from '../test/release';
import type { JourneysPack, PlacesPack } from './packShapes';
import { journeysPackName } from './packShapes';

describe('journey id composition', () => {
  it('round-trips a trip and its leg', () => {
    const id = composeJourneyId('kt_abc', 'ks_board', 'ks_alight');
    expect(id).toBe('kt_abc~ks_board~ks_alight');
    expect(tripIdOf(id)).toBe('kt_abc');
    expect(parseJourneyId(id)).toEqual({ tripId: 'kt_abc', boardStopId: 'ks_board', alightStopId: 'ks_alight' });
  });

  it('reads a bare id as the whole run', () => {
    expect(parseJourneyId('kt_abc')).toEqual({ tripId: 'kt_abc', boardStopId: null, alightStopId: null });
  });

  it('rejects a malformed id rather than guessing', () => {
    expect(() => parseJourneyId('kt_abc~only-one')).toThrow(ContractError);
    expect(() => parseJourneyId('~~')).toThrow(ContractError);
  });
});

const detail = {
  contractVersion: '1.0.0',
  id: 'kt_trip~ks_a~ks_c',
  operator: { id: 'op', name: { el: 'o', en: 'o', sq: 'o' }, logoAvailable: false },
  departure: { at: '2026-10-02T09:00:00+03:00', stopId: 'ks_a', stopName: { el: 'A', en: 'A', sq: 'A' }, quality: 'scheduled' },
  arrival: { at: '2026-10-02T12:10:00+03:00', stopId: 'ks_c', stopName: { el: 'C', en: 'C', sq: 'C' }, quality: 'approximate' },
  durationMinutes: 190,
  intermediateStopCount: 1,
  serviceDate: '2026-10-02',
  crossesMidnight: false,
  positionQuality: 'scheduled',
  fare: { amount: 18, currency: 'EUR', isIndicative: true },
  freshness: { checkedAt: '2026-09-20T06:00:00Z', ageHours: 10, state: 'stale' },
  confidence: 'reviewed',
  purchase: { kind: 'online', url: 'https://demo.invalid/x', label: { el: 'l', en: 'l', sq: 'l' } },
  boardingPoint: {
    stopId: 'ks_a', name: { el: 'A', en: 'A', sq: 'A' }, terminalName: { el: 'A', en: 'A', sq: 'A' },
    bay: 'A1', latitude: 0, longitude: 0, instructions: null, reviewState: 'published', reviewedAt: null, stepFree: true,
  },
  selectedSegment: { boardStopId: 'ks_a', alightStopId: 'ks_c' },
  stops: [
    { stopId: 'ks_a', sequence: 1, name: { el: 'A', en: 'A', sq: 'A' }, latitude: 0, longitude: 0, arrivalAt: null, departureAt: '2026-10-02T09:00:00+03:00', timeQuality: 'scheduled', pickup: 'allowed', dropoff: 'not_allowed', segmentRole: 'board' },
    { stopId: 'ks_b', sequence: 2, name: { el: 'B', en: 'B', sq: 'B' }, latitude: 0, longitude: 0, arrivalAt: '2026-10-02T10:05:00+03:00', departureAt: '2026-10-02T10:10:00+03:00', timeQuality: 'scheduled', pickup: 'allowed', dropoff: 'allowed', segmentRole: 'onSegment' },
    { stopId: 'ks_c', sequence: 3, name: { el: 'C', en: 'C', sq: 'C' }, latitude: 0, longitude: 0, arrivalAt: '2026-10-02T12:10:00+03:00', departureAt: null, timeQuality: 'approximate', pickup: 'not_allowed', dropoff: 'allowed', segmentRole: 'alight' },
  ],
  geometry: null,
  restrictions: [],
  provenance: [],
  correctionUrl: 'https://x/c',
} as const;

describe('scopedDetail', () => {
  it('headlines the chosen leg and marks the stops', () => {
    const scoped = scopedDetail(detail as never, 'ks_a', 'ks_b');
    expect(scoped.arrival.stopId).toBe('ks_b');
    expect(scoped.arrival.at).toBe('2026-10-02T10:05:00+03:00');
    expect(scoped.durationMinutes).toBe(65);
    expect(scoped.id).toBe('kt_trip~ks_a~ks_b');
    expect(scoped.selectedSegment).toEqual({ boardStopId: 'ks_a', alightStopId: 'ks_b' });
    expect(scoped.stops.map((s) => s.segmentRole)).toEqual(['board', 'alight', 'afterAlight']);
  });

  it('keeps the whole run when no leg is named', () => {
    const scoped = scopedDetail(detail as never, null, null);
    expect(scoped.arrival.stopId).toBe('ks_c');
    expect(scoped.stops.map((s) => s.segmentRole)).toEqual(['board', 'onSegment', 'alight']);
  });

  it('marks earlier stops as before the boarding point for an intermediate leg', () => {
    const scoped = scopedDetail(detail as never, 'ks_b', 'ks_c');
    expect(scoped.departure.stopId).toBe('ks_b');
    expect(scoped.stops.map((s) => s.segmentRole)).toEqual(['beforeBoard', 'board', 'alight']);
  });
});

const hasRelease = releaseIsPresent();
const describeRelease = hasRelease ? describe : describe.skip;

function nameMatch(places: PlacesPack, needle: string): string {
  const place = places.places.find((p) => p.name.en.toLowerCase().includes(needle));
  if (!place) throw new Error(`no place matching ${needle}`);
  return place.id;
}

describeRelease('the published release scopes an intermediate leg the same way on both sources', () => {
  it('arrives at the searched stop in the list and the detail, not the end of the run', async () => {
    const manifest = readManifest();
    const places = readPack<PlacesPack>(manifest, 'places');
    const [date] = serviceDates(manifest);
    const pack = readPack<JourneysPack>(manifest, journeysPackName(date!));
    // Only run when the release serves a run with a stop past the searched one.
    const hasVia = Object.values(pack.journeys).some((d) => d.stops.length >= 3);
    if (!hasVia) return;

    const origin = nameMatch(places, 'aloria');
    const destination = nameMatch(places, 'veraki');
    const query = { originId: origin, destinationId: destination, date: date! };

    const staticSource = new StaticPackSource({ baseUrl: '/data/', fetchImpl: makeStaticFetch() });
    const apiSource = new HttpApiSource({ baseUrl: 'https://api.test.invalid', fetchImpl: makeApiFetch() });

    const [a, b] = await Promise.all([staticSource.journeys(query), apiSource.journeys(query)]);
    expect(a).toEqual(b);
    expect(a.results.length).toBeGreaterThan(0);
    for (const result of a.results) {
      expect(result.arrival.stopId).toBe(destination);
      const detailFromStatic = (await staticSource.journey(result.id, date!)).journey;
      const detailFromApi = (await apiSource.journey(result.id, date!)).journey;
      expect(detailFromStatic).toEqual(detailFromApi);
      // The detail agrees with the list: same leg, same arrival, same duration.
      expect(detailFromStatic.arrival.stopId).toBe(destination);
      expect(detailFromStatic.arrival.at).toBe(result.arrival.at);
      expect(detailFromStatic.durationMinutes).toBe(result.durationMinutes);
      const alight = detailFromStatic.stops.find((s) => s.stopId === destination);
      expect(alight?.segmentRole).toBe('alight');
    }
  });
});
