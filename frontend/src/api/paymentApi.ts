import client from './client';
import { PaymentIntent } from '../types/payment';

export const initiatePayment = async (bookingId: string, method: string) => {
  const { data } = await client.post<PaymentIntent>(`/api/payments/initiate`, { bookingId, method });
  return data;
};

export const confirmPayment = async (paymentRef: string) => {
  const { data } = await client.post<PaymentIntent>('/api/payments/confirm', { paymentRef });
  return data;
};
