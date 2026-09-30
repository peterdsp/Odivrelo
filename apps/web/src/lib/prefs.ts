/**
 * Per-viewer UI preferences only: language, theme, reduced motion, the last
 * filter and recent searches.
 *
 * Nothing here is a record the passenger would miss if the browser threw it
 * away, and nothing here is personal. Saved trips, offline packs and the travel
 * wallet live in IndexedDB instead.
 *
 * Every access is wrapped: in a private window, with site data blocked, or when
 * the quota is exhausted, `localStorage` throws on read as well as on write, and
 * the app has to render correctly anyway.
 */
import { BRAND } from '../brand/brand';

const NAMESPACE = `${BRAND.slug}.v1`;

export function prefsKey(name: string): string {
  return `${NAMESPACE}.${name}`;
}

/**
 * Whether this browser is genuinely offering storage.
 *
 * Optional chaining alone is not enough: with `localStorage` absent,
 * `globalThis.localStorage?.setItem(...)` quietly evaluates to `undefined` and a
 * caller that treated that as success would be reporting a write that never
 * happened. Every entry point below therefore checks that the store exists before
 * claiming anything.
 */
function store(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    // Accessing the property itself throws when site data is blocked.
    return null;
  }
}

export function storageAvailable(): boolean {
  const target = store();
  if (!target) return false;
  try {
    const probe = `${NAMESPACE}.probe`;
    target.setItem(probe, '1');
    target.removeItem(probe);
    return true;
  } catch {
    return false;
  }
}

export function readPref<T>(name: string, fallback: T, parse: (raw: unknown) => T | null): T {
  const target = store();
  if (!target) return fallback;
  try {
    const raw = target.getItem(prefsKey(name));
    if (raw === null || raw === undefined) return fallback;
    const parsed = parse(JSON.parse(raw) as unknown);
    return parsed === null ? fallback : parsed;
  } catch {
    return fallback;
  }
}

/** Returns false when nothing was actually stored, so a caller is never told a lie. */
export function writePref(name: string, value: unknown): boolean {
  const target = store();
  if (!target) return false;
  try {
    target.setItem(prefsKey(name), JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

export function removePref(name: string): void {
  try {
    store()?.removeItem(prefsKey(name));
  } catch {
    // Nothing to do: the preference was never persisted.
  }
}

export function clearAllPrefs(): void {
  try {
    const target = store();
    if (!target) return;
    const doomed: string[] = [];
    for (let i = 0; i < target.length; i += 1) {
      const key = target.key(i);
      if (key && key.startsWith(`${NAMESPACE}.`)) doomed.push(key);
    }
    for (const key of doomed) target.removeItem(key);
  } catch {
    // Same as above.
  }
}

// ---------------------------------------------------------------------------
// Typed parsers for the preferences the app actually keeps
// ---------------------------------------------------------------------------

export const parseString = (raw: unknown): string | null => (typeof raw === 'string' ? raw : null);
export const parseBoolean = (raw: unknown): boolean | null => (typeof raw === 'boolean' ? raw : null);
export const parseOneOf =
  <T extends string>(allowed: readonly T[]) =>
  (raw: unknown): T | null =>
    typeof raw === 'string' && (allowed as readonly string[]).includes(raw) ? (raw as T) : null;
export const parseArrayOf =
  <T>(item: (raw: unknown) => T | null, max = 50) =>
  (raw: unknown): T[] | null => {
    if (!Array.isArray(raw)) return null;
    const out: T[] = [];
    for (const entry of raw.slice(0, max)) {
      const parsed = item(entry);
      if (parsed !== null) out.push(parsed);
    }
    return out;
  };
