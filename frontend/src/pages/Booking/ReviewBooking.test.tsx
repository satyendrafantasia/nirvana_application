import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import type { Slot } from '../../types/booking';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import ReviewBooking from './ReviewBooking';

vi.mock('../../hooks/useBookingFlow');

describe('therapist shown on review', () => {
  const slot: Slot = {
    id: '1', available: true, startTime: '2027-01-18T10:00Z', endTime: '2027-01-18T11:00Z',
    therapistSelectionEnabled: true, therapistTypeSelectionEnabled: true,
    therapists: [{ id: '7', name: 'Maya', available: true }], therapistTypes: []
  };
  it.each([
    [{ therapistId: '7', heldTherapistName: 'Maya · Thai' }, 'Maya · Thai'],
    [{ therapistId: '7' }, 'Maya'],
    [{ therapistId: '9', preferredTherapistName: 'Coco' }, 'Coco'],
    [{ therapistType: 'NORTH_EAST' }, 'Northeast'],
    [{}, 'Any available therapist']
  ])('uses the public assignment or the guest’s preference', (preference, label) => {
    vi.mocked(useBookingFlow).mockReturnValue({
      state: { guests: 1, selectedSlot: slot, ...preference }
    } as ReturnType<typeof useBookingFlow>);
    expect(renderToStaticMarkup(<ReviewBooking onNext={vi.fn()} onBack={vi.fn()} />)).toContain(label);
  });
});
