import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import ReviewBooking from './ReviewBooking';

vi.mock('../../hooks/useBookingFlow');

describe('add-ons on review', () => {
  it('lists each add-on with its price for all guests and the longer appointment', () => {
    vi.mocked(useBookingFlow).mockReturnValue({
      state: {
        guests: 2,
        spaName: 'Fern House',
        service: { id: '5', name: 'Deep tissue', durationMinutes: 60, price: 120, currency: 'INR' },
        addOns: [
          { id: 3, name: 'Hot stones', priceCents: 50000, currency: 'INR', extraMinutes: 0 },
          { id: 7, name: 'Extension', priceCents: 40000, currency: 'INR', extraMinutes: 30 }
        ]
      }
    } as unknown as ReturnType<typeof useBookingFlow>);
    const t = renderToStaticMarkup(<ReviewBooking onNext={vi.fn()} onBack={vi.fn()} />).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
    expect(t).toContain('Add-ons');
    expect(t).toContain('Hot stones');
    expect(t).toContain('₹1,000.00 (₹500.00 × 2)');
    expect(t).toContain('Extension +30 min');
    expect(t).toContain('Appointment length: 90 mins');
  });
  it('shows no add-on section when none were chosen', () => {
    vi.mocked(useBookingFlow).mockReturnValue({
      state: { guests: 1, service: { id: '5', name: 'Deep tissue', durationMinutes: 60, price: 120, currency: 'INR' } }
    } as unknown as ReturnType<typeof useBookingFlow>);
    expect(renderToStaticMarkup(<ReviewBooking onNext={vi.fn()} onBack={vi.fn()} />)).not.toContain('Add-ons');
  });
});
