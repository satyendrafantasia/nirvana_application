import client from './client';
import { BookingPayload, BookingSummary, Slot, Therapist } from '../types/booking';

export const getSlots = async (spaId: string, date: string) => {
  const { data } = await client.get<Slot[]>(`/api/spas/${spaId}/slots`, { params: { date } });
  return data;
};

export const getTherapists = async (spaId: string) => {
  const { data } = await client.get<Therapist[]>(`/api/spas/${spaId}/therapists`);
  return data;
};

export const createBooking = async (payload: BookingPayload) => {
  const { data } = await client.post<BookingSummary>('/api/bookings', payload);
  return data;
};

export const getUserBookings = async () => {
  const { data } = await client.get<BookingSummary[]>('/api/me/bookings');
  return data;
};

export const cancelBooking = async (bookingId: string) => {
  const { data } = await client.post(`/api/bookings/${bookingId}/cancel`);
  return data;
};
