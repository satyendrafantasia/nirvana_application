import axios from 'axios';
import client from './client';
import { BookingPayload, BookingSummary, Slot } from '../types/booking';
import { PaymentIntent } from '../types/payment';

// The availability service returns this message for room, capacity and therapist conflicts.
// Other 409s (e.g. package eligibility) can be resolved on the payment step.
export function isSlotConflict(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false;
  const message = error.response?.data?.message;
  if (typeof message !== 'string') return false;
  return (error.response?.status === 409 && (message.startsWith('This time is no longer available') ||
    message === 'Choose the therapist reserved by this hold')) ||
    (error.response?.status === 404 && message.startsWith('Slot not found:'));
}
export const getSlots = async (
  spaId: string,
  serviceId: string,
  date: string,
  guests = 1
): Promise<Slot[]> => {
  const { data } = await client.get<
    Array<{
      slotId: number;
      startTs: string;
      endTs: string;
      remainingCapacityUnits: number;
      status: string;
      therapistSelectionEnabled: boolean;
      therapistTypeSelectionEnabled: boolean;
      therapists?: {
        therapistId: number;
        name: string;
        displayName?: string;
        type: string;
        available: boolean;
      }[];
      therapistTypes?: { type: string; availableCount: number }[];
    }>
  >(`/api/spas/${spaId}/services/${serviceId}/slots`, { params: { date, guests } });
  return data.map((s) => ({
    ...s,
    id: String(s.slotId),
    startTime: s.startTs,
    endTime: s.endTs,
    available: s.status === 'AVAILABLE' && s.remainingCapacityUnits >= guests,
    therapists: (s.therapists || []).map((t) => ({
      ...t,
      id: String(t.therapistId),
      name: t.displayName || t.name
    })),
    therapistTypes: s.therapistTypes || []
  }));
};
export interface TherapistDirectory {
  named: { id: number; name: string; type?: string }[];
  labels: string[];
}
export const getTherapistDirectory = async (spa: string, service: string) =>
  (await client.get<TherapistDirectory>(`/api/spas/${spa}/services/${service}/therapists`)).data;
export interface CreatedBooking {
  therapistName?: string;
  bookingId: number;
  bookingReference: string;
  status: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  totalCents: number;
  currency: string;
  /** Paid from a gift card; totalCents is what is still to pay. */
  walletCents?: number;
  giftCardCents?: number;
  loyaltyPointsRedeemed?: number;
  razorpay?: PaymentIntent;
}
export const createBooking = async (payload: BookingPayload): Promise<CreatedBooking> => {
  // Store only a digest and a random key. A renewed slot hold belongs to the same attempt.
  const signature = JSON.stringify({ ...payload, holdToken: undefined });
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(signature));
  const storageKey = 'nirvana.checkout.' + Array.from(new Uint8Array(digest), n => n.toString(16).padStart(2,'0')).join('');
  const requestKey = localStorage.getItem(storageKey) || crypto.randomUUID();
  localStorage.setItem(storageKey, requestKey);
  localStorage.setItem('nirvana.pendingCheckout',JSON.stringify({key:requestKey,spa:payload.spaId}));
  const response = await client.post<CreatedBooking>('/api/bookings', { ...payload, requestKey });
  localStorage.removeItem('nirvana.pendingCheckout');
  localStorage.removeItem(storageKey);
  return response.data;
};
interface BookingWire {
  originalPreference?: string;
  therapistName?: string;
  bookingId: number;
  bookingReference: string;
  spaId: number;
  spaName: string;
  serviceId: number;
  serviceName: string;
  serviceDurationMinutes: number;
  servicePriceCents: number;
  payAtSpaCheckoutCents?: number | null;
  currency?: string;
  startTs: string;
  status: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  canCancel: boolean;
  invoiceAvailable?: boolean;
  spaAcknowledged?: boolean;
  refundStatus?: string;
}
export const getUserBookings = async (
  page = 0
): Promise<{ items: BookingSummary[]; totalPages: number }> => {
  const { data } = await client.get<{ page: { content: BookingWire[]; totalPages: number } }>(
    '/api/me/bookings',
    { params: { page, size: 20 } }
  );
  return {
    totalPages: data.page.totalPages,
    items: data.page.content.map((b) => ({
      id: String(b.bookingId),
      reference: b.bookingReference,
      therapistName: b.therapistName,
      originalPreference: b.originalPreference,
      spaId: String(b.spaId),
      spaName: b.spaName,
      service: {
        id: String(b.serviceId),
        name: b.serviceName,
        durationMinutes: b.serviceDurationMinutes,
        price: b.servicePriceCents / 100,
        currency: 'INR'
      },
      slot: b.startTs,
      status: b.status,
      paymentMode: b.paymentMode,
      payAtSpaCheckoutCents: b.payAtSpaCheckoutCents ?? undefined,
      currency: b.currency,
      invoiceAvailable: b.invoiceAvailable,
      canCancel: b.canCancel,
      spaAcknowledged: b.spaAcknowledged,
      refundStatus: b.refundStatus
    }))
  };
};
export interface CancellationQuote {
  walletFeeCents?: number;
  walletRefundCents?: number;
  giftCardFeeCents?: number;
  giftCardRefundCents?: number;
  feeCents: number;
  refundCents: number;
  currency: string;
  route: 'WALLET_CREDIT' | 'ORIGINAL_METHOD';
  packageSession: boolean;
}
export const getCancellationQuote = async (id: string) =>
  (await client.get<CancellationQuote>(`/api/bookings/${id}/cancellation-quote`)).data;
export const cancelBooking = async (id: string, quote: CancellationQuote) =>
  (await client.post(`/api/bookings/${id}/cancel`, quote)).data;
export const rescheduleBooking = async (id: string, targetSlotId: string, choice?: string) =>
  (await client.post(`/api/bookings/${id}/reschedule`, { targetSlotId,
    changeTherapistPreference: choice !== undefined,
    therapistId: choice && !choice.startsWith('type:') ? Number(choice) : undefined,
    therapistType: choice?.startsWith('type:') ? choice.slice(5) : undefined })).data;
