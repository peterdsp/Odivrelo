/**
 * Public data contract, version 1.
 *
 * These shapes are transcribed from data/schemas/CONTRACT-v1.md and are the
 * only vocabulary the rest of the app speaks. Both PublicDataSource
 * implementations produce exactly these objects, so no screen can tell which
 * one it is talking to.
 */

export const CONTRACT_VERSION = '1.0.0';

export const LANGUAGES = ['el', 'en', 'sq'] as const;
export type Language = (typeof LANGUAGES)[number];

export type Localized = Readonly<Record<Language, string>>;

/**
 * Some fields in the release are a single string rather than a per-language
 * object, because the generator only has one language for them. Both forms are
 * accepted and `localized()` resolves either.
 */
export type LocalizedOrText = Localized | string;

export const RIGHTS_STATUSES = ['allowed', 'permission_pending', 'prohibited', 'unknown'] as const;
export type RightsStatus = (typeof RIGHTS_STATUSES)[number];

export const REVIEW_STATES = ['candidate', 'verified', 'published', 'stale', 'withdrawn', 'quarantined'] as const;
export type ReviewState = (typeof REVIEW_STATES)[number];

export const TIME_QUALITIES = ['scheduled', 'approximate', 'unknown'] as const;
export type TimeQuality = (typeof TIME_QUALITIES)[number];

export const POSITION_QUALITIES = ['scheduled', 'predicted', 'estimated', 'live'] as const;
export type PositionQuality = (typeof POSITION_QUALITIES)[number];

export const GEOMETRY_CONFIDENCES = ['unverified', 'ordered_stops_only', 'osm_candidate', 'reviewed', 'rejected'] as const;
export type GeometryConfidence = (typeof GEOMETRY_CONFIDENCES)[number];

export const PURCHASE_KINDS = ['online', 'ticket_office', 'phone', 'onboard', 'unavailable'] as const;
export type PurchaseKind = (typeof PURCHASE_KINDS)[number];

export const COVERAGE_STATES = ['covered', 'partial', 'not_covered', 'demo'] as const;
export type CoverageState = (typeof COVERAGE_STATES)[number];

export type DataMode = 'real' | 'demo';
export type FreshnessState = 'fresh' | 'aging' | 'stale';
export type Confidence = 'reviewed' | 'candidate';
export type BoardingRule = 'allowed' | 'not_allowed' | 'on_request';
export type PlaceKind = 'stop_place' | 'stop';

/**
 * Why a search returned nothing.
 *
 * `no_service_on_date` and `no_offline_data_for_date` are deliberately different
 * answers and must never be conflated. A journeys pack is materialised only for
 * the dates the data actually names; a recurring service on some other date is
 * resolved by the API on request. So a static release having no pack for a date
 * means "this device cannot answer that question offline", which is not the same
 * as "nothing runs that day", and saying the latter would invent a certainty the
 * data does not support.
 *
 * Only a live source can ever return `no_service_on_date` for a date outside the
 * packed set.
 */
export type UnavailableReason =
  | 'no_service_on_date'
  | 'no_offline_data_for_date'
  | 'outside_coverage'
  | 'origin_equals_destination'
  | null;

export interface Envelope {
  readonly contractVersion: string;
  readonly releaseId: string;
  readonly publishedAt: string;
  readonly dataMode: DataMode;
}

export interface Attribution {
  readonly name: string;
  readonly url: string;
  readonly licence: string;
}

export interface CoverageSummary {
  readonly state: CoverageState;
  readonly operatorCount: number;
  readonly corridorCount: number;
  readonly stopPlaceCount?: number;
  readonly boardingPointCount?: number;
  readonly serviceDateFrom?: string;
  readonly serviceDateTo?: string;
  readonly note: LocalizedOrText;
  readonly freshness?: Freshness;
}

export interface NotCoveredStatement {
  readonly code: string;
  readonly text: LocalizedOrText;
}

export interface ProductStamp {
  readonly name: string;
  readonly version: string;
  readonly commit: string;
  readonly builtAt: string;
}

