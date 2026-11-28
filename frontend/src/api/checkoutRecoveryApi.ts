import client from './client';
import type { Service } from '../types/spa';
import type { Slot } from '../types/booking';

/** A free time offered when the guest's original time has been taken. */
export interface AlternativeTime {
  slotId: number;
  startTs: string;
  date: string;
}

/** What GET /api/me/checkout-recovery/{token} returns: the abandoned checkout's choices. */
export interface CheckoutResume {
  spaId: number;
  spaName: string;
  serviceId: number;
  serviceName: string;
  serviceAvailable: boolean;
  guests: number;
  therapistId?: string | null;
  therapistType?: string | null;
  therapistName?: string | null;
  /** The appointment's day in the spa's time zone (YYYY-MM-DD), never before today. */
  date: string;
  originalStart: string;
  originalSlotId?: number | null;
  originalAvailable: boolean;
  /** Set while the guest's own unpaid booking still holds the time. */
  pendingBookingReference?: string | null;
  alternatives: AlternativeTime[];
}

export interface RecoveryStats {
  from: string;
  to: string;
  sent: number;
  failed: number;
  clicked: number;
  converted: number;
  recoveredCents: number;
  clickRate: number;
  conversionRate: number;
}

/** Path of the link in the reminder; the token is opaque and signed. */
export const resumePath = (token: string) => `/booking/resume/${encodeURIComponent(token)}`;

/** Opening the link records the click. */
export const getCheckoutResume = async (token: string) =>
  (await client.get<CheckoutResume>(`/api/me/checkout-recovery/${encodeURIComponent(token)}`)).data;

export const stopCheckoutReminders = async (token: string) =>
  (await client.post<{ remindersEnabled: boolean }>('/api/public/checkout-reminders/unsubscribe', null, { params: { t: token } })).data;

export const restartCheckoutReminders = async (token: string) =>
  (await client.post<{ remindersEnabled: boolean }>('/api/public/checkout-reminders/resubscribe', null, { params: { t: token } })).data;

export const recoveryStats = async (spa?: number | string, from?: string, to?: string) =>
  (
    await client.get<RecoveryStats>(
      spa === undefined ? '/api/admin/checkout-recovery/stats' : `/api/owner/spas/${spa}/checkout-recovery/stats`,
      { params: { from, to } }
    )
  ).data;

/** The booking-flow choices to restore, in the shape of the booking context. */
export interface ResumePrefill {
  spaId: string;
  spaName: string;
  service: Service;
  availabilityDate: string;
  guests: number;
  therapistId?: string;
  therapistType?: string;
  therapistName?: string;
  slot?: Slot;
}

/**
 * Turns the resume response into booking-flow state: the same spa, service, guests, therapist
 * choice and day, and the original time when it is still free. {@code chosen} is an alternative
 * the guest picked instead. Returns null when the service can no longer be booked.
 */
export function resumePrefill(
  resume: CheckoutResume,
  services: Service[],
  slotsForDay: Slot[] = [],
  chosen?: AlternativeTime
): ResumePrefill | null {
  const service = services.find((s) => s.id === String(resume.serviceId));
  if (!resume.serviceAvailable || !service) return null;
  const min = service.minPersons || 1;
  const max = Math.max(min, service.maxPersons || 1);
  const wantedSlot = chosen ? chosen.slotId : resume.originalAvailable ? resume.originalSlotId : undefined;
  const slot = wantedSlot == null ? undefined : slotsForDay.find((s) => s.id === String(wantedSlot) && s.available);
  return {
    spaId: String(resume.spaId),
    spaName: resume.spaName,
    service,
    availabilityDate: chosen ? chosen.date : resume.date,
    guests: Math.min(max, Math.max(min, resume.guests || 1)),
    therapistId: resume.therapistId || undefined,
    therapistType: resume.therapistId ? undefined : resume.therapistType || undefined,
    therapistName: resume.therapistName || undefined,
    slot
  };
}

/** The booking-context setters resumePrefill's result is applied through. */
export interface PrefillTarget {
  setSpa: (spaId: string, spaName?: string) => void;
  setService: (service: Service) => void;
  setAvailabilityDate: (date: string) => void;
  setGuests: (guests: number) => void;
  setTherapist: (id?: string, type?: string, publicName?: string) => void;
  setSlot: (slot?: Slot) => void;
}

/** Each setter clears what follows it, so the order matters: the slot comes last. */
export function applyPrefill(prefill: ResumePrefill, flow: PrefillTarget) {
  flow.setSpa(prefill.spaId, prefill.spaName);
  flow.setService(prefill.service);
  flow.setAvailabilityDate(prefill.availabilityDate);
  flow.setGuests(prefill.guests);
  flow.setTherapist(prefill.therapistId, prefill.therapistType, prefill.therapistName);
  if (prefill.slot) flow.setSlot(prefill.slot);
}
