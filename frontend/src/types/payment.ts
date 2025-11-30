export interface PaymentIntent {
  bookingId: string;
  paymentRef: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED';
  redirectUrl?: string;
}
