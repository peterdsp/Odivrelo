import type {
  CoverageResult,
  JourneyResult,
  JourneysResult,
  Manifest,
  Meta,
  OperatorResult,
  OperatorsResult,
  PlacesResult,
  SourcesResult,
  StopResult,
} from './contract';

export interface JourneyQuery {
  readonly originId: string;
  readonly destinationId: string;
  readonly date: string;
  /** Only journeys whose boarding point is reviewed and step-free. */
  readonly accessible?: boolean;
  readonly operatorIds?: readonly string[];
  /** Inclusive Athens wall-clock window, "HH:MM". */
  readonly departFrom?: string;
  readonly departTo?: string;
}

/**
 * The one door every screen goes through.
 *
 * StaticPackSource (content-addressed release packs on a static host) and
 * HttpApiSource (a live /v1 API) both implement it, they return identical
 * contract objects, and the same test suite is run against both.
 */
export interface PublicDataSource {
  /** Identifies the implementation in Settings and diagnostics. */
  readonly kind: 'static' | 'http';
  readonly label: string;
  /** Where the data is actually being read from. Shown to the user, never guessed. */
  readonly origin: string;

  meta(signal?: AbortSignal): Promise<Meta>;
  places(query: string, limit: number, signal?: AbortSignal): Promise<PlacesResult>;
  place(id: string, signal?: AbortSignal): Promise<StopResult>;
  stop(id: string, serviceDate: string, signal?: AbortSignal): Promise<StopResult>;
  journeys(query: JourneyQuery, signal?: AbortSignal): Promise<JourneysResult>;
  journey(id: string, serviceDate: string, signal?: AbortSignal): Promise<JourneyResult>;
  operators(signal?: AbortSignal): Promise<OperatorsResult>;
  operator(id: string, signal?: AbortSignal): Promise<OperatorResult>;
  coverage(signal?: AbortSignal): Promise<CoverageResult>;
  sources(signal?: AbortSignal): Promise<SourcesResult>;
  /**
   * The offline release manifest. An HTTP source mirrors it from
   * /v1/offline/manifest; a static source already has it.
   */
  manifest(signal?: AbortSignal): Promise<Manifest>;
  /** Absolute URL of a manifest-named file, for download and integrity checking. */
  packUrl(path: string): string;
}
