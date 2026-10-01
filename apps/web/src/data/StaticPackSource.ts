/**
 * Reads the published, content-addressed release packs as static files.
 *
 * This is how the 1.0.0 site works on GitHub Pages: there is no server, only
 * `/data/manifest.json` and the immutable packs it names. Because pack names
 * carry their own digest, a pack can be cached forever and never needs
 * revalidating; only the manifest is ever re-fetched.
 *
 * A pack contains exactly what the matching `/v1/...` endpoint returns, so this
 * class deserialises responses rather than computing them. It holds no timetable
 * logic: the only selection it performs is picking journeys out of a day's result
 * list by stop id, and reading a stop's departures out of the same day's pack,
 * both of which return objects the generator wrote verbatim. That is what makes
 * it deep-equal to `HttpApiSource` for the same release, which is asserted by the
 * shared test suite.
 *
 * If an offline pack store is supplied, an installed pack is read from IndexedDB
 * and the network is not touched at all.
 */
import type {
  BoardingPoint,
  CoverageResult,
  JourneyDetail,
  JourneyResult,
  JourneysResult,
  JourneyStop,
  Manifest,
  ManifestFile,
  Meta,
  OperatorResult,
  OperatorsResult,
  Place,
  PlacesResult,
  SourcesResult,
  StopDeparture,
  StopResult,
} from './contract';
import { ContractError, isIdentifier, isServiceDate } from './contract';
import type { JourneyQuery, PublicDataSource } from './PublicDataSource';
import type { CoveragePack, JourneysPack, MetaPack, OperatorsPack, PlacesPack, SourcesPack, StopsPack } from './packShapes';
import { journeysPackName, serviceDateFromPackName } from './packShapes';
import { searchPlaces } from './packQuery';
import { boardingPointFromStop, composeJourneyId, parseJourneyId, scopedDetail, segmentSummary, tripIdOf } from './segments';
import { digestsMatch, sha256Hex } from '../lib/digest';
import { BRAND } from '../brand/brand';

export interface InstalledPackReader {
  /** Bytes of an installed pack, or null when it is not held locally. */
  readPack(path: string): Promise<ArrayBuffer | null>;
  /**
   * The manifest that was stored alongside the installed packs, if any.
   *
   * Only consulted when the network cannot be reached. It is the difference
   * between "this device holds the release" and "this device holds some files it
   * can no longer identify".
   */
  readManifest?(): Promise<Manifest | null>;
}

export interface StaticPackSourceOptions {
  /** Directory the manifest and packs live in. Must end with a slash. */
  baseUrl?: string;
  fetchImpl?: typeof fetch;
  packReader?: InstalledPackReader | null;
}

const DEFAULT_BASE = '/data/';
const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Minutes past local midnight as written in an ISO-8601 string that carries its own offset. */
function localMinutes(iso: string): number {
  return Number(iso.slice(11, 13)) * 60 + Number(iso.slice(14, 16));
}

export class StaticPackSource implements PublicDataSource {
  readonly kind = 'static' as const;
  readonly label = 'Static release packs';

  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private readonly packReader: InstalledPackReader | null;

  private manifestPromise: Promise<Manifest> | null = null;
  private readonly packCache = new Map<string, Promise<unknown>>();

  constructor(options: StaticPackSourceOptions = {}) {
    this.baseUrl = options.baseUrl ?? DEFAULT_BASE;
    this.fetchImpl = options.fetchImpl ?? ((...args) => globalThis.fetch(...args));
    this.packReader = options.packReader ?? null;
  }

  get origin(): string {
    return this.baseUrl;
  }

  packUrl(path: string): string {
    return `${this.baseUrl}${path}`;
  }

  /** Forget cached packs, for example after installing a newer release. */
  reset(): void {
    this.manifestPromise = null;
    this.packCache.clear();
  }

  // -- fetching -------------------------------------------------------------

  private async fetchBytes(path: string, expected: ManifestFile | null, signal?: AbortSignal): Promise<ArrayBuffer> {
    if (this.packReader) {
      const local = await this.packReader.readPack(path);
      if (local) {
        if (expected) await this.verify(local, expected, path);
        return local;
      }
    }
    let response: Response;
    try {
      response = await this.fetchImpl(this.packUrl(path), {
        // Content-addressed names make an immutable cache safe. The manifest is
        // the only mutable file, so it alone is revalidated.
        cache: expected ? 'force-cache' : 'no-cache',
        ...(signal ? { signal } : {}),
      });
    } catch (cause) {
      if ((cause as Error)?.name === 'AbortError') throw cause;
      throw new ContractError('offline', `Could not reach ${path}. The device looks offline.`);
    }
    if (response.status === 404) throw new ContractError('not_found', `${path} is not part of this release.`);
    if (!response.ok) throw new ContractError('unavailable', `${path} could not be read (HTTP ${response.status}).`);
    const bytes = await response.arrayBuffer();
    if (expected) await this.verify(bytes, expected, path);
    return bytes;
  }

