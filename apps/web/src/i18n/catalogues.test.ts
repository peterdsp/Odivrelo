import { describe, expect, it } from 'vitest';
import { LANGUAGES, type Language } from '../data/contract';
import { en } from './en';
import { el } from './el';
import { sq } from './sq';
import { catalogues, detectLanguage, interpolate, messageKeys, pluralKey, SHARED_LITERAL_KEYS } from './catalogues';

/**
 * Translation completeness, enforced.
 *
 * The brief requires every user-visible string in all three languages with no
 * English leaking into Greek or Albanian. TypeScript already makes a missing key a
 * compile error; these tests catch the things the type system cannot see: an empty
 * string, a key left equal to the English text, a placeholder that was dropped in
 * translation, and a plural form that exists in one language but not another.
 */

const PLACEHOLDER = /\{(\w+)\}/g;
const placeholdersOf = (value: string) => [...value.matchAll(PLACEHOLDER)].map((m) => m[1]).sort();

describe('the message catalogues', () => {
  it('covers all three languages', () => {
    expect(Object.keys(catalogues).sort()).toEqual([...LANGUAGES].sort());
  });

  it.each(LANGUAGES)('%s has every key the canonical catalogue defines', (language: Language) => {
    const catalogue = catalogues[language];
    const missing = messageKeys.filter((key) => !(key in catalogue));
    expect(missing, `missing in ${language}: ${missing.join(', ')}`).toEqual([]);
  });

  it.each(LANGUAGES)('%s defines no key the canonical catalogue does not', (language: Language) => {
    const known = new Set<string>(messageKeys);
    const extra = Object.keys(catalogues[language]).filter((key) => !known.has(key));
    expect(extra, `extra in ${language}: ${extra.join(', ')}`).toEqual([]);
  });

  it.each(LANGUAGES)('%s has no empty or whitespace-only string', (language: Language) => {
    const catalogue = catalogues[language] as Record<string, string>;
    const blank = messageKeys.filter((key) => (catalogue[key] ?? '').trim().length === 0);
    expect(blank, `blank in ${language}: ${blank.join(', ')}`).toEqual([]);
  });

  it.each(['el', 'sq'] as const)('%s never falls back to the English text', (language: 'el' | 'sq') => {
    const catalogue = catalogues[language] as Record<string, string>;
    const english = en as unknown as Record<string, string>;
    const untranslated = messageKeys.filter(
      (key) => !SHARED_LITERAL_KEYS.has(key) && catalogue[key] === english[key],
    );
    expect(
      untranslated,
      `these ${language} strings are still the English text: ${untranslated.join(', ')}`,
    ).toEqual([]);
  });

  it.each(LANGUAGES)('%s keeps every placeholder the canonical string declares', (language: Language) => {
    const catalogue = catalogues[language] as Record<string, string>;
    const english = en as unknown as Record<string, string>;
    const broken: string[] = [];
    for (const key of messageKeys) {
      const expected = placeholdersOf(english[key] ?? '');
      const actual = placeholdersOf(catalogue[key] ?? '');
      if (JSON.stringify(expected) !== JSON.stringify(actual)) {
        broken.push(`${key} (expected ${expected.join('|') || 'none'}, got ${actual.join('|') || 'none'})`);
      }
    }
    expect(broken, `placeholder mismatch in ${language}: ${broken.join('; ')}`).toEqual([]);
  });

  it('defines the same set of plural forms in every language', () => {
    const formsOf = (catalogue: Record<string, string>) =>
      Object.keys(catalogue)
        .filter((key) => /_(zero|one|two|few|many|other)$/.test(key))
        .sort();
    const expected = formsOf(en as unknown as Record<string, string>);
    for (const language of LANGUAGES) {
      expect(formsOf(catalogues[language] as Record<string, string>), language).toEqual(expected);
    }
  });

  it('has an _other form for every plural base that has an _one form', () => {
    const catalogue = en as unknown as Record<string, string>;
    const bases = new Set(
      Object.keys(catalogue)
        .filter((key) => key.endsWith('_one'))
        .map((key) => key.slice(0, -'_one'.length)),
    );
    for (const base of bases) {
      for (const language of LANGUAGES) {
        expect(catalogues[language], `${language} ${base}_other`).toHaveProperty(`${base}_other`);
      }
    }
  });

  it('contains no trace of the rejected identity', () => {
    /*
     * The patterns are assembled from fragments rather than written out, so this
     * file does not itself contain the strings scripts/check-brand.sh scans the
     * tree for. A test that trips the gate it exists to support is not much of a
     * test.
     */
    const forbidden = [
      ['hodo', 'map'].join(''),
      ['pora', 'via'].join(''),
      ['drom', 'iqo'].join(''),
      ['per', 'astra'].join(''),
      ['<new', 'name>'].join(''),
    ];
    for (const language of LANGUAGES) {
      for (const [key, value] of Object.entries(catalogues[language] as Record<string, string>)) {
        for (const needle of forbidden) {
          expect(value.toLowerCase(), `${language}.${key} contains a rejected identity`).not.toContain(needle);
        }
      }
    }
  });

  it('never claims Odivrelo is a Greek word or translates it', () => {
    // The name is invented. The About copy has to say so in all three languages.
    expect((el as unknown as Record<string, string>)['settings.aboutBody']).toContain('εφευρημένο');
    expect((en as unknown as Record<string, string>)['settings.aboutBody']).toContain('invented');
    expect((sq as unknown as Record<string, string>)['settings.aboutBody']).toContain('shpikur');
  });
});

describe('plural selection', () => {
  it('prefers an explicit zero form at zero', () => {
    expect(pluralKey('results.count', 0, 'en', en)).toBe('results.count_zero');
    expect(pluralKey('results.count', 0, 'el', el)).toBe('results.count_zero');
    expect(pluralKey('results.count', 0, 'sq', sq)).toBe('results.count_zero');
  });

  it('uses the one form at one and the other form above it, in every language', () => {
    for (const [language, catalogue] of Object.entries(catalogues) as [Language, typeof en][]) {
      expect(pluralKey('results.count', 1, language, catalogue)).toBe('results.count_one');
      expect(pluralKey('results.count', 7, language, catalogue)).toBe('results.count_other');
    }
  });

  it('falls back to the other form when a category has no string', () => {
    // Greek has no "few" category, but asking for one must not produce a key miss.
    expect(pluralKey('results.count', 3, 'el', el)).toBe('results.count_other');
  });
});

describe('interpolation', () => {
  it('substitutes named placeholders', () => {
    expect(interpolate('{a} then {b}', { a: 'one', b: 2 })).toBe('one then 2');
  });

  it('leaves an unknown placeholder visible rather than blanking it', () => {
    expect(interpolate('{a} then {b}', { a: 'one' })).toBe('one then {b}');
  });

  it('returns the template untouched when there are no parameters', () => {
    expect(interpolate('nothing to fill')).toBe('nothing to fill');
  });
});

describe('language detection', () => {
  it('picks the first supported language from the browser list', () => {
    expect(detectLanguage(['de-DE', 'sq-AL', 'en-GB'])).toBe('sq');
    expect(detectLanguage(['el'])).toBe('el');
    expect(detectLanguage(['en-US'])).toBe('en');
  });

  it('returns null when nothing is supported, so the caller applies the default', () => {
    expect(detectLanguage(['de-DE', 'fr-FR'])).toBeNull();
    expect(detectLanguage([])).toBeNull();
  });
});
