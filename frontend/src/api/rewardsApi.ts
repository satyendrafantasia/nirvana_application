import client from './client';
import { loadRazorpay } from './paymentApi';
import { RazorpayResult } from '../types/payment';
import { BookedAddOn } from './addOnApi';

// ---------------------------------------------------------------- loyalty points

export interface LedgerEntry {
  id: number;
  type: string;
  points: number;
  balanceAfter: number;
  bookingId?: number;
  bookingReference?: string;
  expiresAt?: string;
  remaining: number;
  note?: string;
  createdAt: string;
}

export interface Loyalty {
  balance: number;
  pointValuePaise: number;
  maxRedeemPercent: number;
  pointsPer100Rupees: number;
  expiryMonths: number;
  expiringSoonPoints: number;
  expiringSoonAt?: string;
  history: LedgerEntry[];
}

export const getLoyalty = async () => (await client.get<Loyalty>('/api/me/loyalty')).data;

const LEDGER_LABELS: Record<string, string> = {
  EARN: 'Earned',
  EARN_REVERSAL: 'Taken back after a refund',
  REDEEM: 'Used at checkout',
  REDEEM_RETURN: 'Returned after a cancellation',
  REFERRAL: 'Referral reward',
  EXPIRY: 'Expired',
  ADJUSTMENT: 'Adjusted by Nirvana',
  MIGRATION: 'Opening balance'
};
export const ledgerLabel = (type: string) => LEDGER_LABELS[type] ?? type;

export const signedPoints = (points: number) =>
  `${points > 0 ? '+' : points < 0 ? '−' : ''}${Math.abs(points).toLocaleString('en-IN')}`;

export const rupees = (paise: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: paise % 100 ? 2 : 0 }).format(
    paise / 100
  );

/** One sentence on how points work, from the server's settings. */
export const pointsRules = (l: Pick<Loyalty, 'pointValuePaise' | 'maxRedeemPercent' | 'pointsPer100Rupees' | 'expiryMonths'>) =>
  `You earn ${l.pointsPer100Rupees} point${l.pointsPer100Rupees === 1 ? '' : 's'} for every ₹100 you pay for a completed appointment. ` +
  `Each point is worth ${rupees(l.pointValuePaise)} at checkout, for up to ${l.maxRedeemPercent}% of a booking. ` +
  `Points expire ${l.expiryMonths} months after you earn them, and points used on a cancelled booking come back.`;

// ---------------------------------------------------------------- referrals

export interface Referral {
  code: string;
  link: string;
  reward: string;
  invited: number;
  rewarded: number;
  monthlyCap: number;
  referredBy?: string;
  myStatus?: 'PENDING' | 'REWARDED' | 'REJECTED';
  myStatusReason?: string;
  canClaim: boolean;
}

export const getReferral = async () => (await client.get<Referral>('/api/me/referral')).data;
export const claimReferral = async (code: string) =>
  (await client.post<Referral>('/api/me/referral/claim', { code: code.trim() })).data;

const PENDING_REFERRAL = 'nirvana.referralCode';
/** Keeps ?ref=CODE from a referral link until the visitor has an account. */
export const rememberReferral = (search: string) => {
  const code = new URLSearchParams(search).get('ref');
  if (code && /^[A-Za-z0-9]{4,16}$/.test(code)) localStorage.setItem(PENDING_REFERRAL, code.toUpperCase());
};
export const pendingReferral = () => localStorage.getItem(PENDING_REFERRAL) ?? undefined;
export const forgetReferral = () => localStorage.removeItem(PENDING_REFERRAL);

// ---------------------------------------------------------------- checkout preview

export interface CheckoutPreviewInput {
  spaId: string;
  serviceId: string;
  slotId?: string;
  guests: number;
  couponCode?: string;
  redeemLoyaltyPoints?: boolean;
  giftCardCode?: string;
  useWalletCredit?: boolean;
  addOnIds?: number[];
}

export interface CheckoutPreview {
  currency: string;
  priceCents: number;
  /** Coupon and off-peak discounts together. */
  discountCents: number;
  couponCode?: string;
  couponError?: string;
  pointsRedeemed: number;
  pointsCents: number;
  pointsBalance: number;
  taxCents: number;
  totalCents: number;
  giftCardCents: number;
  giftCardBalanceCents: number;
  giftCardError?: string;
  walletCents?: number;
  walletBalanceCents?: number;
  walletError?: string;
  payableCents: number;
  /** The treatment for all guests; priceCents adds the add-ons. */
  serviceCents?: number;
  addOns?: BookedAddOn[];
  extraMinutes?: number;
}

export const previewCheckout = async (input: CheckoutPreviewInput) =>
  (await client.post<CheckoutPreview>('/api/v1/checkout/preview', input)).data;

export interface CheckoutLine {
  label: string;
  cents: number;
  kind: 'charge' | 'discount' | 'payment' | 'total';
}

/**
 * The lines the checkout shows, in the order the server applies them: price, discounts,
 * points, tax, total, gift card, and what is left to pay.
 */
