import { expect, it } from 'vitest';
import { uniqueAvailableTimes, replacementTimes } from './availableTimes';
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

it('keeps Maya when rescheduling and offers Coco only when the customer changes preference', () => {
  const slots = [{
    id: 'room', available: true, startTime: '2027-01-18T10:00:00Z',
    therapists: [{ id: 'maya', available: false }, { id: 'coco', available: true }],
    therapistTypes: [{ type: 'Thai', availableCount: 1 }]
  }] as Slot[];
  expect(replacementTimes(slots, 'keep', 'maya')).toEqual([]);
  expect(replacementTimes(slots, 'coco', 'maya')).toEqual(slots);
  expect(replacementTimes(slots, 'keep', 'type:Thai')).toEqual(slots);
  expect(replacementTimes(slots, 'keep', '')).toEqual(slots);
});
