import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach, describe, expect, it, vi } from 'vitest';
import client from '../../api/client';
import { downloadCalendarInvite } from '../../api/bookingApi';
import AddToCalendar, { CalendarBooking, googleCalendarUrl } from './AddToCalendar';
import BookingConfirmation from '../../pages/Booking/BookingConfirmation';
import MyBookings from '../../pages/Profile/MyBookings';
import type { BookingSummary } from '../../types/booking';

vi.mock('../../hooks/useBookingFlow', () => ({ useBookingFlow: () => ({ reset: () => {} }) }));

const booking: CalendarBooking = {
  id: '8',
  reference: 'REF-8',
  serviceName: 'Deep tissue',
  spaName: 'Lotus & Stone',
  start: '2027-01-02T11:00:00+05:30',
  durationMinutes: 90
};

const summary: BookingSummary = {
  id: '8',
  reference: 'REF-8',
  status: 'CONFIRMED',
  paymentMode: 'OFFLINE',
  spaName: 'Lotus & Stone',
  service: { id: '5', name: 'Deep tissue', durationMinutes: 90, price: 900, currency: 'INR' },
  slot: '2027-01-02T11:00:00+05:30'
};

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('Add to calendar', () => {
  it('builds a Google Calendar template link in UTC with the booking details', () => {
    const url = new URL(googleCalendarUrl(booking, 'https://nirvana.example'));
    expect(url.origin + url.pathname).toBe('https://calendar.google.com/calendar/render');
    expect(url.searchParams.get('action')).toBe('TEMPLATE');
    expect(url.searchParams.get('text')).toBe('Deep tissue at Lotus & Stone');
    expect(url.searchParams.get('dates')).toBe('20270102T053000Z/20270102T070000Z');
    expect(url.searchParams.get('details')).toBe(
      'Booking reference: REF-8\nReschedule or cancel in My bookings: https://nirvana.example/me/bookings');
    expect(url.searchParams.get('location')).toBe('Lotus & Stone');
  });

  it('offers Google Calendar and the .ics download', () => {
    const html = renderToStaticMarkup(<AddToCalendar booking={booking} />);
    expect(html).toContain('Add to calendar');
    expect(html).toContain('Google Calendar');
    expect(html).toContain('Apple / Outlook (.ics)');
    expect(html).toContain('href="https://calendar.google.com/calendar/render?action=TEMPLATE');
    expect(html).toContain('rel="noopener noreferrer"');
  });

  it('downloads the invite from the signed-in guest endpoint', async () => {
    const get = vi.spyOn(client, 'get').mockResolvedValue({
      data: new Blob(['BEGIN:VCALENDAR']),
      headers: { 'content-disposition': 'attachment; filename="nirvana-booking-REF-8.ics"' }
    });
    const link = { href: '', download: '', click: vi.fn(), remove: vi.fn() };
    vi.stubGlobal('document', { createElement: () => link, body: { appendChild: vi.fn() } });
    vi.stubGlobal('URL', Object.assign(globalThis.URL, { createObjectURL: () => 'blob:invite', revokeObjectURL: vi.fn() }));
    await downloadCalendarInvite('8', 'REF-8');
    expect(get).toHaveBeenCalledWith('/api/me/bookings/8/calendar.ics', { responseType: 'blob' });
    expect(link.download).toBe('nirvana-booking-REF-8.ics');
    expect(link.href).toBe('blob:invite');
    expect(link.click).toHaveBeenCalled();
  });

  it('appears on a confirmed booking receipt but not while payment is pending', () => {
    expect(renderToStaticMarkup(<BookingConfirmation summary={summary} onRestart={() => {}} />)).toContain('Add to calendar');
    expect(renderToStaticMarkup(<BookingConfirmation summary={{ ...summary, status: 'PENDING_PAYMENT' }} onRestart={() => {}} />))
      .not.toContain('Add to calendar');
  });

  it('appears on upcoming bookings in My Bookings only', () => {
    const render = (changes: Partial<BookingSummary>) => {
      const queries = new QueryClient();
      queries.setQueryData(['my-bookings', 0], { items: [{ ...summary, ...changes }], totalPages: 1 });
      return renderToStaticMarkup(<QueryClientProvider client={queries}><MyBookings /></QueryClientProvider>);
    };
    expect(render({})).toContain('Add to calendar');
    expect(render({ status: 'CANCELLED' })).not.toContain('Add to calendar');
    expect(render({ slot: '2020-01-02T11:00:00+05:30' })).not.toContain('Add to calendar');
  });
});