  private async verify(bytes: ArrayBuffer, expected: ManifestFile, path: string): Promise<void> {
    if (bytes.byteLength !== expected.bytes) {
      throw new ContractError('integrity', `${path} is ${bytes.byteLength} bytes but the manifest declares ${expected.bytes}.`);
    }
    const digest = await sha256Hex(bytes);
    if (!digestsMatch(digest, expected.sha256)) {
      throw new ContractError('integrity', `${path} failed its SHA-256 check and was discarded.`);
    }
  }

  async manifest(signal?: AbortSignal): Promise<Manifest> {
    if (!this.manifestPromise) {
      this.manifestPromise = (async () => {
        let bytes: ArrayBuffer;
        try {
          bytes = await this.fetchBytes('manifest.json', null, signal);
        } catch (error) {
          // The manifest is the one mutable file, so it is always fetched. With
          // no connection, the copy stored when the packs were installed is used
          // instead: that is exactly the release those packs belong to.
          if (error instanceof ContractError && error.code === 'offline' && this.packReader?.readManifest) {
            const remembered = await this.packReader.readManifest();
            if (remembered) return remembered;
          }
          throw error;
        }
        let parsed: Manifest;
        try {
          parsed = JSON.parse(new TextDecoder().decode(bytes)) as Manifest;
        } catch {
          throw new ContractError('unavailable', 'The release manifest did not parse.');
        }
        if (!parsed?.releaseId || !parsed.files || typeof parsed.files !== 'object') {
          throw new ContractError('unavailable', 'The release manifest is missing required fields.');
        }
        if (parsed.contractVersion !== BRAND.contractVersion) {
          throw new ContractError(
            'release_mismatch',
            `This build speaks contract ${BRAND.contractVersion} but the release declares ${parsed.contractVersion}.`,
          );
        }
        // A release that does not carry the canonical pack names is refused
        // outright rather than half-read. Reading a legacy layout would mean
        // reimplementing a second pack shape, which is the thing the single
        // generator exists to prevent.
        for (const required of ['meta', 'places', 'operators', 'stops', 'coverage', 'sources'] as const) {
          if (!parsed.files[required]) {
            throw new ContractError(
              'release_mismatch',
              `This release does not carry the canonical "${required}" pack. Regenerate it with the release generator.`,
            );
          }
        }
        return parsed;
      })().catch((error: unknown) => {
        this.manifestPromise = null;
        throw error;
      });
    }
    return this.manifestPromise;
  }

  /** Reads and caches one logical pack, verified against the manifest digest. */
  private pack<T>(name: string, signal?: AbortSignal): Promise<T> {
    const cached = this.packCache.get(name);
    if (cached) return cached as Promise<T>;
    const promise = (async () => {
      const manifest = await this.manifest(signal);
      const file = manifest.files[name];
      if (!file) throw new ContractError('not_found', `This release carries no "${name}" pack.`);
      const bytes = await this.fetchBytes(file.path, file, signal);
      try {
        return JSON.parse(new TextDecoder().decode(bytes)) as T;
      } catch {
        throw new ContractError('integrity', `${file.path} verified but did not parse as JSON.`);
      }
    })().catch((error: unknown) => {
      this.packCache.delete(name);
      throw error;
    });
    this.packCache.set(name, promise);
    return promise as Promise<T>;
  }

  private async journeysFor(serviceDate: string, signal?: AbortSignal): Promise<JourneysPack | null> {
    const manifest = await this.manifest(signal);
    const name = journeysPackName(serviceDate);
    if (!manifest.files[name]) return null;
    return this.pack<JourneysPack>(name, signal);
  }

  private async serviceDates(signal?: AbortSignal): Promise<string[]> {
    const manifest = await this.manifest(signal);
    return Object.keys(manifest.files)
      .map(serviceDateFromPackName)
      .filter((value): value is string => value !== null)
      .sort();
  }

  // -- contract surface -----------------------------------------------------

  async meta(signal?: AbortSignal): Promise<Meta> {
    return this.pack<MetaPack>('meta', signal);
  }

  async places(query: string, limit: number, signal?: AbortSignal): Promise<PlacesResult> {
    const pack = await this.pack<PlacesPack>('places', signal);
    // `/v1/places` is a text query against the same item list, so the selection
    // happens here. The items themselves are returned untouched.
    const places = searchPlaces(pack.places, query, limit);
    return {
      contractVersion: pack.contractVersion,
      releaseId: pack.releaseId,
      publishedAt: pack.publishedAt,
      dataMode: pack.dataMode,
      places,
      total: places.length,
    };
  }

