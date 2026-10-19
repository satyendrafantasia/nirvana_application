import client from './client';
import { BookingPayload, BookingSummary, Slot } from '../types/booking';
import { PaymentIntent } from '../types/payment';
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
export interface CreatedBooking {
  bookingId: number;
  bookingReference: string;
  status: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  totalCents: number;
  currency: string;
  razorpay?: PaymentIntent;
}
export const createBooking = async (payload: BookingPayload): Promise<CreatedBooking> =>
  (await client.post('/api/bookings', payload)).data;
interface BookingWire {
  bookingId: number;
  bookingReference: string;
  spaId: number;
  spaName: string;
  serviceId: number;
  serviceName: string;
  serviceDurationMinutes: number;
  servicePriceCents: number;
  startTs: string;
  status: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  canCancel: boolean;
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
      canCancel: b.canCancel
      ,refundStatus:b.refundStatus
    }))
  };
};
export const cancelBooking = async (id: string) =>
  (await client.post(`/api/bookings/${id}/cancel`)).data;
export const rescheduleBooking = async (id: string, targetSlotId: string) =>
  (await client.post(`/api/bookings/${id}/reschedule`, { targetSlotId })).data;
