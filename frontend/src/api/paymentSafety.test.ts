import { beforeEach, expect, it, vi } from 'vitest';
import client from './client';
import { payWithRazorpay, resumePayment } from './paymentApi';
import { PaymentIntent, RazorpayResult } from '../types/payment';
const intent: PaymentIntent = {
  bookingId: 1,
  razorpayKeyId: 'test',
  razorpayOrderId: 'order_1',
  amount: 1250,
  currency: 'INR'
};
let callback: (r: RazorpayResult) => void;
let options: Record<string, unknown>;
let listeners: Record<string, (r?: unknown) => void>;
const result = {
  razorpay_order_id: 'order_1',
  razorpay_payment_id: 'pay_1',
  razorpay_signature: 'signature'
};
beforeEach(() => {
  listeners = {};
  const values = new Map<string, string>();
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => values.get(k) || null,
    setItem: (k: string, v: string) => values.set(k, v),
    removeItem: (k: string) => values.delete(k)
  });
  vi.stubGlobal('localStorage', { getItem: () => null });
  vi.stubGlobal('window', {
    Razorpay: class {
      constructor(o: Record<string, unknown>) {
        options = o;
        callback = o.handler as typeof callback;
      }
      open() {}
      on(event: string, handler: (r?: unknown) => void) {
        listeners[event] = handler;
      }
    }
  });
});
it('converts major-unit amount and waits for backend confirmation', async () => {
  client.defaults.adapter = async (config) => ({
    data: { status: 'CONFIRMED', refundStatus: 'NONE' },
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  });
  const pending = payWithRazorpay(intent);
  await Promise.resolve();
  expect(options.amount).toBe(125000);
  callback(result);
  expect(await pending).toEqual({ status: 'CONFIRMED', refundStatus: 'NONE' });
});
it('retries verification without opening another checkout after a failed confirmation', async () => {
  client.defaults.adapter = async () => {
    throw new Error('Capture pending');
  };
  const pending = payWithRazorpay(intent);
  await Promise.resolve();
  const rejected = expect(pending).rejects.toThrow('Capture pending');
  callback(result);
  await rejected;
  const checkout = vi.fn();
  vi.stubGlobal('window', { Razorpay: checkout });
  client.defaults.adapter = async (config) => ({
    data: { status: 'CONFIRMED', refundStatus: 'NONE' },
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  });
  expect(await resumePayment('1')).toMatchObject({ status: 'CONFIRMED' });
  expect(checkout).not.toHaveBeenCalled();
});
it('preserves server cancellation status after a late capture', async () => {
  client.defaults.adapter = async (config) => ({
    data: { status: 'CANCELLED', refundStatus: 'REQUESTED' },
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  });
  const pending = payWithRazorpay(intent);
  await Promise.resolve();
  callback(result);
  expect(await pending).toEqual({ status: 'CANCELLED', refundStatus: 'REQUESTED' });
});
it('keeps waiting after a failed attempt, so a later success in the same window confirms the booking', async () => {
  client.defaults.adapter = async (config) => ({
    data: { status: 'CONFIRMED', refundStatus: 'NONE' },
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  });
  const pending = payWithRazorpay(intent);
  await Promise.resolve();
  // An international test card is declined; Razorpay keeps the window open for another try.
  listeners['payment.failed']({ error: { description: 'This business accepts domestic (Indian) card payments only.' } });
  callback(result);
  expect(await pending).toEqual({ status: 'CONFIRMED', refundStatus: 'NONE' });
});
it('says why it failed when the guest gives up after a failed attempt', async () => {
  const pending = payWithRazorpay(intent);
  await Promise.resolve();
  listeners['payment.failed']({ error: { description: 'Card declined.' } });
  (options.modal as { ondismiss: () => void }).ondismiss();
  await expect(pending).rejects.toThrow('Payment failed: Card declined. Your booking is saved');
});
