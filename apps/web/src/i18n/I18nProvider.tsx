import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Language, LocalizedOrText } from '../data/contract';
import { isLanguage, localized } from '../data/contract';
import { BRAND } from '../brand/brand';
import { catalogues, detectLanguage, interpolate, pluralKey, type MessageKey, type MessageParams } from './catalogues';
import { parseOneOf, readPref, writePref } from '../lib/prefs';
import {
  formatClock,
  formatDateTime,
  formatMoney,
  formatNumber,
  formatServiceDateLong,
  formatServiceDateShort,
  hoursSince,
  localeTag,
  splitDuration,
} from '../lib/time';

const LANGUAGE_PREF = 'language';
const DEFAULT_LANGUAGE = (isLanguage(BRAND.defaultLanguage) ? BRAND.defaultLanguage : 'el') as Language;

export interface I18n {
  readonly language: Language;
  /** True when the language came from an explicit choice rather than browser detection. */
  readonly explicit: boolean;
  readonly locale: string;
  setLanguage: (language: Language) => void;
  t: (key: MessageKey, params?: MessageParams) => string;
  /** Plural-aware lookup. `count` is passed through as the `{count}` placeholder. */
  plural: (base: string, count: number, params?: MessageParams) => string;
  /** Picks the right field out of a contract Localized value, or returns a plain string as-is. */
  name: (value: LocalizedOrText | null | undefined) => string;
  formatClock: (iso: string) => string;
  formatDateTime: (iso: string) => string;
  formatServiceDate: (serviceDate: string) => string;
  formatServiceDateShort: (serviceDate: string) => string;
  formatNumber: (value: number, options?: Intl.NumberFormatOptions) => string;
  formatMoney: (amount: number, currency: string) => string;
  formatDuration: (minutes: number) => string;
  formatAge: (iso: string, now?: Date) => string;
}

const I18nContext = createContext<I18n | null>(null);

export interface I18nProviderProps {
  children: ReactNode;
  /** Only tests pass this; the app detects or reads the stored preference. */
  initialLanguage?: Language;
}

function initialFrom(explicitOverride?: Language): { language: Language; explicit: boolean } {
  if (explicitOverride) return { language: explicitOverride, explicit: true };
  const stored = readPref<Language | null>(LANGUAGE_PREF, null, parseOneOf(['el', 'en', 'sq'] as const));
  if (stored) return { language: stored, explicit: true };
  return { language: detectLanguage() ?? DEFAULT_LANGUAGE, explicit: false };
}

export function I18nProvider({ children, initialLanguage }: I18nProviderProps) {
  const [state, setState] = useState(() => initialFrom(initialLanguage));
  const { language, explicit } = state;

  // The document language has to follow, or assistive technology reads Greek
  // with an English voice and Albanian with a Greek one.
  useEffect(() => {
    const root = globalThis.document?.documentElement;
    if (root) root.lang = language;
  }, [language]);

  const setLanguage = useCallback((next: Language) => {
    writePref(LANGUAGE_PREF, next);
    setState({ language: next, explicit: true });
  }, []);

  const value = useMemo<I18n>(() => {
    const catalogue = catalogues[language];
    const locale = localeTag(language);
    const t = (key: MessageKey, params?: MessageParams): string => {
      const template = catalogue[key];
      if (template === undefined) {
        // Every key exists in every catalogue (a test enforces it). Showing the
        // key beats showing nothing if that ever stops being true.
        return key;
      }
      return interpolate(template, params);
    };
    return {
      language,
      explicit,
      locale,
      setLanguage,
      t,
      plural: (base, count, params) => t(pluralKey(base, count, language, catalogue), { count, ...params }),
      name: (localizedValue) => localized(localizedValue, language),
      formatClock: (iso) => formatClock(iso, language),
      formatDateTime: (iso) => formatDateTime(iso, language),
      formatServiceDate: (serviceDate) => formatServiceDateLong(serviceDate, language),
      formatServiceDateShort: (serviceDate) => formatServiceDateShort(serviceDate, language),
      formatNumber: (n, options) => formatNumber(n, language, options),
      formatMoney: (amount, currency) => formatMoney(amount, currency, language),
      formatDuration: (minutes) => {
        const { hours, minutes: rest } = splitDuration(minutes);
        return hours === 0
          ? t('results.durationMinutesOnly', { minutes: rest })
          : t('results.durationValue', { hours, minutes: String(rest).padStart(2, '0') });
      },
      formatAge: (iso, now) => {
        const hours = hoursSince(iso, now);
        return t(pluralKey('freshness.age', hours, language, catalogue), { count: hours });
      },
    };
  }, [language, explicit, setLanguage]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
  const context = useContext(I18nContext);
  if (!context) throw new Error('useI18n was called outside an I18nProvider.');
  return context;
}
