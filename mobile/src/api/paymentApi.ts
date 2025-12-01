import client from './client';
import { PaymentConfirmationPayload, PaymentInitResponse } from '../types/payment';

export const initiatePayment = (bookingId: string, method: 'ONLINE' | 'PAY_AT_SPA') =>
  client
    .post<PaymentInitResponse>(`/payments/initiate`, { bookingId, method })
    .then(res => res.data);

export const confirmPayment = (payload: PaymentConfirmationPayload) =>
  client.post(`/payments/confirm`, payload).then(res => res.data);
