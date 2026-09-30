import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  clearAllPrefs,
  parseArrayOf,
  parseBoolean,
  parseOneOf,
  parseString,
  prefsKey,
  readPref,
  removePref,
  storageAvailable,
  writePref,
} from './prefs';

/**
 * Preference storage, including every way it can fail.
 *
 * `localStorage` throws on read as well as on write in a private window, with
 * site data blocked, and when the quota is full. The brief requires the app to
 * render correctly when that happens, so each failure mode is exercised rather
 * than assumed.
 */

function breakStorage(mode: 'throw-get' | 'throw-set' | 'absent') {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const fail = () => {
    throw new DOMException('The operation is insecure.', 'SecurityError');
  };
  const replacement =
    mode === 'absent'
      ? undefined
      : {
          getItem: mode === 'throw-get' ? fail : () => null,
          setItem: mode === 'throw-set' ? fail : () => {},
          removeItem: () => {},
          key: () => null,
          clear: () => {},
          length: 0,
        };
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: replacement });
  return () => {
    if (original) Object.defineProperty(globalThis, 'localStorage', original);
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('preference keys', () => {
  it('are namespaced, so nothing else on the origin is touched', () => {
    expect(prefsKey('language')).toBe('odivrelo.v1.language');
  });
});

describe('reading and writing', () => {
  it('round-trips a value', () => {
    expect(writePref('language', 'sq')).toBe(true);
    expect(readPref('language', 'el', parseString)).toBe('sq');
  });

  it('returns the fallback when nothing is stored', () => {
    expect(readPref('never-written', 'el', parseString)).toBe('el');
  });

  it('returns the fallback when the stored value is not valid JSON', () => {
    globalThis.localStorage.setItem(prefsKey('broken'), '{not json');
    expect(readPref('broken', 'el', parseString)).toBe('el');
  });

  it('returns the fallback when the stored value is the wrong shape', () => {
    writePref('language', { unexpected: true });
    expect(readPref('language', 'el', parseString)).toBe('el');
  });

  it('removes a single preference without touching the others', () => {
    writePref('language', 'en');
    writePref('theme', 'dark');
    removePref('language');
    expect(readPref('language', 'el', parseString)).toBe('el');
    expect(readPref('theme', 'system', parseString)).toBe('dark');
  });

  it('clears only its own namespace', () => {
    writePref('language', 'en');
    globalThis.localStorage.setItem('somebody-elses-key', 'keep me');
    clearAllPrefs();
    expect(readPref('language', 'el', parseString)).toBe('el');
    expect(globalThis.localStorage.getItem('somebody-elses-key')).toBe('keep me');
  });
});

describe('when storage is unavailable', () => {
  it('reports that it is unavailable rather than throwing', () => {
    const restore = breakStorage('throw-set');
    try {
      expect(storageAvailable()).toBe(false);
    } finally {
      restore();
    }
  });

  it('reading falls back instead of throwing', () => {
    const restore = breakStorage('throw-get');
    try {
      expect(readPref('language', 'el', parseString)).toBe('el');
    } finally {
      restore();
    }
  });

  it('writing reports failure instead of throwing', () => {
    const restore = breakStorage('throw-set');
    try {
      expect(writePref('language', 'sq')).toBe(false);
    } finally {
      restore();
    }
  });

  it('survives localStorage being absent entirely', () => {
    const restore = breakStorage('absent');
    try {
      expect(() => readPref('language', 'el', parseString)).not.toThrow();
      expect(readPref('language', 'el', parseString)).toBe('el');
      expect(writePref('language', 'sq')).toBe(false);
      expect(() => removePref('language')).not.toThrow();
      expect(() => clearAllPrefs()).not.toThrow();
      expect(storageAvailable()).toBe(false);
    } finally {
      restore();
    }
  });
});

describe('parsers', () => {
  it('parseString accepts only strings', () => {
    expect(parseString('a')).toBe('a');
    expect(parseString(1)).toBeNull();
    expect(parseString(null)).toBeNull();
  });

  it('parseBoolean accepts only booleans', () => {
    expect(parseBoolean(true)).toBe(true);
    expect(parseBoolean(false)).toBe(false);
    expect(parseBoolean('true')).toBeNull();
  });

  it('parseOneOf rejects anything outside the allowed set', () => {
    const parse = parseOneOf(['el', 'en', 'sq'] as const);
    expect(parse('sq')).toBe('sq');
    expect(parse('de')).toBeNull();
    expect(parse(3)).toBeNull();
  });

  it('parseArrayOf drops invalid entries and bounds the length', () => {
    const parse = parseArrayOf(parseString, 3);
    expect(parse(['a', 2, 'b', null, 'c', 'd'])).toEqual(['a', 'b']);
    expect(parse('not an array')).toBeNull();
    expect(parse(Array.from({ length: 50 }, (_, i) => `v${i}`))).toHaveLength(3);
  });
});
