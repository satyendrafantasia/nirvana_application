export interface PaymentIntent {
  bookingId: number;
  razorpayKeyId: string;
  razorpayOrderId: string;
  amount: number;
  currency: string;
  description?: string;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
}
export interface RazorpayResult {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}
