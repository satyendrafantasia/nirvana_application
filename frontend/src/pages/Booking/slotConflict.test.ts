import { expect, it } from 'vitest';
import { isSlotConflict } from '../../api/bookingApi';
import { withSelectedSlot } from '../../contexts/BookingContext';
import type { Slot } from '../../types/booking';

it.each([
  [409, 'This time is no longer available', true],
  [409, 'This time is no longer available; your hold expired', true],
  [404, 'Slot not found: 12', true],
  [409, 'Package not eligible for this booking', false],
  [409, 'Checkout key already used for different booking details', false],
  [500, 'This time is no longer available', false]
])('classifies %i %s as a slot conflict only when appropriate', (status, message, expected) => {
  expect(isSlotConflict({ isAxiosError: true, response: { status, data: { message } } })).toBe(expected);
});

it.each([{therapistId: '7'}, {therapistType: 'Thai'}, {}])(
  'releases only the stale reservation and retains the day and preference when reselecting',
  (preference) => {
    const draft = { spaId: '5', guests: 2, availabilityDate: '2027-06-14', notes: 'Quiet room', ...preference };
    const stale = { ...draft, slot: '2027-06-14T10:00Z', selectedSlot: { id: '11' } as Slot,
      heldTherapistName: 'Maya', holdToken: 'old', holdExpiresAt: '2027-06-01T10:10Z' };
    const cleared = withSelectedSlot(stale);
    expect(cleared).toMatchObject(draft);
    for (const value of [cleared.slot, cleared.selectedSlot, cleared.holdToken, cleared.heldTherapistName, cleared.holdExpiresAt])
      expect(value).toBeUndefined();
    const replacement = { id: '12', startTime: '2027-06-14T12:00Z' } as Slot;
    expect(withSelectedSlot(cleared, replacement)).toMatchObject({ ...draft, selectedSlot: replacement, slot: replacement.startTime });
  }
);
