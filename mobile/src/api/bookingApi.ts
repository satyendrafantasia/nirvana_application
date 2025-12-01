import client from './client';
import { BookingPayload, BookingSummary, Slot, Therapist } from '../types/booking';

export const getSlots = (spaId: string, params: { serviceId: string; date: string }) =>
  client
    .get<Slot[]>(`/spas/${spaId}/slots`, { params })
    .then(res => res.data);

export const getTherapists = (spaId: string) =>
  client.get<Therapist[]>(`/spas/${spaId}/therapists`).then(res => res.data);

export const createBooking = (payload: BookingPayload) =>
  client.post<{ id: string }>(`/bookings`, payload).then(res => res.data);

export const getUserBookings = () =>
  client.get<BookingSummary[]>('/bookings/me').then(res => res.data);

export const cancelBooking = (id: string) =>
  client.post(`/bookings/${id}/cancel`).then(res => res.data);
