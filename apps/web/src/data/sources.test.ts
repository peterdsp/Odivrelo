import { describe, expect, it } from 'vitest';
import { StaticPackSource } from './StaticPackSource';
import { HttpApiSource } from './HttpApiSource';
import type { PublicDataSource } from './PublicDataSource';
import { ContractError } from './contract';
import {
  findWorkingQuery,
  makeApiFetch,
  makeStaticFetch,
  readManifest,
  readPack,
  releaseIsPresent,
  serviceDates,
} from '../test/release';
import type { JourneysPack, OperatorsPack, PlacesPack, StopsPack } from './packShapes';
import { journeysPackName } from './packShapes';

/**
 * Both data sources, held to one suite.
 *
 * This is the test that makes the architecture true rather than merely intended.
 * A pack contains what the endpoint returns, so for the same release the static
 * source and the HTTP source must produce deep-equal objects for every call. If
 * either one ever starts computing something the other does not, this fails.
 */

const hasRelease = releaseIsPresent();
const describeRelease = hasRelease ? describe : describe.skip;

function makeStatic(): StaticPackSource {
  return new StaticPackSource({ baseUrl: '/data/', fetchImpl: makeStaticFetch() });
}

function makeHttp(): HttpApiSource {
  return new HttpApiSource({ baseUrl: 'https://api.test.invalid', fetchImpl: makeApiFetch() });
}

describeRelease('the published release', () => {
  it('carries the canonical pack names and nothing from an older generator', () => {
    const manifest = readManifest();
    const names = Object.keys(manifest.files);
    for (const required of ['meta', 'coverage', 'sources', 'places', 'operators', 'stops']) {
      expect(names, `missing canonical pack "${required}"`).toContain(required);
    }
    expect(names.filter((n) => n.startsWith('journeys-')).length).toBeGreaterThan(0);
    // The legacy layout must not reappear: it would mean two pack shapes exist.
    expect(names).not.toContain('registry');
    expect(names).not.toContain('routes');
    expect(names.filter((n) => n.startsWith('trips-'))).toHaveLength(0);
  });

  it('names every pack with its own digest and declares the right length', () => {
    const manifest = readManifest();
    for (const [name, entry] of Object.entries(manifest.files)) {
      expect(entry.sha256, `${name} has no digest`).toMatch(/^[0-9a-f]{64}$/);
      expect(entry.path, `${name} is not content-addressed`).toContain(entry.sha256.slice(0, 16));
      expect(entry.bytes, `${name} declares no length`).toBeGreaterThan(0);
    }
  });
});

