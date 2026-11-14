import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import BookingConfirmation from './BookingConfirmation';
import type { BookingSummary } from '../../types/booking';
vi.mock('../../hooks/useBookingFlow', () => ({ useBookingFlow: () => ({ reset: () => {} }) }));
const receipt: BookingSummary = {
  id: '11',
  reference: 'TEST-11',
  status: 'CONFIRMED',
  paymentMode: 'OFFLINE',
  spaName: 'Test spa',
  service: { id: '1', name: 'Test service', currency: 'INR', price: 1200, durationMinutes: 30 },
  slot: '2027-01-01T12:00:00Z',
  totalCents: 120000,
  currency: 'INR'
};
describe('booking receipt', () => {
  it('shows the returned appointment reference, time and amount', () => {
    const html = renderToStaticMarkup(
      <BookingConfirmation summary={receipt} onRestart={() => {}} />
    );
    for (const text of [
      'Appointment confirmed',
      'TEST-11',
      'Test service',
      'Test spa',
      receipt.slot,
      '1,200',
      '/me/bookings'
    ])
      expect(html).toContain(text);
  });
  it('identifies pending payment without announcing confirmation', () => {
    const html = renderToStaticMarkup(
      <BookingConfirmation
        summary={{ ...receipt, status: 'PENDING_PAYMENT' }}
        onRestart={() => {}}
      />
    );
    expect(html).toContain('Payment pending');
    expect(html).not.toContain('Appointment confirmed');
  });
});
