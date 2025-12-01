export interface PaymentInitResponse {
  paymentId: string;
  status: 'PENDING' | 'REQUIRES_ACTION' | 'COMPLETED';
  redirectUrl?: string;
}

export interface PaymentConfirmationPayload {
  paymentId: string;
  status: 'COMPLETED' | 'FAILED';
  metadata?: Record<string, string>;
}
