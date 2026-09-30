import type { JourneyQuery } from '../data/PublicDataSource';
import { isIdentifier, isServiceDate } from '../data/contract';
import { parseArrayOf, parseString, readPref, removePref, writePref } from '../lib/prefs';

/**
 * The URL is the single source of truth for a search.
 *
 * Origin, destination, service date, every filter, the map/list intent and the
 * selected journey all live in the query string. That is what makes the whole set
 * of state-preservation requirements fall out for free: a resize, a rotation, a
 * reload, a background and foreground, and the back button all reconstruct the
 * same screen from the same URL, and a link is shareable and deep-linkable
 * without a second mechanism.
 *
 * Every value is validated on the way in. A query string is untrusted input.
 */

export interface SearchState {
  readonly originId: string | null;
  readonly destinationId: string | null;
  readonly date: string | null;
  readonly operatorIds: readonly string[];
  readonly accessible: boolean;
  readonly departFrom: string | null;
  readonly departTo: string | null;
  readonly view: 'list' | 'map';
  /** Selected journey, for the two-pane layout and for a shareable link. */
  readonly journeyId: string | null;
}

export const EMPTY_SEARCH: SearchState = {
  originId: null,
  destinationId: null,
  date: null,
  operatorIds: [],
  accessible: false,
  departFrom: null,
  departTo: null,
  view: 'list',
  journeyId: null,
};

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

function id(value: string | null): string | null {
  return value && isIdentifier(value) ? value : null;
}

function hhmm(value: string | null): string | null {
  return value && HHMM.test(value) ? value : null;
}

export function readSearchState(params: URLSearchParams): SearchState {
  const rawOperators = params.get('operators');
  return {
    originId: id(params.get('origin')),
    destinationId: id(params.get('destination')),
    date: (() => {
      const value = params.get('date');
      return value && isServiceDate(value) ? value : null;
    })(),
    operatorIds: rawOperators
      ? rawOperators
          .split(',')
          .map((v) => v.trim())
          .filter((v) => isIdentifier(v))
          .slice(0, 20)
      : [],
    accessible: params.get('accessible') === '1',
    departFrom: hhmm(params.get('from')),
    departTo: hhmm(params.get('to')),
    view: params.get('view') === 'map' ? 'map' : 'list',
    journeyId: id(params.get('journey')),
  };
}

export function writeSearchState(state: SearchState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.originId) params.set('origin', state.originId);
  if (state.destinationId) params.set('destination', state.destinationId);
  if (state.date) params.set('date', state.date);
  if (state.operatorIds.length > 0) params.set('operators', state.operatorIds.join(','));
  if (state.accessible) params.set('accessible', '1');
  if (state.departFrom) params.set('from', state.departFrom);
  if (state.departTo) params.set('to', state.departTo);
  if (state.view === 'map') params.set('view', 'map');
  if (state.journeyId) params.set('journey', state.journeyId);
  return params;
}

export function isRunnable(state: SearchState): boolean {
  return state.originId !== null && state.destinationId !== null && state.date !== null;
}

export function toQuery(state: SearchState): JourneyQuery | null {
  if (!isRunnable(state)) return null;
  const query: JourneyQuery = {
    originId: state.originId as string,
    destinationId: state.destinationId as string,
    date: state.date as string,
    ...(state.accessible ? { accessible: true } : {}),
    ...(state.operatorIds.length > 0 ? { operatorIds: state.operatorIds } : {}),
    ...(state.departFrom ? { departFrom: state.departFrom } : {}),
    ...(state.departTo ? { departTo: state.departTo } : {}),
  };
  return query;
}

export function activeFilterCount(state: SearchState): number {
  return (
    (state.accessible ? 1 : 0) +
    (state.operatorIds.length > 0 ? 1 : 0) +
    (state.departFrom ? 1 : 0) +
    (state.departTo ? 1 : 0)
  );
}

/**
 * Two searches are the same when they would produce the same request. Used so a
 * geometry change, a re-render or a restored back-stack entry never re-issues an
 * identical search.
 */
export function sameQuery(a: JourneyQuery | null, b: JourneyQuery | null): boolean {
  if (a === null || b === null) return a === b;
  return (
    a.originId === b.originId &&
    a.destinationId === b.destinationId &&
    a.date === b.date &&
    (a.accessible ?? false) === (b.accessible ?? false) &&
    (a.departFrom ?? '') === (b.departFrom ?? '') &&
    (a.departTo ?? '') === (b.departTo ?? '') &&
    (a.operatorIds ?? []).join(',') === (b.operatorIds ?? []).join(',')
  );
}

// ---------------------------------------------------------------------------
// Recent searches
// ---------------------------------------------------------------------------

const RECENT_PREF = 'recentSearches';
const RECENT_LIMIT = 6;

export interface RecentSearch {
  readonly originId: string;
  readonly destinationId: string;
  readonly originLabel: string;
  readonly destinationLabel: string;
  readonly date: string;
  readonly at: string;
}

const parseRecent = (raw: unknown): RecentSearch | null => {
  if (!raw || typeof raw !== 'object') return null;
  const value = raw as Record<string, unknown>;
  const originId = parseString(value.originId);
  const destinationId = parseString(value.destinationId);
  const date = parseString(value.date);
  const originLabel = parseString(value.originLabel);
  const destinationLabel = parseString(value.destinationLabel);
  const at = parseString(value.at);
  if (!originId || !destinationId || !date || !originLabel || !destinationLabel || !at) return null;
  if (!isIdentifier(originId) || !isIdentifier(destinationId) || !isServiceDate(date)) return null;
  return { originId, destinationId, originLabel, destinationLabel, date, at };
};

export function readRecentSearches(): RecentSearch[] {
  return readPref<RecentSearch[]>(RECENT_PREF, [], parseArrayOf(parseRecent, RECENT_LIMIT));
}

export function rememberSearch(entry: RecentSearch): RecentSearch[] {
  const existing = readRecentSearches().filter(
    (r) => !(r.originId === entry.originId && r.destinationId === entry.destinationId && r.date === entry.date),
  );
  const next = [entry, ...existing].slice(0, RECENT_LIMIT);
  writePref(RECENT_PREF, next);
  return next;
}

export function clearRecentSearches(): void {
  removePref(RECENT_PREF);
}

// ---------------------------------------------------------------------------
// Last-used filter, kept as a per-viewer convenience
// ---------------------------------------------------------------------------

const FILTER_PREF = 'lastFilter';

export interface StoredFilter {
  readonly accessible: boolean;
  readonly operatorIds: readonly string[];
  readonly departFrom: string | null;
  readonly departTo: string | null;
}

export function readStoredFilter(): StoredFilter | null {
  return readPref<StoredFilter | null>(FILTER_PREF, null, (raw) => {
    if (!raw || typeof raw !== 'object') return null;
    const value = raw as Record<string, unknown>;
    return {
      accessible: value.accessible === true,
      operatorIds: Array.isArray(value.operatorIds)
        ? value.operatorIds.filter((v): v is string => typeof v === 'string' && isIdentifier(v)).slice(0, 20)
        : [],
      departFrom: hhmm(typeof value.departFrom === 'string' ? value.departFrom : null),
      departTo: hhmm(typeof value.departTo === 'string' ? value.departTo : null),
    };
  });
}

export function writeStoredFilter(filter: StoredFilter): void {
  writePref(FILTER_PREF, filter);
}
