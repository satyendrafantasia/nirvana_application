import { afterEach, describe, expect, it } from 'vitest';
import {
  CheckoutPreview,
  checkoutLines,
  forgetReferral,
  ledgerLabel,
  pendingReferral,
  pointsRules,
  rememberReferral,
  signedPoints
} from './rewardsApi';

const preview = (over: Partial<CheckoutPreview> = {}): CheckoutPreview => ({
  currency: 'INR',
  priceCents: 12000,
  discountCents: 600,
  pointsRedeemed: 22,
  pointsCents: 2200,
  pointsBalance: 700,
  taxCents: 1656,
  totalCents: 10856,
  giftCardCents: 5000,
  giftCardBalanceCents: 5000,
  payableCents: 5856,
  ...over
});

describe('checkout lines', () => {
  it('lists every step in the order the server applies it', () => {
    const lines = checkoutLines(preview({ couponCode: 'SAVE10' }));
    expect(lines.map((l) => l.label)).toEqual([
      'Treatment',
      'Discounts (coupon SAVE10)',
      '22 loyalty points',
      'Tax',
      'Total',
      'Gift card',
      'Left to pay'
    ]);
    expect(lines.map((l) => l.cents)).toEqual([12000, -600, -2200, 1656, 10856, -5000, 5856]);
  });
  it('adds up: total less gift card is what is left to pay', () => {
    const lines = checkoutLines(preview());
    const sum = lines.filter((l) => l.kind !== 'total').reduce((n, l) => n + l.cents, 0);
    expect(sum).toBe(lines[lines.length - 1].cents);
  });
  it('leaves out steps that do nothing', () => {
    const lines = checkoutLines(
      preview({ discountCents: 0, pointsRedeemed: 0, pointsCents: 0, taxCents: 0, totalCents: 12000, giftCardCents: 0, payableCents: 12000 })
    );
    expect(lines.map((l) => l.label)).toEqual(['Treatment', 'Total']);
  });
  it('names a single point in the singular', () => {
    expect(checkoutLines(preview({ pointsRedeemed: 1, pointsCents: 100 }))[2].label).toBe('1 loyalty point');
  });
});

describe('points', () => {
  it('describes the rules from the server settings', () => {
    const text = pointsRules({ pointValuePaise: 100, maxRedeemPercent: 20, pointsPer100Rupees: 1, expiryMonths: 12 });
    expect(text).toContain('1 point for every ₹100');
    expect(text).toContain('worth ₹1');
    expect(text).toContain('up to 20%');
    expect(text).toContain('expire 12 months');
  });
  it('labels every ledger entry type in plain words', () => {
    for (const type of ['EARN', 'EARN_REVERSAL', 'REDEEM', 'REDEEM_RETURN', 'REFERRAL', 'EXPIRY', 'ADJUSTMENT', 'MIGRATION'])
      expect(ledgerLabel(type)).not.toBe(type);
  });
  it('signs changes', () => {
    expect(signedPoints(10)).toBe('+10');
    expect(signedPoints(-1500)).toBe('−1,500');
    expect(signedPoints(0)).toBe('0');
  });
});

describe('referral link', () => {
  const store = new Map<string, string>();
  globalThis.localStorage ??= {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, v),
    removeItem: (k: string) => void store.delete(k),
    clear: () => store.clear(),
    key: () => null,
    length: 0
  } as Storage;
  afterEach(() => forgetReferral());
  it('keeps the code from ?ref= until the visitor has an account', () => {
    rememberReferral('?ref=abcd2345&utm=x');
    expect(pendingReferral()).toBe('ABCD2345');
    forgetReferral();
    expect(pendingReferral()).toBeUndefined();
  });
  it('ignores anything that is not a code', () => {
    rememberReferral('?ref=<script>');
    rememberReferral('?other=1');
    expect(pendingReferral()).toBeUndefined();
  });
});