export interface Meta extends Envelope {
  readonly product: ProductStamp;
  readonly languages: readonly Language[];
  readonly coverage: CoverageSummary;
  readonly offlineManifestUrl: string;
  readonly gtfsUrl: string;
  readonly attribution: readonly Attribution[];
  /** Service dates this release holds timetables for. Absent in some releases. */
  readonly serviceDates?: readonly string[];
}

export interface Place {
  readonly id: string;
  readonly kind: PlaceKind;
  readonly name: Localized;
  readonly parentId: string | null;
  readonly municipality: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly coordinateStatus: string;
  readonly boardingPointCount: number;
  readonly operatorIds: readonly string[];
  readonly coverage: CoverageState;
  readonly bay?: string | null;
  readonly stepFree?: boolean | null;
  readonly reviewState?: ReviewState;
  readonly reviewedAt?: string | null;
  readonly instructions?: LocalizedOrText | null;
  readonly correctionUrl?: string;
  readonly address?: string | null;
  readonly phone?: string | null;
}

export interface PlacesResult extends Envelope {
  readonly places: readonly Place[];
  readonly total: number;
}

export interface OperatorRef {
  readonly id: string;
  readonly name: Localized;
  readonly logoAvailable: boolean;
}

export interface CallingPoint {
  readonly at: string;
  readonly stopId: string;
  readonly stopName: Localized;
  readonly quality: TimeQuality;
}

export interface Fare {
  readonly amount: number;
  readonly currency: string;
  readonly isIndicative: boolean;
}

export interface Freshness {
  readonly checkedAt: string;
  readonly ageHours: number;
  readonly state: FreshnessState;
}

export interface Purchase {
  readonly kind: PurchaseKind;
  readonly url: string | null;
  readonly phone?: string | null;
  readonly address?: string | null;
  readonly openingHours?: string | null;
  readonly label?: LocalizedOrText;
  readonly disclaimer?: LocalizedOrText;
}

export interface JourneySummary {
  readonly id: string;
  readonly routeId?: string;
  readonly routeName?: LocalizedOrText;
  readonly operator: OperatorRef;
  readonly departure: CallingPoint;
  readonly arrival: CallingPoint;
  readonly durationMinutes: number;
  readonly intermediateStopCount: number;
  readonly serviceDate: string;
  readonly crossesMidnight: boolean;
  readonly positionQuality: PositionQuality;
  readonly fare: Fare | null;
  readonly freshness: Freshness;
  readonly confidence: Confidence;
  readonly purchase: Purchase;
}

export interface JourneysResult extends Envelope {
  readonly query: { readonly originId: string; readonly destinationId: string; readonly date: string };
  readonly coverage: CoverageState;
  readonly results: readonly JourneySummary[];
  readonly unavailableReason: UnavailableReason;
}

export interface BoardingPoint {
  readonly stopId: string;
  readonly name: Localized;
  readonly terminalName: Localized;
  readonly bay: string | null;
  readonly latitude: number;
  readonly longitude: number;
  readonly instructions: LocalizedOrText | null;
  readonly reviewState: ReviewState;
  readonly reviewedAt: string | null;
  readonly stepFree: boolean | null;
}

/** Where a stop sits relative to the traveller's own leg. */
export type SegmentRole = 'board' | 'onSegment' | 'alight' | 'beforeBoard' | 'afterAlight';

export interface JourneyStop {
  readonly stopId: string;
  readonly sequence: number;
  readonly name: Localized;
  readonly latitude: number;
  readonly longitude: number;
  readonly arrivalAt: string | null;
  readonly departureAt: string | null;
  readonly timeQuality: TimeQuality;
  readonly pickup: BoardingRule;
  readonly dropoff: BoardingRule;
  readonly segmentRole: SegmentRole;
}

export interface SelectedSegment {
  readonly boardStopId: string;
  readonly alightStopId: string;
}

export interface Geometry {
  readonly type: 'LineString';
  readonly coordinates: readonly (readonly [number, number])[];
  readonly confidence: GeometryConfidence;
  readonly method: string;
  readonly attribution: string;
}

export interface Restriction {
  readonly code: string;
  readonly text: LocalizedOrText;
}