export function checkoutLines(p: CheckoutPreview): CheckoutLine[] {
  const addOns = p.addOns ?? [];
  const lines: CheckoutLine[] = [
    { label: 'Treatment', cents: addOns.length && p.serviceCents != null ? p.serviceCents : p.priceCents, kind: 'charge' }
  ];
  for (const a of addOns)
    lines.push({ label: a.quantity > 1 ? `${a.name} × ${a.quantity}` : a.name, cents: a.amountCents, kind: 'charge' });
  if (p.discountCents > 0)
    lines.push({ label: p.couponCode ? `Discounts (coupon ${p.couponCode})` : 'Off-peak discount', cents: -p.discountCents, kind: 'discount' });
  if (p.pointsCents > 0)
    lines.push({
      label: `${p.pointsRedeemed.toLocaleString('en-IN')} loyalty point${p.pointsRedeemed === 1 ? '' : 's'}`,
      cents: -p.pointsCents,
      kind: 'discount'
    });
  if (p.taxCents > 0) lines.push({ label: 'Tax', cents: p.taxCents, kind: 'charge' });
  lines.push({ label: 'Total', cents: p.totalCents, kind: 'total' });
  if (p.giftCardCents > 0 || (p.walletCents ?? 0) > 0) {
    if (p.giftCardCents > 0) lines.push({ label: 'Gift card', cents: -p.giftCardCents, kind: 'payment' });
    if (p.walletCents) lines.push({ label: 'Wallet credit', cents: -p.walletCents, kind: 'payment' });
    lines.push({ label: 'Left to pay', cents: p.payableCents, kind: 'total' });
  }
  return lines;
}

export const money = (cents: number, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(cents / 100);

// ---------------------------------------------------------------- gift cards

export interface GiftCard {
  id: number;
  code: string;
  status: 'PAYMENT_PENDING' | 'ACTIVE' | 'EXHAUSTED' | 'EXPIRED' | 'VOID' | 'REFUNDED';
  source: 'PURCHASE' | 'ADMIN';
  spaId?: number;
  spaName?: string;
  initialCents: number;
  balanceCents: number;
  currency: string;
  recipientName?: string;
  recipientEmail: string;
  message?: string;
  expiresAt?: string;
  activatedAt?: string;
  createdAt: string;
}

export interface GiftCardPurchase {
  amountCents: number;
  spaId?: number;
  recipientName?: string;
  recipientEmail: string;
  message?: string;
}

export interface GiftCardCheckout {
  card: GiftCard;
  razorpayKeyId: string;
  razorpayOrderId: string;
  amountCents: number;
  currency: string;
}

export interface GiftCardBalance {
  code: string;
  status: string;
  balanceCents: number;
  currency: string;
  spaId?: number;
  spaName?: string;
  expiresAt?: string;
}

export const GIFT_CARD_STATUS: Record<GiftCard['status'], string> = {
  PAYMENT_PENDING: 'Awaiting payment',
  ACTIVE: 'Active',
  EXHAUSTED: 'Fully used',
  EXPIRED: 'Expired',
  VOID: 'Cancelled',
  REFUNDED: 'Refunded'
};

export const startGiftCard = async (input: GiftCardPurchase) =>
  (await client.post<GiftCardCheckout>('/api/v1/gift-cards/checkout', input)).data;
export const resumeGiftCard = async (id: number) => (await client.post<GiftCard>(`/api/v1/gift-cards/${id}/resume`)).data;
export const myGiftCards = async () => (await client.get<GiftCard[]>('/api/v1/gift-cards/mine')).data;
export const giftCardBalance = async (code: string) =>
  (await client.get<GiftCardBalance>('/api/v1/gift-cards/balance', { params: { code: code.trim() } })).data;

/** Opens Razorpay for a gift card and confirms the payment with the server. */
export async function payForGiftCard(intent: GiftCardCheckout): Promise<GiftCard> {
  const id = intent.card.id;
  const key = `nirvana_gift_card_${id}`;
  const verify = async (result: RazorpayResult) => {
    const card = (await client.post<GiftCard>(`/api/v1/gift-cards/${id}/confirm`, result)).data;
    sessionStorage.removeItem(key);
    return card;
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
      description: 'Gift card',
      handler: async (result: RazorpayResult) => {
        sessionStorage.setItem(key, JSON.stringify(result));
        try {
          resolve(await verify(result));
        } catch (e) {
          reject(e);
        }
      },
      modal: { ondismiss: () => reject(new Error('Checkout closed. You can finish paying from your gift cards below.')) }
    });
    checkout.on('payment.failed', () => reject(new Error('Payment was not completed. Try again from your gift cards below.')));
    checkout.open();
  });
}

export interface OwnerGiftCards {
  currency: string;
  from: string;
  to: string;
  sales: { cards: number; soldCents: number; outstandingCents: number };
  redemptions: { redeemedCents: number; bookings: number };
  recent: { createdAt: string; type: 'REDEEM' | 'REVERSAL'; amountCents: number; bookingReference?: string; card: string; scope: string }[];
}
export const ownerGiftCards = async (spa: number | string, from?: string, to?: string) =>
  (await client.get<OwnerGiftCards>(`/api/owner/spas/${spa}/gift-cards`, { params: { from, to } })).data;

export interface IssueGiftCard extends GiftCardPurchase {
  validityDays?: number;
  note: string;
}
export const adminGiftCards = async (q?: string) =>
  (await client.get<GiftCard[]>('/api/admin/gift-cards', { params: { q: q || undefined } })).data;
export const issueGiftCard = async (input: IssueGiftCard) => (await client.post<GiftCard>('/api/admin/gift-cards', input)).data;
export const voidGiftCard = async (id: number, reason: string) =>
  (await client.post<GiftCard>(`/api/admin/gift-cards/${id}/void`, { reason })).data;
