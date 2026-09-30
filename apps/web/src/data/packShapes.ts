/**
 * The canonical offline pack shapes.
 *
 * There is exactly one generator for release packs and a pack contains precisely
 * what the matching `/v1/...` endpoint would return. The web app therefore has no
 * timetable engine of its own: reading a pack is deserialising an endpoint
 * response, which is why `StaticPackSource` and `HttpApiSource` can be held to
 * deep equality for the same release.
 *
 * Logical pack names, as written into `manifest.files`:
 *
 *   meta                     -> /v1/meta
 *   coverage                 -> /v1/coverage
 *   sources                  -> /v1/sources
 *   places                   -> /v1/places with no query: every place
 *   operators                -> /v1/operators/{id} per operator, keyed by id
 *   stops                    -> /v1/stops/{id} per stop, keyed by id
 *   journeys-<serviceDate>   -> /v1/journeys for that date, plus the
 *                               /v1/journeys/{id} detail for every result
 *   gtfs                     -> /v1/gtfs
 *
 * One shape is necessarily narrower than its endpoint. `/v1/journeys` is
 * parameterised by origin and destination, which a static file cannot be, so the
 * pack carries the whole day's result list and the client selects from it by stop
 * id. Nothing is computed: the summaries and details are returned exactly as the
 * generator wrote them.
 */
import type {
  CoverageResult,
  CoverageState,
  Envelope,
  JourneyDetail,
  JourneySummary,
  Meta,
  Operator,
  Place,
  SourcesResult,
  StopResult,
} from './contract';

/** A `stops` pack entry: a `/v1/stops/{id}` response without its own envelope. */
export type StopPackEntry = Omit<StopResult, keyof Envelope>;

export type MetaPack = Meta;

export interface PlacesPack extends Envelope {
  readonly places: readonly Place[];
  readonly total: number;
}

export interface OperatorsPack extends Envelope {
  readonly operators: Readonly<Record<string, Operator>>;
}

export interface StopsPack extends Envelope {
  readonly serviceDate?: string;
  readonly stops: Readonly<Record<string, StopPackEntry>>;
}

export interface JourneysPack extends Envelope {
  readonly serviceDate: string;
  readonly coverage: CoverageState;
  /** Every journey that operates on this service date, in departure order. */
  readonly results: readonly JourneySummary[];
  /** The `/v1/journeys/{id}` detail for each result, keyed by journey id. */
  readonly journeys: Readonly<Record<string, JourneyDetail>>;
}

export type CoveragePack = CoverageResult;
export type SourcesPack = SourcesResult;

/** Logical names a release must carry, other than the per-date journey packs. */
export const REQUIRED_PACK_NAMES = ['meta', 'coverage', 'sources', 'places', 'operators', 'stops'] as const;
export type RequiredPackName = (typeof REQUIRED_PACK_NAMES)[number];

export const JOURNEYS_PREFIX = 'journeys-';

export function journeysPackName(serviceDate: string): string {
  return `${JOURNEYS_PREFIX}${serviceDate}`;
}

export function serviceDateFromPackName(name: string): string | null {
  return name.startsWith(JOURNEYS_PREFIX) ? name.slice(JOURNEYS_PREFIX.length) : null;
}