  async place(id: string, signal?: AbortSignal): Promise<StopResult> {
    const dates = await this.serviceDates(signal);
    return this.stop(id, dates[0] ?? '1970-01-01', signal);
  }

  async stop(id: string, serviceDate: string, signal?: AbortSignal): Promise<StopResult> {
    if (!isIdentifier(id)) throw new ContractError('invalid_request', 'That stop identifier is not usable.');
    if (!isServiceDate(serviceDate)) throw new ContractError('invalid_request', 'That service date is not usable.', 'date');
    const pack = await this.pack<StopsPack>('stops', signal);
    const entry = pack.stops[id];
    if (!entry) throw new ContractError('not_found', 'That place is not in this data release.');

    // The stops pack is written for one service date. For any other date the
    // departures are read out of that date's journeys pack instead: each one is
    // taken verbatim from a journey detail's own stop list, so no time, headsign
    // or sequence is computed here.
    const departures =
      pack.serviceDate === serviceDate ? entry.departures : await this.departuresFor(id, serviceDate, signal);

    return {
      contractVersion: pack.contractVersion,
      releaseId: pack.releaseId,
      publishedAt: pack.publishedAt,
      dataMode: pack.dataMode,
      ...entry,
      serviceDate,
      departures,
    };
  }

  private async departuresFor(stopId: string, serviceDate: string, signal?: AbortSignal): Promise<StopDeparture[]> {
    const journeys = await this.journeysFor(serviceDate, signal);
    if (!journeys) return [];
    const departures: StopDeparture[] = [];
    for (const detail of Object.values(journeys.journeys)) {
      const calling = detail.stops.find((stop) => stop.stopId === stopId);
      if (!calling) continue;
      const last = detail.stops[detail.stops.length - 1];
      if (!last) continue;
      departures.push({
        journeyId: composeJourneyId(tripIdOf(detail.id), stopId, last.stopId),
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
    return departures;
  }

  async journeys(query: JourneyQuery, signal?: AbortSignal): Promise<JourneysResult> {
    if (!isIdentifier(query.originId)) throw new ContractError('invalid_request', 'Origin is not a valid place identifier.', 'origin');
    if (!isIdentifier(query.destinationId)) {
      throw new ContractError('invalid_request', 'Destination is not a valid place identifier.', 'destination');
    }
    if (!isServiceDate(query.date)) throw new ContractError('invalid_request', 'Date is not a valid service date.', 'date');

    const [placesPack, coveragePack, dates] = await Promise.all([
      this.pack<PlacesPack>('places', signal),
      this.pack<CoveragePack>('coverage', signal),
      this.serviceDates(signal),
    ]);

    const envelope = {
      contractVersion: placesPack.contractVersion,
      releaseId: placesPack.releaseId,
      publishedAt: placesPack.publishedAt,
      dataMode: placesPack.dataMode,
    };
    const base = { ...envelope, query: { originId: query.originId, destinationId: query.destinationId, date: query.date } };

    const origin = placesPack.places.find((p) => p.id === query.originId);
    const destination = placesPack.places.find((p) => p.id === query.destinationId);
    if (!origin) throw new ContractError('not_found', 'That origin is not in this data release.', 'origin');
    if (!destination) throw new ContractError('not_found', 'That destination is not in this data release.', 'destination');

    const coverage =
      origin.coverage === 'not_covered' || destination.coverage === 'not_covered' ? 'not_covered' : coveragePack.coverage.state;

    if (origin.id === destination.id) {
      return { ...base, coverage, results: [], unavailableReason: 'origin_equals_destination' };
    }
    if (coverage === 'not_covered') {
      return { ...base, coverage: 'not_covered', results: [], unavailableReason: 'outside_coverage' };
    }
    /*
     * A journeys pack exists only for the dates the release names. For any other
     * date this source genuinely cannot answer, and it says so. Reporting
     * "no service on this date" here would be a different and much stronger
     * claim than the data supports: the service may well run, and a live API
     * would resolve it.
     */
    if (!dates.includes(query.date)) {
      return { ...base, coverage, results: [], unavailableReason: 'no_offline_data_for_date' };
    }

    const journeys = await this.journeysFor(query.date, signal);
    if (!journeys) return { ...base, coverage, results: [], unavailableReason: 'no_offline_data_for_date' };

    const expand = (place: Place): ReadonlySet<string> => {
      if (place.kind === 'stop') return new Set([place.id]);
      const ids = new Set<string>([place.id]);
      for (const candidate of placesPack.places) if (candidate.parentId === place.id) ids.add(candidate.id);
      return ids;
    };
    const originIds = expand(origin);
    const destinationIds = expand(destination);

    let results = journeys.results
      .map((summary) => {
        const detail = journeys.journeys[summary.id];
        return detail ? segmentSummary(summary, detail, originIds, destinationIds) : null;
      })
      .filter((journey): journey is NonNullable<typeof journey> => journey !== null);

    if (query.operatorIds && query.operatorIds.length > 0) {
      const allowed = new Set(query.operatorIds);
      results = results.filter((journey) => allowed.has(journey.operator.id));
    }
    if (query.accessible) {
      results = results.filter((journey) => {
        const detail = journeys.journeys[tripIdOf(journey.id)];
        return detail?.boardingPoint.stepFree === true && detail.boardingPoint.reviewState === 'published';
      });
    }
    const from = query.departFrom && HHMM.test(query.departFrom) ? localMinutes(`0000-00-00T${query.departFrom}:00`) : null;
    const to = query.departTo && HHMM.test(query.departTo) ? localMinutes(`0000-00-00T${query.departTo}:00`) : null;
    if (from !== null || to !== null) {
      results = results.filter((journey) => {
        const minutes = localMinutes(journey.departure.at);
        if (from !== null && minutes < from) return false;
        if (to !== null && minutes > to) return false;
        return true;
      });
    }

    if (results.length === 0) {
      const anyOnThisDate = journeys.results.some((summary) => {
        const detail = journeys.journeys[summary.id];
        return detail ? segmentSummary(summary, detail, originIds, destinationIds) !== null : false;
      });
      return { ...base, coverage, results: [], unavailableReason: anyOnThisDate ? 'no_service_on_date' : 'outside_coverage' };
    }

    results.sort((a, b) => a.departure.at.localeCompare(b.departure.at) || a.id.localeCompare(b.id));
    return { ...base, coverage, results, unavailableReason: null };
  }

  async journey(id: string, serviceDate: string, signal?: AbortSignal): Promise<JourneyResult> {
    const { tripId, boardStopId, alightStopId } = parseJourneyId(id);
    if (!isServiceDate(serviceDate)) throw new ContractError('invalid_request', 'That service date is not usable.', 'date');
    const journeys = await this.journeysFor(serviceDate, signal);
    const detail = journeys?.journeys[tripId];
    if (!journeys || !detail) {
      throw new ContractError('not_found', 'That journey does not run on that service date in this release.');
    }
    const boardingPointFor = await this.boardingPointResolver(detail, boardStopId, signal);
    return {
      contractVersion: journeys.contractVersion,
      releaseId: journeys.releaseId,
      publishedAt: journeys.publishedAt,
      dataMode: journeys.dataMode,
      journey: scopedDetail(detail, boardStopId, alightStopId, boardingPointFor),
    };
  }

  /**
   * A way to describe the boarding point of a leg that starts partway along the
   * run. The run's own boarding point only describes its first stop, so an
   * intermediate boarding reads its stop from the stops pack. A leg that boards at
   * the first stop needs nothing extra.
   */
  private async boardingPointResolver(
    detail: JourneyDetail,
    boardStopId: string | null,
    signal?: AbortSignal,
  ): Promise<((stop: JourneyStop) => BoardingPoint) | undefined> {
    const ordered = [...detail.stops].sort((a, b) => a.sequence - b.sequence);
    const first = ordered[0];
    if (!boardStopId || !first || boardStopId === first.stopId) return undefined;
    if (!ordered.some((stop) => stop.stopId === boardStopId)) return undefined;
    const stopsPack = await this.pack<StopsPack>('stops', signal);
    return (stop: JourneyStop) => boardingPointFromStop(stopsPack.stops[stop.stopId], stop);
  }

  async operators(signal?: AbortSignal): Promise<OperatorsResult> {
    const pack = await this.pack<OperatorsPack>('operators', signal);
    // Directory order is the generator's key order, which is stable because the
    // pack is content-addressed: identical bytes mean an identical order.
    return {
      contractVersion: pack.contractVersion,
      releaseId: pack.releaseId,
      publishedAt: pack.publishedAt,
      dataMode: pack.dataMode,
      operators: Object.values(pack.operators),
    };
  }

  async operator(id: string, signal?: AbortSignal): Promise<OperatorResult> {
    if (!isIdentifier(id)) throw new ContractError('invalid_request', 'That operator identifier is not usable.');
    const pack = await this.pack<OperatorsPack>('operators', signal);
    const operator = pack.operators[id];
    if (!operator) throw new ContractError('not_found', 'That operator is not in this data release.');
    return {
      contractVersion: pack.contractVersion,
      releaseId: pack.releaseId,
      publishedAt: pack.publishedAt,
      dataMode: pack.dataMode,
      operator,
    };
  }

  async coverage(signal?: AbortSignal): Promise<CoverageResult> {
    return this.pack<CoveragePack>('coverage', signal);
  }

  async sources(signal?: AbortSignal): Promise<SourcesResult> {
    return this.pack<SourcesPack>('sources', signal);
  }
}
