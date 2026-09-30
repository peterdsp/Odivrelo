import { beforeEach, describe, expect, it } from 'vitest';
import {
  activeFilterCount,
  clearRecentSearches,
  EMPTY_SEARCH,
  isRunnable,
  readRecentSearches,
  readSearchState,
  readStoredFilter,
  rememberSearch,
  sameQuery,
  toQuery,
  writeSearchState,
  writeStoredFilter,
  type SearchState,
} from './searchParams';

/**
 * Search state lives in the URL.
 *
 * That single decision is what makes the state-preservation requirements hold: a
 * resize, a rotation, a reload, a background and foreground, and the back button
 * all reconstruct the same screen from the same string. These tests pin the
 * round trip, the validation of untrusted query strings, and the rule that an
 * identical search is never re-issued because the window changed shape.
 */

const FULL: SearchState = {
  originId: 'ks_origin',
  destinationId: 'ks_destination',
  date: '2026-10-02',
  operatorIds: ['demo-aloria-coach'],
  accessible: true,
  departFrom: '08:00',
  departTo: '20:30',
  view: 'map',
  journeyId: 'kt_selected',
};

describe('the search state round trip', () => {
  it('survives being written to a query string and read back', () => {
    expect(readSearchState(writeSearchState(FULL))).toEqual(FULL);
  });

  it('survives it again, so the encoding is stable', () => {
    const once = writeSearchState(FULL).toString();
    const twice = writeSearchState(readSearchState(new URLSearchParams(once))).toString();
    expect(twice).toBe(once);
  });

  it('omits everything that is at its default, so a simple search has a simple link', () => {
    const simple: SearchState = {
      ...EMPTY_SEARCH,
      originId: 'ks_a',
      destinationId: 'ks_b',
      date: '2026-10-02',
    };
    expect(writeSearchState(simple).toString()).toBe('origin=ks_a&destination=ks_b&date=2026-10-02');
  });

  it('reads an empty query string as the empty state rather than throwing', () => {
    expect(readSearchState(new URLSearchParams())).toEqual(EMPTY_SEARCH);
  });
});

describe('a query string is untrusted input', () => {
  it('rejects an identifier that is not identifier-shaped', () => {
    const state = readSearchState(new URLSearchParams('origin=<script>&destination=ks_b'));
    expect(state.originId).toBeNull();
    expect(state.destinationId).toBe('ks_b');
  });

  it('rejects a date that is not a real calendar date', () => {
    expect(readSearchState(new URLSearchParams('date=2026-02-30')).date).toBeNull();
    expect(readSearchState(new URLSearchParams('date=yesterday')).date).toBeNull();
    expect(readSearchState(new URLSearchParams('date=2026-10-02')).date).toBe('2026-10-02');
  });

  it('rejects a departure window that is not a real time', () => {
    expect(readSearchState(new URLSearchParams('from=25:00')).departFrom).toBeNull();
    expect(readSearchState(new URLSearchParams('from=8:00')).departFrom).toBeNull();
    expect(readSearchState(new URLSearchParams('from=08:00')).departFrom).toBe('08:00');
  });

  it('bounds the operator list so a hostile link cannot make a huge request', () => {
    const many = Array.from({ length: 200 }, (_, i) => `op-${i}`).join(',');
    expect(readSearchState(new URLSearchParams(`operators=${many}`)).operatorIds).toHaveLength(20);
  });

  it('treats any view other than map as the list', () => {
    expect(readSearchState(new URLSearchParams('view=map')).view).toBe('map');
    expect(readSearchState(new URLSearchParams('view=hologram')).view).toBe('list');
  });
});

describe('building a data-source query', () => {
  it('is runnable only with an origin, a destination and a date', () => {
    expect(isRunnable(EMPTY_SEARCH)).toBe(false);
    expect(isRunnable({ ...EMPTY_SEARCH, originId: 'a' })).toBe(false);
    expect(isRunnable(FULL)).toBe(true);
    expect(toQuery(EMPTY_SEARCH)).toBeNull();
  });

  it('carries every filter through', () => {
    expect(toQuery(FULL)).toEqual({
      originId: 'ks_origin',
      destinationId: 'ks_destination',
      date: '2026-10-02',
      accessible: true,
      operatorIds: ['demo-aloria-coach'],
      departFrom: '08:00',
      departTo: '20:30',
    });
  });

  it('leaves absent filters out rather than sending empty values', () => {
    const query = toQuery({ ...EMPTY_SEARCH, originId: 'a', destinationId: 'b', date: '2026-10-02' })!;
    expect(query).toEqual({ originId: 'a', destinationId: 'b', date: '2026-10-02' });
    expect('accessible' in query).toBe(false);
    expect('operatorIds' in query).toBe(false);
  });

  it('counts the filters that are actually applied', () => {
    expect(activeFilterCount(EMPTY_SEARCH)).toBe(0);
    expect(activeFilterCount(FULL)).toBe(4);
    expect(activeFilterCount({ ...EMPTY_SEARCH, accessible: true })).toBe(1);
  });
});

