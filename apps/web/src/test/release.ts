/**
 * The test harness reads the real published release.
 *
 * Nothing here hand-builds a pack. The fixture is whatever
 * `apps/web/public/data/` currently holds, which is what
 * `scripts/web-sync-packs.sh` copied out of the canonical release, so a change to
 * the release shape breaks these tests rather than passing silently against a
 * stale hand-written copy.
 *
 * Two fetch implementations are exposed:
 *
 *  - `staticFetch` serves `/data/manifest.json` and `/data/packs/...` from disk,
 *    which is exactly what StaticPackSource talks to in a browser;
 *  - `apiFetch` serves the `/v1/...` endpoints from the same packs, written
 *    independently of StaticPackSource so that the parity test actually compares
 *    two implementations rather than one implementation with itself.
 */
import { readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import type {
  CoverageResult,
  JourneyResult,
  JourneysResult,
  JourneyStop,
  Manifest,
  Meta,
  OperatorResult,
  OperatorsResult,
  PlacesResult,
  SourcesResult,
  StopDeparture,
  StopResult,
} from '../data/contract';
import type { JourneysPack, OperatorsPack, PlacesPack, StopsPack } from '../data/packShapes';
import { journeysPackName, serviceDateFromPackName } from '../data/packShapes';
import { searchPlaces } from '../data/packQuery';
import { boardingPointFromStop, composeJourneyId, parseJourneyId, scopedDetail, segmentSummary, tripIdOf } from '../data/segments';

const here = dirname(fileURLToPath(import.meta.url));
export const DATA_DIR = join(here, '..', '..', 'public', 'data');

export function releaseIsPresent(): boolean {
  return existsSync(join(DATA_DIR, 'manifest.json'));
}

export function readManifest(): Manifest {
  return JSON.parse(readFileSync(join(DATA_DIR, 'manifest.json'), 'utf8')) as Manifest;
}

export function readPackBytes(path: string): Buffer {
  return readFileSync(join(DATA_DIR, path));
}

export function readPack<T>(manifest: Manifest, name: string): T {
  const entry = manifest.files[name];
  if (!entry) throw new Error(`The release carries no "${name}" pack.`);
  return JSON.parse(readPackBytes(entry.path).toString('utf8')) as T;
}

export function serviceDates(manifest: Manifest): string[] {
  return Object.keys(manifest.files)
    .map(serviceDateFromPackName)
    .filter((value): value is string => value !== null)
    .sort();
}

function toArrayBuffer(buffer: Buffer): ArrayBuffer {
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength) as ArrayBuffer;
}

function bytesResponse(buffer: Buffer, mediaType = 'application/json'): Response {
  return new Response(toArrayBuffer(buffer), { status: 200, headers: { 'Content-Type': mediaType } });
}

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
}

export interface StaticFetchOptions {
  /** Paths that should answer as if the device were offline. */
  readonly failPaths?: ReadonlySet<string>;
  /** Paths whose bytes should be corrupted, to exercise integrity rejection. */
  readonly corruptPaths?: ReadonlySet<string>;
  /** Everything fails, as when there is no connection at all. */
  readonly offline?: boolean;
  /** Records every path requested, so a test can assert what was fetched. */
  readonly log?: string[];
}

/** Serves `/data/...` from the release on disk. */
export function makeStaticFetch(options: StaticFetchOptions = {}): typeof fetch {
  return (async (input: RequestInfo | URL) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    const path = raw.replace(/^https?:\/\/[^/]+/, '').replace(/^\/data\//, '');
    options.log?.push(path);
    if (options.offline || options.failPaths?.has(path)) {
      throw new TypeError('Failed to fetch');
    }
    const file = join(DATA_DIR, path);
    if (!existsSync(file)) return new Response('not found', { status: 404 });
    const bytes = readFileSync(file);
    if (options.corruptPaths?.has(path)) {
      // Flip one byte. The length still matches, so only the digest can catch it,
      // which is precisely the case worth testing.
      const corrupted = Buffer.from(bytes);
      const index = Math.max(0, corrupted.length - 3);
      corrupted[index] = corrupted[index] === 0x20 ? 0x09 : 0x20;
      return bytesResponse(corrupted);
    }
    return bytesResponse(bytes, path.endsWith('.zip') ? 'application/zip' : 'application/json');
  }) as typeof fetch;
}

// ---------------------------------------------------------------------------
// An independent `/v1` implementation, built from the same packs.
// ---------------------------------------------------------------------------

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;
const minutesOf = (iso: string) => Number(iso.slice(11, 13)) * 60 + Number(iso.slice(14, 16));

