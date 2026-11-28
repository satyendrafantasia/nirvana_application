import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { BookingContext } from '../contexts/BookingContext';
import ResumeCheckout from '../pages/Booking/ResumeCheckout';
import type { Slot } from '../types/booking';
import type { Service } from '../types/spa';
import { applyPrefill, CheckoutResume, PrefillTarget, resumePath, resumePrefill } from './checkoutRecoveryApi';

const service: Service = { id: '7', name: 'Deep Tissue Massage', durationMinutes: 60, price: 2500, currency: 'INR', minPersons: 1, maxPersons: 2 };

const resume: CheckoutResume = {
  spaId: 3,
  spaName: 'Lotus Spa',
  serviceId: 7,
  serviceName: 'Deep Tissue Massage',
  serviceAvailable: true,
  guests: 2,
  therapistId: '12',
  therapistType: null,
  therapistName: 'Meera',
  date: '2026-12-02',
  originalStart: '2026-12-02T11:30:00+05:30',
  originalSlotId: 501,
  originalAvailable: true,
  pendingBookingReference: null,
  alternatives: []
};

const slot = (id: string, startTime: string, available = true): Slot => ({
  id, startTime, endTime: startTime, available,
  therapistSelectionEnabled: true, therapistTypeSelectionEnabled: false, therapists: [], therapistTypes: []
});

function recorder() {
  const calls: Array<[string, unknown[]]> = [];
  const record = (name: string) => (...args: unknown[]) => { calls.push([name, args]); };
  const flow: PrefillTarget = {
    setSpa: record('setSpa'), setService: record('setService'), setAvailabilityDate: record('setAvailabilityDate'),
    setGuests: record('setGuests'), setTherapist: record('setTherapist'), setSlot: record('setSlot')
  };
  return { calls, flow };
}

describe('checkout reminder resume link', () => {
  it('points at the booking flow with only the opaque token in the URL', () => {
    expect(resumePath('abc.DEF_-1')).toBe('/booking/resume/abc.DEF_-1');
    expect(resumePath('a/b')).toBe('/booking/resume/a%2Fb');
  });

  it('prefills the same spa, service, therapist, guests, date and the original time when it is still free', () => {
    const original = slot('501', '2026-12-02T11:30:00+05:30');
    const prefill = resumePrefill(resume, [service], [slot('500', '2026-12-02T10:30:00+05:30'), original]);
    expect(prefill).toEqual({
      spaId: '3', spaName: 'Lotus Spa', service, availabilityDate: '2026-12-02', guests: 2,
      therapistId: '12', therapistType: undefined, therapistName: 'Meera', slot: original
    });

    const { calls, flow } = recorder();
    applyPrefill(prefill!, flow);
    expect(calls.map(([name]) => name)).toEqual(['setSpa', 'setService', 'setAvailabilityDate', 'setGuests', 'setTherapist', 'setSlot']);
    expect(calls[0][1]).toEqual(['3', 'Lotus Spa']);
    expect(calls[4][1]).toEqual(['12', undefined, 'Meera']);
    expect(calls[5][1]).toEqual([original]);
  });

  it('leaves the time open when the original was taken, and uses the alternative the guest picks', () => {
    const taken = { ...resume, originalAvailable: false, originalSlotId: null,
      alternatives: [{ slotId: 777, startTs: '2026-12-03T12:00:00+05:30', date: '2026-12-03' }] };
    const open = resumePrefill(taken, [service], [slot('501', resume.originalStart)]);
    expect(open?.slot).toBeUndefined();
    expect(open?.availabilityDate).toBe('2026-12-02');
    const { calls, flow } = recorder();
    applyPrefill(open!, flow);
    expect(calls.some(([name]) => name === 'setSlot')).toBe(false);

    const picked = resumePrefill(taken, [service], [slot('777', '2026-12-03T12:00:00+05:30')], taken.alternatives[0]);
    expect(picked?.slot?.id).toBe('777');
    expect(picked?.availabilityDate).toBe('2026-12-03');
  });

  it('keeps a therapist type choice, clamps guests and gives up on a service that is gone', () => {
    const byType = resumePrefill({ ...resume, therapistId: null, therapistType: 'Female therapist', therapistName: null, guests: 6 }, [service]);
    expect(byType?.therapistType).toBe('Female therapist');
    expect(byType?.therapistId).toBeUndefined();
    expect(byType?.guests).toBe(2);
    expect(resumePrefill(resume, [])).toBeNull();
    expect(resumePrefill({ ...resume, serviceAvailable: false }, [service])).toBeNull();
    // A slot that has filled up since is not preselected.
    expect(resumePrefill(resume, [service], [slot('501', resume.originalStart, false)])?.slot).toBeUndefined();
  });

  it('shows the nearest free times when the original time was taken', () => {
    const token = 'tok.sig';
    const client = new QueryClient();
    client.setQueryData(['checkout-resume', token], {
      ...resume, originalAvailable: false, originalSlotId: null,
      alternatives: [{ slotId: 777, startTs: '2026-12-02T12:30:00+05:30', date: '2026-12-02' }]
    });
    client.setQueryData(['booking-services', '3'], [service]);
    const noop = vi.fn();
    const html = renderToStaticMarkup(
      <QueryClientProvider client={client}>
        <BookingContext.Provider value={{ state: { guests: 1 }, setHold: noop, setSpa: noop, setService: noop,
          setAvailabilityDate: noop, setSlot: noop, setGuests: noop, setTherapist: noop, setPaymentMode: noop, setNotes: noop, reset: noop }}>
          <MemoryRouter initialEntries={[resumePath(token)]}>
            <Routes><Route path="/booking/resume/:token" element={<ResumeCheckout />} /></Routes>
          </MemoryRouter>
        </BookingContext.Provider>
      </QueryClientProvider>
    );
    expect(html).toContain('Deep Tissue Massage at Lotus Spa with Meera');
    expect(html).toContain('has been taken');
    expect(html).toContain('Nearest free times');
    expect(html).toContain('See all times');
  });
});
