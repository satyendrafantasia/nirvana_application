import { expect, it } from 'vitest';
import { uniqueAvailableTimes } from './availableTimes';
import { Slot } from '../../types/booking';
it('offers a start once and selects a room compatible with the requested therapist', () => {
  const times = ['a', 'b'].map(
    (id, i) =>
      ({
        id,
        available: true,
        startTime: i ? '2027-01-18T15:30:00+05:30' : '2027-01-18T10:00:00Z',
        therapists: [{ id, available: true }]
      }) as Slot
  );
  expect(uniqueAvailableTimes(times).map((s) => s.id)).toEqual(['a']);
  expect(uniqueAvailableTimes(times, 'b').map((s) => s.id)).toEqual(['b']);
  expect(uniqueAvailableTimes(times, 'other')).toEqual([]);
});