describeRelease.each([
  ['StaticPackSource', () => makeStatic() as PublicDataSource],
  ['HttpApiSource', () => makeHttp() as PublicDataSource],
])('%s satisfies the contract', (_name, build) => {
  it('reports the release metadata', async () => {
    const meta = await build().meta();
    expect(meta.contractVersion).toBe('1.0.0');
    expect(meta.releaseId).toMatch(/^[0-9a-f]{8,}$/);
    expect(['real', 'demo']).toContain(meta.dataMode);
    expect(meta.coverage).toBeTruthy();
  });

  it('lists places and finds them by name', async () => {
    const source = build();
    const all = await source.places('', 500);
    expect(all.places.length).toBeGreaterThan(0);
    const first = all.places[0]!;
    const found = await source.places(first.name.en.slice(0, 4), 20);
    expect(found.places.map((p) => p.id)).toContain(first.id);
  });

  it('lists operators and reads one by id', async () => {
    const source = build();
    const list = await source.operators();
    expect(list.operators.length).toBeGreaterThan(0);
    const one = await source.operator(list.operators[0]!.id);
    expect(one.operator.id).toBe(list.operators[0]!.id);
  });

  it('rejects an unknown operator with not_found', async () => {
    await expect(build().operator('no-such-operator')).rejects.toMatchObject({ code: 'not_found' });
  });

  it('rejects a malformed service date with invalid_request', async () => {
    const query = findWorkingQuery()!;
    await expect(
      build().journeys({ originId: query.originId, destinationId: query.destinationId, date: '2026-13-45' }),
    ).rejects.toMatchObject({ code: 'invalid_request' });
  });

  it('answers a journey search that has results', async () => {
    const query = findWorkingQuery()!;
    const result = await build().journeys(query);
    expect(result.unavailableReason).toBeNull();
    expect(result.results.length).toBeGreaterThan(0);
    for (const journey of result.results) {
      expect(journey.serviceDate).toBe(query.date);
      expect(Date.parse(journey.departure.at)).not.toBeNaN();
      expect(Date.parse(journey.arrival.at)).not.toBeNaN();
      expect(journey.freshness.state).toMatch(/^(fresh|aging|stale)$/);
    }
  });

  it('says origin_equals_destination rather than returning nothing', async () => {
    const query = findWorkingQuery()!;
    const result = await build().journeys({ ...query, destinationId: query.originId });
    expect(result.results).toHaveLength(0);
    expect(result.unavailableReason).toBe('origin_equals_destination');
  });

  it('returns no results for a date the release does not cover', async () => {
    const query = findWorkingQuery()!;
    const result = await build().journeys({ ...query, date: '2030-01-01' });
    expect(result.results).toHaveLength(0);
    expect(result.unavailableReason).not.toBeNull();
  });

  it('reads a journey detail with its stops, boarding point and provenance', async () => {
    const query = findWorkingQuery()!;
    const list = await build().journeys(query);
    const detail = (await build().journey(list.results[0]!.id, query.date)).journey;
    expect(detail.stops.length).toBeGreaterThan(0);
    expect(detail.boardingPoint.stopId).toBeTruthy();
    expect(Array.isArray(detail.provenance)).toBe(true);
    // The stop list must be in calling order.
    const sequences = detail.stops.map((s) => s.sequence);
    expect(sequences).toEqual([...sequences].sort((a, b) => a - b));
  });

  it('rejects a journey link that names no journey', async () => {
    const query = findWorkingQuery()!;
    await expect(build().journey('kt_definitely_not_real', query.date)).rejects.toMatchObject({ code: 'not_found' });
  });

  it('reads a stop with its departures for a service date', async () => {
    const manifest = readManifest();
    const stops = readPack<StopsPack>(manifest, 'stops');
    const id = Object.keys(stops.stops)[0]!;
    const date = serviceDates(manifest)[0]!;
    const stop = await build().stop(id, date);
    expect(stop.id).toBe(id);
    expect(stop.serviceDate).toBe(date);
    expect(Array.isArray(stop.departures)).toBe(true);
  });

  it('reads coverage and the source register', async () => {
    const source = build();
    const coverage = await source.coverage();
    expect(coverage.coverage.state).toMatch(/^(covered|partial|not_covered|demo)$/);
    const sources = await source.sources();
    expect(sources.sources.length).toBeGreaterThan(0);
    for (const entry of sources.sources) {
      expect(entry.rightsStatus).toMatch(/^(allowed|permission_pending|prohibited|unknown)$/);
      expect(entry.licence).toBeTruthy();
    }
  });
});