export interface ApiFetchOptions {
  readonly offline?: boolean;
  readonly log?: string[];
}

export function makeApiFetch(options: ApiFetchOptions = {}): typeof fetch {
  const manifest = readManifest();
  const meta = readPack<Meta>(manifest, 'meta');
  const places = readPack<PlacesPack>(manifest, 'places');
  const operators = readPack<OperatorsPack>(manifest, 'operators');
  const stops = readPack<StopsPack>(manifest, 'stops');
  const coverage = readPack<CoverageResult>(manifest, 'coverage');
  const sources = readPack<SourcesResult>(manifest, 'sources');
  const dates = serviceDates(manifest);
  const journeysFor = (date: string): JourneysPack | null =>
    manifest.files[journeysPackName(date)] ? readPack<JourneysPack>(manifest, journeysPackName(date)) : null;

  const envelope = {
    contractVersion: places.contractVersion,
    releaseId: places.releaseId,
    publishedAt: places.publishedAt,
    dataMode: places.dataMode,
  };

  const error = (status: number, code: string, message: string, field?: string) =>
    jsonResponse({ error: { code, message, ...(field ? { field } : {}) } }, status);

  return (async (input: RequestInfo | URL) => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    if (options.offline) throw new TypeError('Failed to fetch');
    const url = new URL(raw, 'https://api.test.invalid');
    options.log?.push(url.pathname + url.search);
    const path = url.pathname;
    const q = url.searchParams;

    if (path === '/v1/meta') return jsonResponse(meta satisfies Meta);
    if (path === '/v1/coverage') return jsonResponse(coverage satisfies CoverageResult);
    if (path === '/v1/sources') return jsonResponse(sources satisfies SourcesResult);
    if (path === '/v1/offline/manifest') return jsonResponse(manifest);

    if (path === '/v1/places') {
      const found = searchPlaces(places.places, q.get('q') ?? '', Number(q.get('limit') ?? '20'));
      return jsonResponse({ ...envelope, places: found, total: found.length } satisfies PlacesResult);
    }

    if (path === '/v1/operators') {
      return jsonResponse({ ...envelope, operators: Object.values(operators.operators) } satisfies OperatorsResult);
    }

    const operatorMatch = /^\/v1\/operators\/(.+)$/.exec(path);
    if (operatorMatch) {
      const operator = operators.operators[decodeURIComponent(operatorMatch[1] as string)];
      if (!operator) return error(404, 'not_found', 'That operator is not in this data release.');
      return jsonResponse({ ...envelope, operator } satisfies OperatorResult);
    }

    const stopMatch = /^\/v1\/stops\/(.+)$/.exec(path);
    if (stopMatch) {
      const id = decodeURIComponent(stopMatch[1] as string);
      const entry = stops.stops[id];
      if (!entry) return error(404, 'not_found', 'That place is not in this data release.');
      const date = q.get('date') ?? stops.serviceDate ?? dates[0] ?? '';
      let departures: StopDeparture[];
      if (stops.serviceDate === date) {
        departures = [...entry.departures];
      } else {
        departures = [];
        const pack = journeysFor(date);
        for (const detail of Object.values(pack?.journeys ?? {})) {
          const calling = detail.stops.find((stop) => stop.stopId === id);
          const last = detail.stops[detail.stops.length - 1];
          if (!calling || !last) continue;
          departures.push({
            journeyId: composeJourneyId(tripIdOf(detail.id), id, last.stopId),
            operator: detail.operator,
            departureAt: calling.departureAt,
            arrivalAt: calling.arrivalAt,
            timeQuality: calling.timeQuality,
            headsign: last.name,
            destinationStopId: last.stopId,
            serviceDate: detail.serviceDate,
          });
        }
        departures.sort((a, b) => (a.departureAt ?? a.arrivalAt ?? '').localeCompare(b.departureAt ?? b.arrivalAt ?? ''));
      }
      return jsonResponse({ ...envelope, ...entry, serviceDate: date, departures } satisfies StopResult);
    }

    const journeyMatch = /^\/v1\/journeys\/(.+)$/.exec(path);
    if (journeyMatch) {
      const rawId = decodeURIComponent(journeyMatch[1] as string);
      let parsed: ReturnType<typeof parseJourneyId>;
      try {
        parsed = parseJourneyId(rawId);
      } catch {
        return error(400, 'invalid_request', 'That journey link is not in a form this release understands.', 'id');
      }
      const date = q.get('date') ?? '';
      const pack = journeysFor(date);
      const detail = pack?.journeys[parsed.tripId];
      if (!detail) return error(404, 'not_found', 'That journey does not run on that service date in this release.');
      const resolver =
        parsed.boardStopId && parsed.boardStopId !== [...detail.stops].sort((a, b) => a.sequence - b.sequence)[0]?.stopId
          ? (stop: JourneyStop) => boardingPointFromStop(stops.stops[stop.stopId], stop)
          : undefined;
      const journey = scopedDetail(detail, parsed.boardStopId, parsed.alightStopId, resolver);
      return jsonResponse({ ...envelope, journey } satisfies JourneyResult);
    }

    if (path === '/v1/journeys') {
      const originId = q.get('origin') ?? '';
      const destinationId = q.get('destination') ?? '';
      const date = q.get('date') ?? '';
      const base = { ...envelope, query: { originId, destinationId, date } };
      const origin = places.places.find((p) => p.id === originId);
      const destination = places.places.find((p) => p.id === destinationId);
      if (!origin) return error(404, 'not_found', 'That origin is not in this data release.', 'origin');
      if (!destination) return error(404, 'not_found', 'That destination is not in this data release.', 'destination');

      const cov =
        origin.coverage === 'not_covered' || destination.coverage === 'not_covered' ? 'not_covered' : coverage.coverage.state;
      if (origin.id === destination.id) {
        return jsonResponse({ ...base, coverage: cov, results: [], unavailableReason: 'origin_equals_destination' } satisfies JourneysResult);
      }
      if (cov === 'not_covered') {
        return jsonResponse({ ...base, coverage: 'not_covered', results: [], unavailableReason: 'outside_coverage' } satisfies JourneysResult);
      }
      const pack = dates.includes(date) ? journeysFor(date) : null;
      if (!pack) {
        return jsonResponse({ ...base, coverage: cov, results: [], unavailableReason: 'no_service_on_date' } satisfies JourneysResult);
      }

      const expand = (placeId: string, kind: string) =>
        kind === 'stop'
          ? new Set([placeId])
          : new Set([placeId, ...places.places.filter((p) => p.parentId === placeId).map((p) => p.id)]);
      const originIds = expand(origin.id, origin.kind);
      const destinationIds = expand(destination.id, destination.kind);

      let results = pack.results
        .map((summary) => {
          const detail = pack.journeys[summary.id];
          return detail ? segmentSummary(summary, detail, originIds, destinationIds) : null;
        })
        .filter((journey): journey is NonNullable<typeof journey> => journey !== null);
      const operatorFilter = q.get('operators');
      if (operatorFilter) {
        const allowed = new Set(operatorFilter.split(','));
        results = results.filter((journey) => allowed.has(journey.operator.id));
      }
      if (q.get('accessible') === 'true') {
        results = results.filter((journey) => {
          const detail = pack.journeys[tripIdOf(journey.id)];
          return detail?.boardingPoint.stepFree === true && detail.boardingPoint.reviewState === 'published';
        });
      }
      const from = q.get('departFrom');
      const to = q.get('departTo');
      if ((from && HHMM.test(from)) || (to && HHMM.test(to))) {
        const lower = from && HHMM.test(from) ? minutesOf(`0000-00-00T${from}:00`) : null;
        const upper = to && HHMM.test(to) ? minutesOf(`0000-00-00T${to}:00`) : null;
        results = results.filter((journey) => {
          const minutes = minutesOf(journey.departure.at);
          if (lower !== null && minutes < lower) return false;
          if (upper !== null && minutes > upper) return false;
          return true;
        });
      }

      if (results.length === 0) {
        const anyOnThisDate = pack.results.some((summary) => {
          const detail = pack.journeys[summary.id];
          return detail ? segmentSummary(summary, detail, originIds, destinationIds) !== null : false;
        });
        return jsonResponse({
          ...base,
          coverage: cov,
          results: [],
          unavailableReason: anyOnThisDate ? 'no_service_on_date' : 'outside_coverage',
        } satisfies JourneysResult);
      }
      results.sort((a, b) => a.departure.at.localeCompare(b.departure.at) || a.id.localeCompare(b.id));
      return jsonResponse({ ...base, coverage: cov, results, unavailableReason: null } satisfies JourneysResult);
    }

    return error(404, 'not_found', `No endpoint at ${path}.`);
  }) as typeof fetch;
}

/** A pair of origin and destination ids that genuinely has journeys on some date. */
export function findWorkingQuery(): { originId: string; destinationId: string; date: string } | null {
  const manifest = readManifest();
  for (const date of serviceDates(manifest)) {
    const pack = readPack<JourneysPack>(manifest, journeysPackName(date));
    const first = pack.results[0];
    if (first) return { originId: first.departure.stopId, destinationId: first.arrival.stopId, date };
  }
  return null;
}
