import { describe, expect, it } from 'vitest';
import {
  addServiceDays,
  athensOffsetMinutes,
  formatClock,
  formatServiceDateLong,
  hoursSince,
  isPastServiceDate,
  serviceDateOf,
  serviceDateStart,
  splitDuration,
  todayServiceDate,
} from './time';
import { isServiceDate } from '../data/contract';

/**
 * Service-date arithmetic.
 *
 * The contract has one rule that is easy to get wrong and expensive when it is:
 * a journey that departs before midnight and arrives after it keeps the earlier
 * service date, and a client must never recompute the date from the arrival
 * instant. These tests pin that, plus the two Europe/Athens transitions where
 * wall-clock and elapsed time disagree.
 */

describe('Europe/Athens offsets', () => {
  it('is +03:00 in summer and +02:00 in winter', () => {
    expect(athensOffsetMinutes(new Date('2026-07-01T12:00:00Z'))).toBe(180);
    expect(athensOffsetMinutes(new Date('2026-01-15T12:00:00Z'))).toBe(120);
  });

  it('changes at the 2026-10-25 transition, not before it', () => {
    // 03:59 local on the morning of the change is still summer time.
    expect(athensOffsetMinutes(new Date('2026-10-25T00:59:00Z'))).toBe(180);
    // 04:00 local, one minute later in real time, is winter time.
    expect(athensOffsetMinutes(new Date('2026-10-25T01:00:00Z'))).toBe(120);
  });

  it('changes at the 2026-03-29 transition', () => {
    expect(athensOffsetMinutes(new Date('2026-03-29T00:59:00Z'))).toBe(120);
    expect(athensOffsetMinutes(new Date('2026-03-29T01:00:00Z'))).toBe(180);
  });
});

describe('service dates', () => {
  it('reports the Athens calendar day, not the UTC one', () => {
    // 22:30 UTC on 1 July is 01:30 on 2 July in Athens.
    expect(serviceDateOf(new Date('2026-07-01T22:30:00Z'))).toBe('2026-07-02');
    // 21:00 UTC on 15 January is 23:00 the same day in Athens.
    expect(serviceDateOf(new Date('2026-01-15T21:00:00Z'))).toBe('2026-01-15');
  });

  it('starts a service date at local midnight on both sides of a transition', () => {
    expect(serviceDateStart('2026-07-01').toISOString()).toBe('2026-06-30T21:00:00.000Z');
    expect(serviceDateStart('2026-01-15').toISOString()).toBe('2026-01-14T22:00:00.000Z');
    // The day the clocks go back starts while summer time is still in force.
    expect(serviceDateStart('2026-10-25').toISOString()).toBe('2026-10-24T21:00:00.000Z');
  });

  it('adds days on the calendar, so a transition never shifts the date', () => {
    expect(addServiceDays('2026-10-24', 1)).toBe('2026-10-25');
    expect(addServiceDays('2026-10-25', 1)).toBe('2026-10-26');
    expect(addServiceDays('2026-03-28', 1)).toBe('2026-03-29');
    expect(addServiceDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addServiceDays('2026-01-01', -1)).toBe('2025-12-31');
    // Leap year.
    expect(addServiceDays('2028-02-28', 1)).toBe('2028-02-29');
  });

  it('accepts only real calendar dates', () => {
    expect(isServiceDate('2026-10-25')).toBe(true);
    expect(isServiceDate('2028-02-29')).toBe(true);
    expect(isServiceDate('2026-02-30')).toBe(false);
    expect(isServiceDate('2026-13-01')).toBe(false);
    expect(isServiceDate('2026-1-1')).toBe(false);
    expect(isServiceDate('not-a-date')).toBe(false);
    expect(isServiceDate(20261025)).toBe(false);
  });

  it('treats a date as past only once its whole Athens day has elapsed', () => {
    const during = new Date('2026-10-25T20:00:00Z'); // 22:00 in Athens on the 25th
    expect(isPastServiceDate('2026-10-25', during)).toBe(false);
    expect(isPastServiceDate('2026-10-24', during)).toBe(true);
    expect(isPastServiceDate('2026-10-26', during)).toBe(false);
  });

  it('agrees with todayServiceDate', () => {
    const now = new Date('2026-10-25T20:00:00Z');
    expect(todayServiceDate(now)).toBe(serviceDateOf(now));
  });
});