describe('an identical search is never re-issued', () => {
  it('treats two structurally equal queries as the same', () => {
    expect(sameQuery(toQuery(FULL), toQuery({ ...FULL }))).toBe(true);
  });

  it('ignores changes that cannot affect the request', () => {
    // The selected journey and the map/list intent are presentation, not query.
    expect(sameQuery(toQuery(FULL), toQuery({ ...FULL, journeyId: 'kt_other', view: 'list' }))).toBe(true);
  });

  it('notices a change that does affect the request', () => {
    expect(sameQuery(toQuery(FULL), toQuery({ ...FULL, date: '2026-10-03' }))).toBe(false);
    expect(sameQuery(toQuery(FULL), toQuery({ ...FULL, accessible: false }))).toBe(false);
    expect(sameQuery(toQuery(FULL), toQuery({ ...FULL, operatorIds: [] }))).toBe(false);
    expect(sameQuery(toQuery(FULL), toQuery({ ...FULL, departFrom: '09:00' }))).toBe(false);
  });

  it('handles a null on either side', () => {
    expect(sameQuery(null, null)).toBe(true);
    expect(sameQuery(toQuery(FULL), null)).toBe(false);
    expect(sameQuery(null, toQuery(FULL))).toBe(false);
  });
});

describe('state preserved across a simulated geometry change', () => {
  /*
   * A resize, a rotation or a fold changes nothing about the URL, so the state
   * reconstructed from it is identical. This is the mechanism the brief's
   * preservation requirement rests on, tested at the level where it actually
   * lives rather than by resizing a browser.
   */
  it('reconstructs the same state and the same request after any geometry change', () => {
    const url = writeSearchState(FULL).toString();
    for (const _geometry of ['portrait', 'landscape', 'folded', 'reloaded', 'restored from the back stack']) {
      const restored = readSearchState(new URLSearchParams(url));
      expect(restored).toEqual(FULL);
      expect(sameQuery(toQuery(restored), toQuery(FULL))).toBe(true);
      // The selected journey, the service date and the map intent all survive.
      expect(restored.journeyId).toBe('kt_selected');
      expect(restored.date).toBe('2026-10-02');
      expect(restored.view).toBe('map');
    }
  });
});

describe('recent searches and the remembered filter', () => {
  beforeEach(() => {
    clearRecentSearches();
    globalThis.localStorage.clear();
  });

  const entry = (date: string) => ({
    originId: 'ks_a',
    destinationId: 'ks_b',
    originLabel: 'A',
    destinationLabel: 'B',
    date,
    at: '2026-09-30T10:00:00.000Z',
  });

  it('remembers a search and puts the newest first', () => {
    rememberSearch(entry('2026-10-02'));
    const list = rememberSearch(entry('2026-10-03'));
    expect(list[0]!.date).toBe('2026-10-03');
    expect(list).toHaveLength(2);
    expect(readRecentSearches()).toHaveLength(2);
  });

  it('does not duplicate an identical search', () => {
    rememberSearch(entry('2026-10-02'));
    const list = rememberSearch(entry('2026-10-02'));
    expect(list).toHaveLength(1);
  });

  it('keeps at most six', () => {
    for (let day = 1; day <= 10; day += 1) {
      rememberSearch(entry(`2026-10-${String(day).padStart(2, '0')}`));
    }
    expect(readRecentSearches()).toHaveLength(6);
  });

  it('drops a stored entry that no longer validates', () => {
    globalThis.localStorage.setItem(
      'poravia.v1.recentSearches',
      JSON.stringify([entry('2026-02-30'), entry('2026-10-02')]),
    );
    const list = readRecentSearches();
    expect(list).toHaveLength(1);
    expect(list[0]!.date).toBe('2026-10-02');
  });

  it('round-trips the remembered filter and validates it on the way back', () => {
    writeStoredFilter({ accessible: true, operatorIds: ['op-a'], departFrom: '08:00', departTo: null });
    expect(readStoredFilter()).toEqual({
      accessible: true,
      operatorIds: ['op-a'],
      departFrom: '08:00',
      departTo: null,
    });

    globalThis.localStorage.setItem(
      'poravia.v1.lastFilter',
      JSON.stringify({ accessible: 'yes', operatorIds: 'not an array', departFrom: '99:99' }),
    );
    expect(readStoredFilter()).toEqual({ accessible: false, operatorIds: [], departFrom: null, departTo: null });
  });
});
