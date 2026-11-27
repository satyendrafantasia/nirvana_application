import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import type { BookingSummary } from '../../types/booking';
import MyBookings from './MyBookings';

const appointment: BookingSummary = {
  id: '8',
  spaName: 'Nirvana Owner Preview',
  service: { id: '5', name: 'Relaxation Massage', durationMinutes: 60, price: 900, currency: 'INR' },
  slot: '2027-01-02T11:00:00+05:30',
  status: 'CONFIRMED',
  paymentMode: 'OFFLINE',
  payAtSpaCheckoutCents: 150000,
  currency: 'INR'
};

function renderAppointment(changes: Partial<BookingSummary> = {}) {
  const client = new QueryClient();
  client.setQueryData(['my-bookings', 0], { items: [{ ...appointment, ...changes }], totalPages: 1 });
  return renderToStaticMarkup(<QueryClientProvider client={client}><MyBookings /></QueryClientProvider>);
}

describe('My Bookings checkout amount', () => {
  it('shows the original at-spa total even when the current service price differs', () => {
    expect(renderAppointment()).toContain('Original Pay at spa total · ₹1,500.00');
  });
  it('preserves a fully credited amount of zero', () => {
    expect(renderAppointment({ payAtSpaCheckoutCents: 0 })).toContain('Original Pay at spa total · ₹0.00');
  });
  it('omits the at-spa total for package and online bookings', () => {
    expect(renderAppointment({ payAtSpaCheckoutCents: undefined })).not.toContain('Original Pay at spa total');
    expect(renderAppointment({ paymentMode: 'ONLINE' })).not.toContain('Original Pay at spa total');
  });
});
