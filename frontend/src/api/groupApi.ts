import client from './client';
import { PaymentIntent } from '../types/payment';
import { CancellationQuote } from './bookingApi';

/** Couples and group bookings: one booker, 2 to 6 guests, one start time, a booking per guest. */
export const MAX_GROUP = 6;

export interface GuestChoice {
  name: string;
  phone?: string;
  serviceId?: string;
  /** A named therapist (number as string) or a label as `type:<label>`; empty means anyone. */
  preference?: string;
}

export interface GroupTime {
  startTs: string;
  endTs: string;
}

export interface GroupMember {
  bookingId: number;
  bookingReference: string;
  position: number;
  guestName: string;
  booker: boolean;
  serviceId: number;
  serviceName: string;
  therapistName?: string;
  startTs: string;
  endTs: string;
  status: string;
  priceCents: number;
  discountCents: number;
  taxCents: number;
  giftCardCents: number;
  payableCents: number;
}

export interface GroupView {
  groupId: number;
  spaId: number;
  spaName: string;
  size: number;
  startTs: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  currency: string;
  totalCents: number;
  paymentStatus: string;
  members: GroupMember[];
  razorpay?: PaymentIntent;
}

export interface GroupQuote {
  groupId: number;
  currency: string;
  refundCents: number;
  feeCents: number;
  members: { bookingId: number; guestName: string; quote: CancellationQuote }[];
}

export interface DeskGroup {
  groupId: number;
  size: number;
  bookerName?: string;
  members: { bookingId: number; position: number; guestName: string; guestPhone?: string; booker: boolean }[];
}

function preference(choice: GuestChoice) {
  const p = choice.preference || '';
  return {
    therapistId: p && !p.startsWith('type:') ? Number(p) : undefined,
    therapistType: p.startsWith('type:') ? p.slice(5) : undefined
  };
}

export const guestRequests = (guests: GuestChoice[]) =>
  guests.map((g) => ({ serviceId: Number(g.serviceId), ...preference(g) }));

/** Every guest has a service, and nobody shares a named therapist. */
export function readyForTimes(guests: GuestChoice[]) {
  if (guests.length < 2 || guests.some((g) => !g.serviceId)) return false;
  const named = guests.map((g) => g.preference).filter((p): p is string => !!p && !p.startsWith('type:'));
  return new Set(named).size === named.length;
}

export const getGroupTimes = async (spaId: string, date: string, guests: GuestChoice[]) =>
  (await client.post<GroupTime[]>(`/api/spas/${spaId}/group-availability`, { date, guests: guestRequests(guests) })).data;

export const getSideBySide = async (spaId: string) =>
  (await client.get<Record<string, number>>(`/api/spas/${spaId}/side-by-side-services`)).data;

export const holdGroup = async (spaId: string, startTs: string, guests: GuestChoice[]) =>
  (
    await client.post<{ holdToken: string; expiresAt: string; therapists: (string | null)[] }>('/api/booking-groups/holds', {
      spaId: Number(spaId),
      startTs,
      guests: guestRequests(guests)
    })
  ).data;

export interface GroupCheckout {
  spaId: string;
  startTs: string;
  holdToken?: string;
  paymentMode: 'ONLINE' | 'OFFLINE';
  couponCode?: string;
  redeemLoyaltyPoints?: boolean;
  giftCardCode?: string;
  specialRequest?: string;
  requestKey: string;
  guests: GuestChoice[];
}

export const createGroup = async (c: GroupCheckout) =>
  (
    await client.post<GroupView>('/api/booking-groups', {
      ...c,
      spaId: Number(c.spaId),
      guests: c.guests.map((g, i) => ({
        name: g.name.trim() || (i === 0 ? undefined : ''),
        phone: g.phone?.trim() || undefined,
        serviceId: Number(g.serviceId),
        ...preference(g)
      }))
    })
  ).data;

export const getGroup = async (id: number) => (await client.get<GroupView>(`/api/booking-groups/${id}`)).data;
export const getMyGroups = async () => (await client.get<GroupView[]>('/api/me/booking-groups')).data;
export const getGroupQuote = async (id: number) =>
  (await client.get<GroupQuote>(`/api/booking-groups/${id}/cancellation-quote`)).data;
export const cancelGroup = async (id: number, expectedRefundCents: number) =>
  (await client.post<GroupQuote>(`/api/booking-groups/${id}/cancel`, { expectedRefundCents })).data;

export const getSpaGroups = async (spaId: string, from: string, to: string) =>
  (await client.get<DeskGroup[]>(`/api/owner/spas/${spaId}/booking-groups`, { params: { from, to } })).data;

/** The group a booking belongs to, with that guest's seat. */
export function findMember<G extends { members: { bookingId: number }[] }>(
  groups: G[] | undefined,
  bookingId: string | number
): { group: G; member: G['members'][number] } | undefined {
  for (const group of groups || []) {
    const member: G['members'][number] | undefined = group.members.find((m) => String(m.bookingId) === String(bookingId));
    if (member) return { group, member };
  }
  return undefined;
}