export interface Provenance {
  readonly sourceId?: string;
  readonly id?: string;
  readonly sourceName?: string;
  readonly name?: string;
  readonly sourceUrl?: string;
  readonly url?: string;
  readonly retrievedAt?: string;
  readonly legalReviewedAt?: string;
  readonly rightsStatus: RightsStatus;
  readonly licence: string;
  readonly note?: LocalizedOrText;
  readonly authorityLevel?: string;
  readonly sourceKind?: string;
}

/** One display shape for a source, whichever spelling the release used. */
export interface ProvenanceView {
  readonly key: string;
  readonly name: string;
  readonly url: string | null;
  readonly checkedAt: string | null;
  readonly rightsStatus: RightsStatus;
  readonly licence: string;
}

/**
 * The host part of a URL, or null when the value is absent or unparseable.
 *
 * URLs arrive from the data release, so they are untrusted input: `new URL()`
 * throws on anything malformed, and an unguarded call in a render is enough to
 * take a whole page down. It happened once, to the operator page, on a release
 * whose `directoryUrl` was null.
 */
export function hostnameOf(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    return new URL(url).hostname;
  } catch {
    return null;
  }
}

/** A URL safe to put in an href, or null. Only http and https are allowed. */
export function safeHref(url: string | null | undefined): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.toString() : null;
  } catch {
    return null;
  }
}

export function provenanceView(entry: Provenance, index = 0): ProvenanceView {
  const key = entry.sourceId ?? entry.id ?? `source-${index}`;
  return {
    key,
    name: entry.sourceName ?? entry.name ?? key,
    url: safeHref(entry.sourceUrl ?? entry.url ?? null),
    checkedAt: entry.retrievedAt ?? entry.legalReviewedAt ?? null,
    rightsStatus: entry.rightsStatus,
    licence: entry.licence,
  };
}

export interface JourneyDetail extends JourneySummary {
  readonly boardingPoint: BoardingPoint;
  readonly selectedSegment: SelectedSegment;
  readonly stops: readonly JourneyStop[];
  readonly geometry: Geometry | null;
  readonly restrictions: readonly Restriction[];
  readonly provenance: readonly Provenance[];
  readonly correctionUrl: string;
}

export interface JourneyResult extends Envelope {
  readonly journey: JourneyDetail;
}

export interface OperatorContact {
  readonly phone: string | null;
  readonly email: string | null;
  readonly address: string | null;
}

export interface Operator {
  readonly id: string;
  readonly name: Localized;
  readonly federationNumber: number;
  readonly officialSiteUrl: string | null;
  readonly directoryUrl: string | null;
  readonly contact: OperatorContact | null;
  readonly coverage: { readonly state: CoverageState; readonly routeCount: number; readonly stopCount: number };
  readonly sources: readonly Provenance[];
  readonly verifiedAt: string;
  readonly correctionUrl: string;
  readonly routeIds?: readonly string[];
  readonly logoAvailable?: boolean;
}

export interface OperatorsResult extends Envelope {
  readonly operators: readonly Operator[];
}

export interface OperatorResult extends Envelope {
  readonly operator: Operator;
}

export interface StopDeparture {
  readonly journeyId: string;
  readonly headsignStopId?: string;
  readonly operator: OperatorRef;
  readonly departureAt: string | null;
  readonly arrivalAt: string | null;
  readonly timeQuality: TimeQuality;
  readonly headsign: Localized;
  readonly destinationStopId: string;
  readonly serviceDate: string;
}

export interface TerminalRef {
  readonly id: string;
  readonly name: Localized;
}

export interface BoardingPointSummary {
  readonly stopId?: string;
  readonly id?: string;
  readonly name: Localized;
  readonly bay: string | null;
  readonly latitude: number;
  readonly longitude: number;
  readonly stepFree?: boolean | null;
  readonly reviewState?: ReviewState;
  readonly instructions?: LocalizedOrText | null;
}

