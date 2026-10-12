import { describe, expect, it } from 'vitest';
import type { Place } from '../data/contract';
import { groupPlaceOptions } from './PlaceField';

const place = (id: string, kind: Place['kind'], coverage: Place['coverage']): Place => ({
  id, kind, name: { el: 'Αθήνα', en: 'Athens', sq: 'Athinë' }, parentId: null,
  municipality: 'Athens', latitude: 0, longitude: 0, coordinateStatus: 'valid',
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