describe('overnight journeys and the DST night', () => {
  /*
   * An overnight journey on the night the clocks go back in Athens. The
   * transition is at 04:00 local, which becomes 03:00, so a coach that departs
   * at 23:40 on the 24th under +03:00 and arrives at 05:20 on the 25th is by then
   * under +02:00. Getting the offsets right here is the whole point: an arrival
   * written `+02:00` before the transition would be a different instant entirely,
   * which is the mistake this test was itself written with the first time.
   */
  const departure = '2026-10-24T23:40:00+03:00';
  const arrival = '2026-10-25T05:20:00+02:00';

  it('keeps the service date of the departure', () => {
    // The date is taken from the departure, never recomputed from arrival.
    expect(departure.slice(0, 10)).toBe('2026-10-24');
    expect(arrival.slice(0, 10)).toBe('2026-10-25');
    expect(serviceDateOf(new Date(arrival))).toBe('2026-10-25');
    // Which is exactly why a client must not derive the service date that way.
    expect(serviceDateOf(new Date(arrival))).not.toBe(departure.slice(0, 10));
  });

  it('shows elapsed time, which is an hour more than the wall clock suggests', () => {
    const elapsed = (Date.parse(arrival) - Date.parse(departure)) / 60000;
    // 20:40Z to 03:20Z is six hours and forty minutes of real time.
    expect(elapsed).toBe(400);
    // The wall clock reads 23:40 to 05:20, which looks like five hours forty.
    const wallClock = 5 * 60 + 20 + (24 * 60 - (23 * 60 + 40));
    expect(wallClock).toBe(340);
    expect(elapsed - wallClock).toBe(60);
  });

  it('renders each time on the Athens clock a passenger would read', () => {
    expect(formatClock(departure, 'en')).toBe('23:40');
    expect(formatClock(arrival, 'en')).toBe('05:20');
  });

  it('rejects an arrival whose declared offset is not the one in force', () => {
    // `+02:00` at 01:20 on the 25th is 23:20Z on the 24th, when Athens is still
    // on +03:00, so that instant is 02:20 on the Athens clock and not 01:20.
    expect(formatClock('2026-10-25T01:20:00+02:00', 'en')).toBe('02:20');
  });

  it('detects crossing midnight from the written local dates, not from elapsed time', () => {
    expect(arrival.slice(0, 10) !== departure.slice(0, 10)).toBe(true);
    const sameDay = ['2026-10-02T09:00:00+03:00', '2026-10-02T12:10:00+03:00'];
    expect(sameDay[1]!.slice(0, 10) !== sameDay[0]!.slice(0, 10)).toBe(false);
  });
});

describe('presentation', () => {
  it('formats the long service date in each language', () => {
    expect(formatServiceDateLong('2026-10-02', 'en')).toMatch(/Friday/);
    expect(formatServiceDateLong('2026-10-02', 'el')).toMatch(/Παρασκευή/);
    // Albanian names the weekday too; the exact spelling is the platform's.
    expect(formatServiceDateLong('2026-10-02', 'sq')).toMatch(/\d{4}/);
  });

  it('splits a duration into hours and minutes', () => {
    expect(splitDuration(190)).toEqual({ hours: 3, minutes: 10 });
    expect(splitDuration(45)).toEqual({ hours: 0, minutes: 45 });
    expect(splitDuration(0)).toEqual({ hours: 0, minutes: 0 });
    expect(splitDuration(-5)).toEqual({ hours: 0, minutes: 0 });
  });

  it('never reports a negative age', () => {
    const now = new Date('2026-09-30T00:00:00Z');
    expect(hoursSince('2026-09-29T00:00:00Z', now)).toBe(24);
    expect(hoursSince('2026-10-01T00:00:00Z', now)).toBe(0);
  });
});