describeRelease('the two sources agree', () => {
  const stripEnvelope = <T extends object>(value: T) => {
    const { publishedAt: _publishedAt, ...rest } = value as T & { publishedAt?: string };
    return rest;
  };

  it('returns deep-equal meta', async () => {
    expect(await makeStatic().meta()).toEqual(await makeHttp().meta());
  });

  it('returns deep-equal coverage and sources', async () => {
    expect(await makeStatic().coverage()).toEqual(await makeHttp().coverage());
    expect(await makeStatic().sources()).toEqual(await makeHttp().sources());
  });

  it('returns deep-equal place lists, for an empty query and a text query', async () => {
    expect(await makeStatic().places('', 500)).toEqual(await makeHttp().places('', 500));
    const sample = (await makeStatic().places('', 500)).places[0]!;
    const needle = sample.name.en.slice(0, 3);
    expect(await makeStatic().places(needle, 10)).toEqual(await makeHttp().places(needle, 10));
  });

  it('returns deep-equal operator lists and details', async () => {
    const list = await makeStatic().operators();
    expect(list).toEqual(await makeHttp().operators());
    for (const operator of list.operators) {
      expect(await makeStatic().operator(operator.id)).toEqual(await makeHttp().operator(operator.id));
    }
  });

  it('returns deep-equal stops for every stop and every service date', async () => {
    const manifest = readManifest();
    const stops = readPack<StopsPack>(manifest, 'stops');
    for (const date of serviceDates(manifest)) {
      for (const id of Object.keys(stops.stops)) {
        expect(await makeStatic().stop(id, date), `${id} on ${date}`).toEqual(await makeHttp().stop(id, date));
      }
    }
  });

  it('returns deep-equal journey searches for every origin, destination and date in the release', async () => {
    const manifest = readManifest();
    const places = readPack<PlacesPack>(manifest, 'places');
    const dates = serviceDates(manifest);
    let compared = 0;
    for (const date of dates) {
      for (const origin of places.places) {
        for (const destination of places.places) {
          const query = { originId: origin.id, destinationId: destination.id, date };
          const [a, b] = await Promise.all([makeStatic().journeys(query), makeHttp().journeys(query)]);
          expect(a, `${origin.id} -> ${destination.id} on ${date}`).toEqual(b);
          compared += 1;
        }
      }
    }
    expect(compared).toBeGreaterThan(0);
  });

  it('returns deep-equal journey details for every journey in the release', async () => {
    const manifest = readManifest();
    let compared = 0;
    for (const date of serviceDates(manifest)) {
      const pack = readPack<JourneysPack>(manifest, journeysPackName(date));
      for (const id of Object.keys(pack.journeys)) {
        expect(await makeStatic().journey(id, date), `${id} on ${date}`).toEqual(await makeHttp().journey(id, date));
        compared += 1;
      }
    }
    expect(compared).toBeGreaterThan(0);
  });

  it('applies the accessible and operator filters identically', async () => {
    const manifest = readManifest();
    const operators = readPack<OperatorsPack>(manifest, 'operators');
    const query = findWorkingQuery()!;
    const operatorIds = [Object.keys(operators.operators)[0]!];
    for (const variant of [
      { ...query, accessible: true },
      { ...query, operatorIds },
      { ...query, departFrom: '00:00', departTo: '12:00' },
      { ...query, departFrom: '20:00' },
    ]) {
      expect(await makeStatic().journeys(variant), JSON.stringify(variant)).toEqual(await makeHttp().journeys(variant));
    }
  });

  it('agrees about the release envelope on every call', async () => {
    const staticMeta = await makeStatic().meta();
    const httpMeta = await makeHttp().meta();
    expect(stripEnvelope(staticMeta)).toEqual(stripEnvelope(httpMeta));
    expect(staticMeta.releaseId).toBe(httpMeta.releaseId);
  });
});

describeRelease('StaticPackSource integrity and offline behaviour', () => {
  it('verifies every pack against the digest the manifest declares', async () => {
    const source = new StaticPackSource({ baseUrl: '/data/', fetchImpl: makeStaticFetch() });
    await expect(source.meta()).resolves.toBeTruthy();
    await expect(source.places('', 10)).resolves.toBeTruthy();
  });

  it('rejects a corrupted pack instead of parsing it', async () => {
    const manifest = readManifest();
    const placesPath = manifest.files.places!.path;
    const source = new StaticPackSource({
      baseUrl: '/data/',
      fetchImpl: makeStaticFetch({ corruptPaths: new Set([placesPath]) }),
    });
    await expect(source.places('', 10)).rejects.toMatchObject({ code: 'integrity' });
  });

  it('reports offline rather than a parse error when nothing can be fetched', async () => {
    const source = new StaticPackSource({ baseUrl: '/data/', fetchImpl: makeStaticFetch({ offline: true }) });
    await expect(source.meta()).rejects.toMatchObject({ code: 'offline' });
  });

  it('refuses a release that does not carry the canonical packs', async () => {
    const legacy = {
      contractVersion: '1.0.0',
      product: 'Odivrelo',
      releaseId: 'deadbeefdeadbeef',
      publishedAt: '2026-09-30T00:00:00Z',
      files: {
        stops: { path: 'packs/stops-x.json', sha256: 'a'.repeat(64), bytes: 1, mediaType: 'application/json' },
        routes: { path: 'packs/routes-x.json', sha256: 'b'.repeat(64), bytes: 1, mediaType: 'application/json' },
      },
    };
    const source = new StaticPackSource({
      baseUrl: '/data/',
      fetchImpl: (async () => new Response(JSON.stringify(legacy), { status: 200 })) as typeof fetch,
    });
    await expect(source.manifest()).rejects.toMatchObject({ code: 'release_mismatch' });
  });

  it('refuses a release built for a different contract version', async () => {
    const future = { contractVersion: '2.0.0', releaseId: 'x', publishedAt: 'now', product: 'Odivrelo', files: {} };
    const source = new StaticPackSource({
      baseUrl: '/data/',
      fetchImpl: (async () => new Response(JSON.stringify(future), { status: 200 })) as typeof fetch,
    });
    await expect(source.manifest()).rejects.toMatchObject({ code: 'release_mismatch' });
  });

  it('reads an installed pack from storage and never touches the network', async () => {
    const manifest = readManifest();
    const log: string[] = [];
    const { readPackBytes } = await import('../test/release');
    const source = new StaticPackSource({
      baseUrl: '/data/',
      fetchImpl: makeStaticFetch({ log }),
      packReader: {
        async readPack(path: string) {
          if (path === 'manifest.json') return null;
          const bytes = readPackBytes(path);
          return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
        },
      },
    });
    await source.places('', 10);
    // Only the manifest, which is the one mutable file, came off the network.
    expect(log).toEqual(['manifest.json']);
    expect(log).not.toContain(manifest.files.places!.path);
  });
});

