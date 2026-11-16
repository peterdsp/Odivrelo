import type { Language } from '../data/contract';
import { LANGUAGES } from '../data/contract';
import { el } from './el';
import { en, type Catalogue, type MessageKey } from './en';
import { sq } from './sq';

export type { Catalogue, MessageKey };

export const catalogues: Readonly<Record<Language, Catalogue>> = { el, en, sq };

export const messageKeys = Object.keys(en) as MessageKey[];

/** Keys that intentionally hold the same text in more than one language. */
export const SHARED_LITERAL_KEYS: ReadonlySet<string> = new Set([
  'language.el',
  'language.en',
  'language.sq',
  'language.sqName',
  'settings.supportEmail',
  'wallet.label',
  'booking.phone',
  'operator.title',
  'station.title',
  'meta.operator.title',
  'meta.station.title',
  'results.durationMinutesOnly',
  'station.coordinatesValue',
  'search.terminal',
  // "operator" is the same word in Albanian as in English; the plural is not.
  'operators.count_one',
]);

/**
 * Plural categories a template may declare. Which one is used for a given count
 * is decided by Intl.PluralRules for the active language, except that an
 * explicit `_zero` always wins at zero: "no journeys" reads better than
 * "0 journeys" in all three languages.
 */
const pluralRules = new Map<Language, Intl.PluralRules>();
function rulesFor(language: Language): Intl.PluralRules {
  let rules = pluralRules.get(language);
  if (!rules) {
    rules = new Intl.PluralRules(language === 'el' ? 'el-GR' : language === 'sq' ? 'sq-AL' : 'en-GB');
    pluralRules.set(language, rules);
  }
  return rules;
}

export function pluralKey(base: string, count: number, language: Language, catalogue: Catalogue): MessageKey {
  const zero = `${base}_zero` as MessageKey;
  if (count === 0 && zero in catalogue) return zero;
  const category = rulesFor(language).select(count);
  const specific = `${base}_${category}` as MessageKey;
  if (specific in catalogue) return specific;
  const other = `${base}_other` as MessageKey;
  if (other in catalogue) return other;
  return base as MessageKey;
}

export type MessageParams = Readonly<Record<string, string | number>>;

/** Substitutes `{name}` placeholders. An unknown placeholder is left visible rather than silently blanked. */
export function interpolate(template: string, params?: MessageParams): string {
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (whole, name: string) => {
    const value = params[name];
    return value === undefined ? whole : String(value);
  });
}

/**
 * Browser language preference, narrowed to a language this app actually has.
 *
 * Passing no argument reads the browser's own list. Passing an explicit list,
 * including an empty one, uses exactly that, so a caller can ask "given these
 * preferences, what would we pick?" and get a truthful answer.
 */
export function detectLanguage(candidates?: readonly string[]): Language | null {
  const list = candidates ?? globalThis.navigator?.languages ?? [];
  for (const tag of list) {
    const base = tag.toLowerCase().split('-')[0];
    if (base && (LANGUAGES as readonly string[]).includes(base)) return base as Language;
  }
  return null;
}
