import { beforeEach, describe, it, expect, vi } from 'vitest';
import client from './client';
import { login, requestOtp, verifyOtp } from './authApi';
import { searchSpas, getSpaServices } from './spaApi';
import { getSlots, createBooking, getUserBookings, rescheduleBooking } from './bookingApi';
import { confirmPayment, initiatePayment } from './paymentApi';
let requests: {
  url?: string;
  method?: string;
  data: unknown;
  params: unknown;
  headers: unknown;
}[] = [];
let response: unknown = {};
beforeEach(() => {
  const values = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => values.get(k) || null,
    setItem: (k: string, v: string) => values.set(k, v),
    removeItem: (k: string) => values.delete(k)
  });
  vi.stubGlobal('sessionStorage', { getItem: () => null });
  requests = [];
  client.defaults.adapter = async (config) => {
    requests.push({
      url: config.url,
      method: config.method,
      data: config.data ? JSON.parse(config.data) : undefined,
      params: config.params,
      headers: config.headers
    });
    return { data: response, status: 200, statusText: 'OK', headers: {}, config };
  };
});
describe('Spring API contracts', () => {
  it('maps username/accessToken authentication', async () => {
    response = {
      accessToken: 'access',
      refreshToken: 'refresh',
      userId: 7,
      username: 'guest@example.test'
    };
    const r = await login({ email: 'guest@example.test', password: 'test' });
    expect(requests[0].data).toEqual({ username: 'guest@example.test', password: 'test' });
    expect(r.token).toBe('access');
    expect(r.user.id).toBe('7');
  });
  it('uses paged spa search and actual rating fields', async () => {
    response = { content: [{ id: 1, name: 'Spa', ratingAvg: 4.8, thumbnailImageUrl: '/photo' }] };
    const r = await searchSpas({ city: 'Pune' });
    expect(requests[0].url).toBe('/api/spas/search');
    expect(r[0]).toMatchObject({ id: '1', rating: 4.8, coverImage: '/photo' });
  });
  it('converts paise and hides inactive services', async () => {
    response = [
      {
        id: 3,
        name: 'Massage',
        priceCents: 125000,
        currency: 'INR',
        isActive: true,
        isVisibleOnMarketplace: true
      },
      { id: 4, isActive: false }
    ];
    expect(await getSpaServices('1')).toEqual([
      expect.objectContaining({ id: '3', price: 1250, currency: 'INR' })
    ]);
  });
  it('scopes slots to spa/service/date/guest count and keeps therapist availability', async () => {
    response = [
      {
        slotId: 9,
        startTs: '2026-10-20T10:00:00+05:30',
        endTs: '2026-10-20T11:00:00+05:30',
        status: 'AVAILABLE',
        remainingCapacityUnits: 1,
        therapists: [{ therapistId: 2, name: 'A', available: true }]
      }
    ];
    const slots = await getSlots('1', '3', '2026-10-20', 2);
    expect(requests[0]).toMatchObject({
      url: '/api/spas/1/services/3/slots',
      params: { date: '2026-10-20', guests: 2 }
    });
    expect(slots[0]).toMatchObject({
      id: '9',
      available: false,
      therapists: [expect.objectContaining({ id: '2' })]
    });
    expect((await getSlots('1', '3', '2026-10-20', 1))[0].available).toBe(true);
  });
  it('submits slot ID, guests and OFFLINE without trusting a browser price', async () => {
    response = { bookingId: 8 };
    await createBooking({
      spaId: '1',
      serviceId: '3',
      slotId: '9',
      guests: 1,
      paymentMode: 'OFFLINE'
    });
    expect(requests[0].data).toEqual({
      spaId: '1',
      serviceId: '3',
      slotId: '9',
      guests: 1,
      paymentMode: 'OFFLINE'
    });
  });
  it('unwraps nested bookings pagination and preserves cancellation eligibility', async () => {
    response = {
      page: {
        totalPages: 2,
        content: [
          {
            bookingId: 8,
            spaId: 1,
            spaName: 'Spa',
            serviceId: 3,
            serviceName: 'Massage',
            servicePriceCents: 125000,
            status: 'CONFIRMED',
            canCancel: false
          }
        ]
      }
    };
    const r = await getUserBookings(1);
    expect(r.totalPages).toBe(2);
    expect(r.items[0]).toMatchObject({
      id: '8',
      canCancel: false,
      service: { id: '3', price: 1250 }
    });
    expect(requests[0].params).toEqual({ page: 1, size: 20 });
  });
  it('reschedules using targetSlotId', async () => {
    await rescheduleBooking('8', '10');
    expect(requests[0]).toMatchObject({
      url: '/api/bookings/8/reschedule',
      data: { targetSlotId: '10' }
    });
  });
  it('uses booking-scoped Razorpay order and exact signed callback fields', async () => {
    await initiatePayment('8');
    await confirmPayment('8', {
      razorpay_order_id: 'order_1',
      razorpay_payment_id: 'pay_1',
      razorpay_signature: 'signature'
    });
    expect(requests[0].url).toBe('/api/bookings/8/payments/razorpay/order');
    expect(requests[1]).toMatchObject({
      url: '/api/bookings/8/payments/razorpay/confirm',
      data: {
        razorpay_order_id: 'order_1',
        razorpay_payment_id: 'pay_1',
        razorpay_signature: 'signature'
      }
    });
  });
  it('uses OTP verification ID followed by login completion', async () => {
    response = { verificationId: 'verify_1' };
    expect(await requestOtp('guest@example.test')).toEqual(response);
    response = {
      loginToken: 'login_1',
      accessToken: 'access',
      userId: 7,
      username: 'guest@example.test'
    };
    await verifyOtp('verify_1', '123456');
    expect(requests.map((r) => r.url)).toEqual([
      '/api/auth/login/otp/request',
      '/api/auth/login/otp/verify',
      '/api/auth/login/otp/complete'
    ]);
    expect(requests[1].data).toEqual({ verificationId: 'verify_1', otp: '123456' });
    expect(requests[2].data).toEqual({ loginToken: 'login_1' });
  });
});