describe('HttpApiSource error mapping', () => {
  it('turns an API error envelope into a ContractError with the same code', async () => {
    const source = new HttpApiSource({
      baseUrl: 'https://api.test.invalid',
      fetchImpl: (async () =>
        new Response(JSON.stringify({ error: { code: 'unavailable', message: 'Down for maintenance.' } }), {
          status: 503,
        })) as typeof fetch,
    });
    await expect(source.meta()).rejects.toMatchObject({ code: 'unavailable', message: 'Down for maintenance.' });
  });

  it('reports offline when the request itself fails', async () => {
    const source = new HttpApiSource({
      baseUrl: 'https://api.test.invalid',
      fetchImpl: (async () => {
        throw new TypeError('Failed to fetch');
      }) as typeof fetch,
    });
    const error = await source.meta().catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ContractError);
    expect((error as ContractError).code).toBe('offline');
  });

  it('does not dress a non-JSON error body up as something it understands', async () => {
    const source = new HttpApiSource({
      baseUrl: 'https://api.test.invalid',
      fetchImpl: (async () => new Response('<html>502</html>', { status: 502 })) as typeof fetch,
    });
    await expect(source.meta()).rejects.toMatchObject({ code: 'unavailable' });
  });
});

describeRelease('an unpacked date is not the same as no service', () => {
  /*
   * A journeys pack is materialised only for the dates the data names. A
   * recurring service on any other date is resolved by the live API on request
   * and is deliberately absent from the packs.
   *
   * So the two sources must give different, and individually truthful, answers
   * for an unpacked date: the static source says it cannot answer offline, and
   * only a live source is entitled to say that nothing runs. Conflating them
   * would put a certainty in front of a passenger that the data does not support.
   */
  const unpacked = '2030-01-01';

  it('the static source says it has no offline data, not that nothing runs', async () => {
    const query = findWorkingQuery()!;
    const result = await makeStatic().journeys({ ...query, date: unpacked });
    expect(result.unavailableReason).toBe('no_offline_data_for_date');
    expect(result.unavailableReason).not.toBe('no_service_on_date');
    expect(result.results).toHaveLength(0);
  });

  it('a date that is packed but genuinely empty is reported as no service', async () => {
    const manifest = readManifest();
    const places = readPack<PlacesPack>(manifest, 'places');
    const dates = serviceDates(manifest);
    // Find a packed date and a place pair that the day's pack really has nothing for.
    let asserted = false;
    for (const date of dates) {
      const pack = readPack<JourneysPack>(manifest, journeysPackName(date));
      for (const origin of places.places) {
        for (const destination of places.places) {
          if (origin.id === destination.id) continue;
          const anyMatch = pack.results.some(
            (journey) => journey.departure.stopId === origin.id && journey.arrival.stopId === destination.id,
          );
          if (anyMatch) continue;
          const result = await makeStatic().journeys({ originId: origin.id, destinationId: destination.id, date });
          // Whatever the reason is, it is never the offline one: the pack is here.
          expect(result.unavailableReason).not.toBe('no_offline_data_for_date');
          expect(['no_service_on_date', 'outside_coverage']).toContain(result.unavailableReason);
          asserted = true;
          break;
        }
        if (asserted) break;
      }
      if (asserted) break;
    }
    expect(asserted, 'the release had no empty origin/destination pair to check').toBe(true);
  });

  it('the two sources still agree for every date the release does pack', async () => {
    const manifest = readManifest();
    const places = readPack<PlacesPack>(manifest, 'places');
    for (const date of serviceDates(manifest)) {
      for (const origin of places.places.slice(0, 4)) {
        for (const destination of places.places.slice(0, 4)) {
          const query = { originId: origin.id, destinationId: destination.id, date };
          expect(await makeStatic().journeys(query), JSON.stringify(query)).toEqual(await makeHttp().journeys(query));
        }
      }
    }
  });
});
