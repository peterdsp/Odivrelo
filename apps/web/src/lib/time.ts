/**
 * Service-date and clock arithmetic.
 *
 * Service dates are Europe/Athens calendar days. A journey that departs before
 * midnight and arrives after it keeps the earlier service date, so nothing here
 * ever recomputes a service date from an arrival instant.
 *
 * Every offset is derived from Intl with an explicit `Europe/Athens` time zone
 * rather than hardcoded, so the 2026-10-25 transition and every future rule
 * change are handled by the platform's own tz database.
 */
import type { Language } from '../data/contract';

export const SERVICE_TIME_ZONE = 'Europe/Athens';

const localeFor: Record<Language, string> = { el: 'el-GR', en: 'en-GB', sq: 'sq-AL' };

export function localeTag(language: Language): string {
  return localeFor[language];
}

const partsCache = new Map<string, Intl.DateTimeFormat>();
function formatter(key: string, options: Intl.DateTimeFormatOptions, locale: string): Intl.DateTimeFormat {
  const cacheKey = `${key}|${locale}`;
  let f = partsCache.get(cacheKey);
  if (!f) {
    f = new Intl.DateTimeFormat(locale, { timeZone: SERVICE_TIME_ZONE, ...options });
    partsCache.set(cacheKey, f);
  }
  return f;
}

/** Offset of Europe/Athens from UTC, in minutes, at a given absolute instant. */
export function athensOffsetMinutes(instant: Date): number {
  const parts = Object.fromEntries(
    formatter(
      'offset',
      { hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' },
      'en-US',
    )
      .formatToParts(instant)
      .filter((p) => p.type !== 'literal')
      .map((p) => [p.type, p.value]),
  ) as Record<string, string>;
  const hour = parts.hour === '24' ? 0 : Number(parts.hour);
  const asUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    hour,
    Number(parts.minute),
    Number(parts.second),
  );
  return Math.round((asUtc - instant.getTime()) / 60000);
}

/** The Europe/Athens calendar date an instant falls on. */
export function serviceDateOf(instant: Date): string {
  const parts = Object.fromEntries(
    formatter('date', { year: 'numeric', month: '2-digit', day: '2-digit' }, 'en-CA')
      .formatToParts(instant)
      .filter((p) => p.type !== 'literal')
      .map((p) => [p.type, p.value]),
  ) as Record<string, string>;
  return `${parts.year}-${parts.month}-${parts.day}`;
}

export function todayServiceDate(now: Date = new Date()): string {
  return serviceDateOf(now);
}

export function addServiceDays(serviceDate: string, days: number): string {
  const [y, m, d] = serviceDate.split('-').map(Number) as [number, number, number];
  const probe = new Date(Date.UTC(y, m - 1, d));
  probe.setUTCDate(probe.getUTCDate() + days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${probe.getUTCFullYear()}-${pad(probe.getUTCMonth() + 1)}-${pad(probe.getUTCDate())}`;
}

export function compareServiceDates(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Midnight at the start of a service date, as an absolute instant. */
export function serviceDateStart(serviceDate: string): Date {
  const [y, m, d] = serviceDate.split('-').map(Number) as [number, number, number];
  const naive = Date.UTC(y, m - 1, d);
  let offset = athensOffsetMinutes(new Date(naive));
  offset = athensOffsetMinutes(new Date(naive - offset * 60000));
  return new Date(naive - offset * 60000);
}

/** A service date is past once its whole day has elapsed in Athens. */
export function isPastServiceDate(serviceDate: string, now: Date = new Date()): boolean {
  return serviceDate < todayServiceDate(now);
}

// ---------------------------------------------------------------------------
// Presentation
// ---------------------------------------------------------------------------

/**
 * Times carried in a contract payload already state the offset that was in
 * force. They are rendered in the service time zone so that a passenger reading
 * the page from another country still sees the time printed on the timetable.
 */
export function formatClock(iso: string, language: Language): string {
  return formatter('clock', { hour: '2-digit', minute: '2-digit', hour12: false }, localeTag(language)).format(new Date(iso));
}

export function formatServiceDateLong(serviceDate: string, language: Language): string {
  return formatter('dateLong', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }, localeTag(language)).format(
    serviceDateStart(serviceDate),
  );
}

export function formatServiceDateShort(serviceDate: string, language: Language): string {
  return formatter('dateShort', { weekday: 'short', day: 'numeric', month: 'short' }, localeTag(language)).format(
    serviceDateStart(serviceDate),
  );
}

export function formatDateTime(iso: string, language: Language): string {
  return formatter(
    'dateTime',
    { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false },
    localeTag(language),
  ).format(new Date(iso));
}

export function formatNumber(value: number, language: Language, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(localeTag(language), options).format(value);
}

export function formatMoney(amount: number, currency: string, language: Language): string {
  return new Intl.NumberFormat(localeTag(language), { style: 'currency', currency }).format(amount);
}

/** Hours since an instant, floored, never negative. */
export function hoursSince(iso: string, now: Date = new Date()): number {
  return Math.max(0, Math.floor((now.getTime() - Date.parse(iso)) / 3_600_000));
}

export interface DurationParts {
  readonly hours: number;
  readonly minutes: number;
}

export function splitDuration(totalMinutes: number): DurationParts {
  const safe = Math.max(0, Math.round(totalMinutes));
  return { hours: Math.floor(safe / 60), minutes: safe % 60 };
}

/** The `datetime` value for a `<time>` element. */
export function machineDateTime(iso: string): string {
  return iso;
}
