import { beforeEach, it, expect, vi } from 'vitest';
import client from './client';
import {
  listPackages,
  myPackages,
  startPackage,
  confirmPackage,
  payForPackage,
  PackageCheckout
} from './packageApi';
const load = vi.hoisted(() => vi.fn());
vi.mock('./paymentApi', () => ({ loadRazorpay: load }));
let requests: { url?: string; data: unknown }[] = [];
let response: unknown = [];
beforeEach(() => {
  requests = [];
  load.mockReset();
  const saved = new Map<string, string>();
  vi.stubGlobal('localStorage', { getItem: () => null });
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => saved.get(k),
    setItem: (k: string, v: string) => saved.set(k, v),
    removeItem: (k: string) => saved.delete(k)
  });
  client.defaults.adapter = async (config) => {
    requests.push({ url: config.url, data: config.data ? JSON.parse(config.data) : null });
    return { data: response, status: 200, statusText: 'OK', headers: {}, config };
  };
});
it('uses catalog, owned purchases and server-priced checkout routes', async () => {
  await listPackages();
  await myPackages();
  await startPackage(4);
  expect(requests.map((r) => r.url)).toEqual([
    '/api/packages/catalog',
    '/api/packages/mine',
    '/api/packages/4/checkout'
  ]);
  expect(requests[2].data).toBeNull();
});
it('posts exact signed result to the owned subscription', async () => {
  const result = {
    razorpay_order_id: 'order',
    razorpay_payment_id: 'pay',
    razorpay_signature: 'sig'
  };
  await confirmPackage(9, result);
  expect(requests[0]).toEqual({ url: '/api/packages/mine/9/confirm', data: result });
});
const intent: PackageCheckout = {
  purchase: {
    subscriptionId: 9,
    packageId: 4,
    packageType: 'GLOBAL_BRONZE',
    status: 'PAYMENT_PENDING',
    paymentStatus: 'PENDING',
    remainingSessions: 0,
    sessions: 5,
    amountCents: 20000,
    currency: 'INR',
    expiryDate: null
  },
  razorpayKeyId: 'key',
  razorpayOrderId: 'order',
  amountCents: 20000,
  currency: 'INR'
};
it('retries saved verification without opening a second checkout', async () => {
  sessionStorage.setItem(
    'nirvana_package_9',
    JSON.stringify({
      razorpay_order_id: 'order',
      razorpay_payment_id: 'pay',
      razorpay_signature: 'sig'
    })
  );
  response = { ...intent.purchase, status: 'ACTIVE', remainingSessions: 5 };
  expect((await payForPackage(intent)).status).toBe('ACTIVE');
  expect(load).not.toHaveBeenCalled();
  expect(sessionStorage.getItem('nirvana_package_9')).toBeUndefined();
});
it('does not charge again when server reconciliation finds an active purchase', async () => {
  expect(
    (await payForPackage({ ...intent, purchase: { ...intent.purchase, status: 'ACTIVE' } })).status
  ).toBe('ACTIVE');
  expect(load).not.toHaveBeenCalled();
  expect(requests).toHaveLength(0);
});
