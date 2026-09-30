/**
 * Talks to the `/v1/...` REST surface described in the contract.
 *
 * It is selected only when VITE_API_BASE_URL is configured. The deployed 1.0.0
 * site has no server, so it ships with the static source; this implementation
 * exists so that the moment an API is available nothing above the data layer
 * has to change, and it is held to exactly the same test suite.
 */
import type {
  CoverageResult,
  ErrorCode,
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
import { ContractError, isIdentifier, isServiceDate } from './contract';
import type { JourneyQuery, PublicDataSource } from './PublicDataSource';

export interface HttpApiSourceOptions {
  baseUrl: string;
  fetchImpl?: typeof fetch;
  /** Sent as Accept-Language so the API can localise its own messages. */
  language?: () => string;
}

const KNOWN_CODES: readonly ErrorCode[] = [
  'not_found',
  'invalid_request',
  'unavailable',
  'release_mismatch',
  'unauthorized',
];

export class HttpApiSource implements PublicDataSource {
  readonly kind = 'http' as const;
  readonly label = 'Live API';

  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private readonly language: () => string;

  constructor(options: HttpApiSourceOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.fetchImpl = options.fetchImpl ?? ((...args) => globalThis.fetch(...args));
    this.language = options.language ?? (() => 'el');
  }

  get origin(): string {
    return this.baseUrl;
  }

  packUrl(path: string): string {
    return `${this.baseUrl}/v1/offline/${path}`;
  }

  private async request<T>(path: string, signal?: AbortSignal): Promise<T> {
    let response: Response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${path}`, {
        headers: { Accept: 'application/json', 'Accept-Language': this.language() },
        ...(signal ? { signal } : {}),
      });
    } catch (cause) {
      if ((cause as Error)?.name === 'AbortError') throw cause;
      throw new ContractError('offline', `Could not reach ${this.baseUrl}. The device looks offline.`);
    }
    if (!response.ok) {
      let code: ErrorCode = response.status === 404 ? 'not_found' : response.status === 400 ? 'invalid_request' : 'unavailable';
      let message = `The API answered HTTP ${response.status}.`;
      let field: string | undefined;
      try {
        const body = (await response.json()) as { error?: { code?: string; message?: string; field?: string } };
        if (body?.error) {
          if (body.error.code && (KNOWN_CODES as readonly string[]).includes(body.error.code)) {
            code = body.error.code as ErrorCode;
          }
          if (body.error.message) message = body.error.message;
          field = body.error.field;
        }
      } catch {
        // A non-JSON error body is still an error; the status code stands.
      }
      throw new ContractError(code, message, field);
    }
    try {
      return (await response.json()) as T;
    } catch {
      throw new ContractError('unavailable', 'The API returned a body that did not parse as JSON.');
    }
  }

  async meta(signal?: AbortSignal): Promise<Meta> {
    return this.request<Meta>('/v1/meta', signal);
  }

  async places(query: string, limit: number, signal?: AbortSignal): Promise<PlacesResult> {
    const params = new URLSearchParams({ q: query, limit: String(limit), lang: this.language() });
    return this.request<PlacesResult>(`/v1/places?${params.toString()}`, signal);
  }

  async place(id: string, signal?: AbortSignal): Promise<StopResult> {
    if (!isIdentifier(id)) throw new ContractError('invalid_request', 'That place identifier is not usable.');
    return this.request<StopResult>(`/v1/stops/${encodeURIComponent(id)}?lang=${this.language()}`, signal);
  }

  async stop(id: string, serviceDate: string, signal?: AbortSignal): Promise<StopResult> {
    if (!isIdentifier(id)) throw new ContractError('invalid_request', 'That stop identifier is not usable.');
    if (!isServiceDate(serviceDate)) throw new ContractError('invalid_request', 'That service date is not usable.', 'date');
    const params = new URLSearchParams({ date: serviceDate, lang: this.language() });
    return this.request<StopResult>(`/v1/stops/${encodeURIComponent(id)}?${params.toString()}`, signal);
  }

  async journeys(query: JourneyQuery, signal?: AbortSignal): Promise<JourneysResult> {
    if (!isIdentifier(query.originId)) throw new ContractError('invalid_request', 'Origin is not a valid place identifier.', 'origin');
    if (!isIdentifier(query.destinationId)) throw new ContractError('invalid_request', 'Destination is not a valid place identifier.', 'destination');
    if (!isServiceDate(query.date)) throw new ContractError('invalid_request', 'Date is not a valid service date.', 'date');
    const params = new URLSearchParams({
      origin: query.originId,
      destination: query.destinationId,
      date: query.date,
      lang: this.language(),
    });
    if (query.accessible) params.set('accessible', 'true');
    if (query.operatorIds?.length) params.set('operators', query.operatorIds.join(','));
    if (query.departFrom) params.set('departFrom', query.departFrom);
    if (query.departTo) params.set('departTo', query.departTo);
    return this.request<JourneysResult>(`/v1/journeys?${params.toString()}`, signal);
  }

  async journey(id: string, serviceDate: string, signal?: AbortSignal): Promise<JourneyResult> {
    if (!isServiceDate(serviceDate)) throw new ContractError('invalid_request', 'That service date is not usable.', 'date');
    const params = new URLSearchParams({ date: serviceDate, lang: this.language() });
    return this.request<JourneyResult>(`/v1/journeys/${encodeURIComponent(id)}?${params.toString()}`, signal);
  }

  async operators(signal?: AbortSignal): Promise<OperatorsResult> {
    return this.request<OperatorsResult>(`/v1/operators?lang=${this.language()}`, signal);
  }

  async operator(id: string, signal?: AbortSignal): Promise<OperatorResult> {
    if (!isIdentifier(id)) throw new ContractError('invalid_request', 'That operator identifier is not usable.');
    return this.request<OperatorResult>(`/v1/operators/${encodeURIComponent(id)}?lang=${this.language()}`, signal);
  }

  async coverage(signal?: AbortSignal): Promise<CoverageResult> {
    return this.request<CoverageResult>('/v1/coverage', signal);
  }

  async sources(signal?: AbortSignal): Promise<SourcesResult> {
    return this.request<SourcesResult>('/v1/sources', signal);
  }

  async manifest(signal?: AbortSignal): Promise<Manifest> {
    return this.request<Manifest>('/v1/offline/manifest', signal);
  }
}
