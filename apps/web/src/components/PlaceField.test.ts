import { describe, expect, it } from 'vitest';
import type { Place } from '../data/contract';
import { groupPlaceOptions } from './PlaceField';

const place = (
  id: string,
  kind: Place['kind'],
  coverage: Place['coverage'],
  municipality: string | null = 'Athens',
): Place => ({
  id, kind, name: { el: 'Αθήνα', en: 'Athens', sq: 'Athinë' }, parentId: null,
  municipality, latitude: 0, longitude: 0, coordinateStatus: 'valid',
  boardingPointCount: 1, operatorIds: [id], coverage,
});

describe('groupPlaceOptions', () => {
  it('removes duplicate identities by visible label while retaining a covered representative', () => {
    const result = groupPlaceOptions([place('uncovered', 'stop_place', 'not_covered'), place('covered', 'stop_place', 'covered')], (name) => name.en);
    expect(result.map((item) => item.id)).toEqual(['covered']);
  });

  it('keeps distinct boarding-point and terminal identities separate', () => {
    const result = groupPlaceOptions([place('terminal', 'stop_place', 'covered'), place('bay', 'stop', 'covered')], (name) => name.en);
    expect(result).toHaveLength(2);
  });
});

describe('groupPlaceOptions with the municipality the real release publishes', () => {
  /*
   * Every place in the KTEL release has a null municipality: the ticketing
   * source does not publish one. The contract typed it as a plain string and
   * this fixture supplied "Athens", so grouping called `.trim()` on a value
   * that is null in production. It threw, the caller swallowed the error into an
   * empty suggestion list, and the place picker stopped offering anything at
   * all on the live site. Nothing failed, which is what made it hard to see.
   */
  it('groups places that have no municipality instead of throwing', () => {
    const result = groupPlaceOptions(
      [place('a', 'stop_place', 'covered', null), place('b', 'stop', 'covered', null)],
      (name) => name.en,
    );
    expect(result).toHaveLength(2);
  });

  it('still collapses duplicates when the municipality is absent', () => {
    const result = groupPlaceOptions(
      [place('uncovered', 'stop_place', 'not_covered', null), place('covered', 'stop_place', 'covered', null)],
      (name) => name.en,
    );
    expect(result.map((item) => item.id)).toEqual(['covered']);
  });
});

describe('ordering by coverage', () => {
  it('leads with places a search can answer for, without dropping the rest', () => {
    const named = (id: string, label: string, coverage: Place['coverage']): Place => ({
      ...place(id, 'stop', coverage, null),
      name: { el: label, en: label, sq: label },
    });
    const result = groupPlaceOptions(
      [named('dead1', 'A', 'not_covered'), named('live', 'B', 'covered'), named('dead2', 'C', 'not_covered')],
      (name) => name.en,
    );
    // 14,437 of the 19,139 places in the release are not covered, so a reader
    // picking by name alone lands on one of those most of the time and only
    // finds out after searching.
    expect(result.map((item) => item.id)).toEqual(['live', 'dead1', 'dead2']);
  });
});
