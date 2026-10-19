import client from './client';
import { loadRazorpay } from './paymentApi';
import { RazorpayResult } from '../types/payment';
export interface CatalogPackage {
  id: number;
  packageType: string;
  description: string;
  amountCents: number;
  currency: string;
  sessions: number;
  validityDays: number | null;
  eligibleSpas: { id: number; name: string }[];
}
export interface OwnedPackage {
  subscriptionId: number;
  packageId: number;
  packageType: string;
  status: string;
  paymentStatus: string;
  remainingSessions: number;
  sessions: number;
  amountCents: number | null;
  currency: string | null;
  expiryDate: string | null;
}
export interface PackageCheckout {
  purchase: OwnedPackage;
  razorpayKeyId: string;
  razorpayOrderId: string;
  amountCents: number;
  currency: string;
}
export const listPackages = async () =>
  (await client.get<CatalogPackage[]>('/api/packages/catalog')).data;
export const myPackages = async () => (await client.get<OwnedPackage[]>('/api/packages/mine')).data;
export const startPackage = async (id: number) =>
  (await client.post<PackageCheckout>(`/api/packages/${id}/checkout`)).data;
export const resumePackage = async (id: number) =>
  (await client.post<PackageCheckout>(`/api/packages/mine/${id}/checkout`)).data;
export const confirmPackage = async (id: number, result: RazorpayResult) =>
  (await client.post<OwnedPackage>(`/api/packages/mine/${id}/confirm`, result)).data;
export async function payForPackage(
  intent: PackageCheckout,
  spaPackage = false
): Promise<OwnedPackage> {
  const id = intent.purchase.subscriptionId,
    key = `nirvana_${spaPackage ? 'spa_package' : 'package'}_${id}`;
  if (intent.purchase.status !== 'PAYMENT_PENDING') {
    sessionStorage.removeItem(key);
    return intent.purchase;
  }
  const verify = async (result: RazorpayResult) => {
    const purchase = spaPackage
      ? (await client.post<OwnedPackage>(`/api/v1/user/packages/${id}/confirm`, result)).data
      : await confirmPackage(id, result);
    sessionStorage.removeItem(key);
    return purchase;
  };
  const saved = sessionStorage.getItem(key);
  if (saved) return verify(JSON.parse(saved));
  const Razorpay = await loadRazorpay();
  return new Promise((resolve, reject) => {
    const checkout = new Razorpay({
      key: intent.razorpayKeyId,
      order_id: intent.razorpayOrderId,
      amount: intent.amountCents,
      currency: intent.currency,
      name: 'Nirvana Wellness',
      description: `${intent.purchase.packageType} package`,
      handler: async (result: RazorpayResult) => {
        sessionStorage.setItem(key, JSON.stringify(result));
        try {
          resolve(await verify(result));
        } catch (e) {
          reject(e);
        }
      },
      modal: {
        ondismiss: () => reject(new Error('Checkout closed. Resume this purchase in My Packages.'))
      }
    });
    checkout.on('payment.failed', () =>
      reject(new Error('Payment was not completed. Retry this purchase in My Packages.'))
    );
    checkout.open();
  });
}
export const packagePrice = (amount: number, currency: string) =>
  new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount / 100);