/** A `/v1/stops/{id}` response. Stop identity is flattened onto the object itself. */
export interface StopResult extends Envelope {
  readonly id: string;
  readonly kind: PlaceKind;
  readonly name: Localized;
  readonly municipality: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly coordinateStatus: string;
  readonly coverage: CoverageState;
  readonly bay: string | null;
  readonly stepFree: boolean | null;
  readonly address?: string | null;
  readonly phone?: string | null;
  readonly instructions: LocalizedOrText | null;
  readonly reviewState?: ReviewState;
  readonly reviewedAt?: string | null;
  readonly terminal: TerminalRef | null;
  readonly boardingPoints: readonly BoardingPointSummary[];
  readonly operators: readonly OperatorRef[];
  readonly operatorIds?: readonly string[];
  readonly departures: readonly StopDeparture[];
  readonly serviceDate: string;
  readonly provenance: readonly Provenance[];
  readonly correctionUrl?: string;
}

export interface CoverageResult extends Envelope {
  readonly coverage: CoverageSummary;
  readonly notCovered?: readonly NotCoveredStatement[];
  /** What an absent result does and does not prove, keyed by result status. */
  readonly absenceSemantics?: Readonly<Record<string, string>>;
}

export interface SourcesResult extends Envelope {
  readonly sources: readonly Provenance[];
  readonly total?: number;
}

export interface ManifestFile {
  readonly path: string;
  readonly sha256: string;
  readonly bytes: number;
  readonly mediaType: string;
}

/**
 * The release manifest, exactly as the one canonical generator writes it.
 *
 * `files` is a flat map from logical pack name to file entry. Names are
 * `meta`, `coverage`, `sources`, `places`, `operators`, `stops`, `gtfs` and one
 * `journeys-<serviceDate>` per service date. File names are content-addressed
 * (`<name>-<first16 of sha256>.<ext>`) so a pack can be cached forever, and the
 * manifest is written last, carrying the full digest and byte length per pack.
 */
export interface Manifest {
  readonly contractVersion: string;
  readonly product: string;
  readonly releaseId: string;
  readonly publishedAt: string;
  readonly files: Readonly<Record<string, ManifestFile>>;
  readonly counts?: Readonly<Record<string, number>>;
}

/**
 * A downloadable group of packs, as presented on the offline screen.
 *
 * Derived from the manifest by the client rather than declared in it: the
 * generator's job is to publish packs, and how they are grouped for download is
 * a presentation decision.
 */
export interface OfflinePackDescriptor {
  readonly id: string;
  readonly nameKey: 'offline.packCore' | 'offline.packTimetables' | 'offline.packGtfs';
  readonly descriptionKey: 'offline.packCoreBody' | 'offline.packTimetablesBody' | 'offline.packGtfsBody';
  readonly required: boolean;
  /** Logical pack names, as they appear in `manifest.files`. */
  readonly packNames: readonly string[];
  readonly bytes: number;
}

export type ErrorCode =
  | 'not_found'
  | 'invalid_request'
  | 'unavailable'
  | 'release_mismatch'
  | 'unauthorized'
  | 'offline'
  | 'integrity';

export class ContractError extends Error {
  readonly code: ErrorCode;
  readonly field: string | undefined;

  constructor(code: ErrorCode, message: string, field?: string) {
    super(message);
    this.name = 'ContractError';
    this.code = code;
    this.field = field;
  }
}

// ---------------------------------------------------------------------------
// Validation. Deep links and cached payloads are untrusted input.
// ---------------------------------------------------------------------------

const SERVICE_DATE = /^\d{4}-\d{2}-\d{2}$/;
const IDENTIFIER = /^[A-Za-z0-9._:-]{1,128}$/;

export function isServiceDate(value: unknown): value is string {
  if (typeof value !== 'string' || !SERVICE_DATE.test(value)) return false;
  const [y, m, d] = value.split('-').map(Number) as [number, number, number];
  if (m < 1 || m > 12 || d < 1 || d > 31) return false;
  const probe = new Date(Date.UTC(y, m - 1, d));
  return probe.getUTCFullYear() === y && probe.getUTCMonth() === m - 1 && probe.getUTCDate() === d;
}

/** Identifiers are opaque, so this only guards shape and length, never meaning. */
export function isIdentifier(value: unknown): value is string {
  return typeof value === 'string' && IDENTIFIER.test(value);
}

export function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (LANGUAGES as readonly string[]).includes(value);
}

export function localized(value: LocalizedOrText | null | undefined, language: Language): string {
  if (!value) return '';
  if (typeof value === 'string') return value;
  return value[language] || value.en || value.el || '';
}
