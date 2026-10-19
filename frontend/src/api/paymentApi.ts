import client from './client';
import { PaymentIntent, RazorpayResult } from '../types/payment';
export const initiatePayment = async (id: string) =>
  (
    await client.post<PaymentIntent>(
      `/api/bookings/${id}/payments/razorpay/order`,
      {},
      { headers: { 'Idempotency-Key': `web-booking-${id}` } }
    )
  ).data;
export const confirmPayment = async (id: string, result: RazorpayResult) => {
  return (
    await client.post<{ status: string; refundStatus: string }>(
      `/api/bookings/${id}/payments/razorpay/confirm`,
      result
    )
  ).data;
};
export const resumePayment = async (id: string) => {
  const saved = sessionStorage.getItem(`nirvana_payment_${id}`);
  if (saved) {
    const status = await confirmPayment(id, JSON.parse(saved));
    sessionStorage.removeItem(`nirvana_payment_${id}`);
    return status;
  }
  return payWithRazorpay(await initiatePayment(id));
};
type Checkout = { open: () => void; on: (event: string, handler: () => void) => void };
declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => Checkout;
  }
}
let scriptPromise: Promise<void> | undefined;
export const payWithRazorpay = async (
  intent: PaymentIntent
): Promise<{ status: string; refundStatus: string }> => {
  const saved = sessionStorage.getItem(`nirvana_payment_${intent.bookingId}`);
  if (saved) {
    const status = await confirmPayment(String(intent.bookingId), JSON.parse(saved));
    sessionStorage.removeItem(`nirvana_payment_${intent.bookingId}`);
    return status;
  }
  const Razorpay = await loadRazorpay();
  return new Promise((resolve, reject) => {
    const checkout = new Razorpay({
      key: intent.razorpayKeyId,
      order_id: intent.razorpayOrderId,
      amount: Math.round(Number(intent.amount) * 100),
      currency: intent.currency,
      name: 'Nirvana',
      description: intent.description,
      prefill: {
        name: intent.customerName,
        email: intent.customerEmail,
        contact: intent.customerPhone
      },
      handler: async (result: RazorpayResult) => {
        sessionStorage.setItem(`nirvana_payment_${intent.bookingId}`, JSON.stringify(result));
        try {
          const status = await confirmPayment(String(intent.bookingId), result);
          sessionStorage.removeItem(`nirvana_payment_${intent.bookingId}`);
          resolve(status);
        } catch (e) {
          reject(e);
        }
      },
      modal: {
        ondismiss: () =>
          reject(
            new Error(
              'Payment was not completed. Your booking is saved; retry payment here or in My bookings.'
            )
          )
      }
    });
    checkout.on('payment.failed', () =>
      reject(new Error('Payment failed. You can retry the existing booking.'))
    );
    checkout.open();
  });
};

export async function loadRazorpay() {
  if (!window.Razorpay) {
    scriptPromise ??= new Promise<void>((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://checkout.razorpay.com/v1/checkout.js';
      s.onload = () => resolve();
      s.onerror = () => {
        scriptPromise = undefined;
        s.remove();
        reject(new Error('Unable to load Razorpay. Please retry.'));
      };
      document.head.append(s);
    });
    await scriptPromise;
  }
  if (!window.Razorpay) throw new Error('Razorpay is unavailable');
  return window.Razorpay;
}
