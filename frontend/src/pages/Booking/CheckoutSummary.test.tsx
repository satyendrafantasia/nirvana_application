import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import CheckoutSummary from './CheckoutSummary';
import { CheckoutPreview } from '../../api/rewardsApi';
import { giftCardProblem } from '../GiftCards/GiftCards';

const base: CheckoutPreview = {
  currency: 'INR',
  priceCents: 12000,
  discountCents: 600,
  pointsRedeemed: 22,
  pointsCents: 2200,
  pointsBalance: 700,
  taxCents: 1656,
  totalCents: 10856,
  giftCardCents: 0,
  giftCardBalanceCents: 0,
  payableCents: 10856
};
const text = (p: Partial<CheckoutPreview>, payAtSpa = false) =>
  renderToStaticMarkup(<CheckoutSummary preview={{ ...base, ...p }} payAtSpa={payAtSpa} />).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

describe('checkout summary', () => {
  it('shows the price, discounts, points, tax and total', () => {
    const t = text({});
    expect(t).toContain('Treatment ₹120.00');
    expect(t).toContain('Off-peak discount −₹6.00');
    expect(t).toContain('22 loyalty points −₹22.00');
    expect(t).toContain('Tax ₹16.56');
    expect(t).toContain('Total ₹108.56');
    expect(t).not.toContain('Gift card');
  });
  it('shows a partial gift card, what is left to pay, and the balance it keeps', () => {
    const t = text({ giftCardCents: 5000, giftCardBalanceCents: 8000, payableCents: 5856 }, true);
    expect(t).toContain('Gift card −₹50.00');
    expect(t).toContain('Left to pay ₹58.56');
    expect(t).toContain('₹58.56 to pay at the spa');
    expect(t).toContain('₹30.00 stays on your gift card');
  });
  it('says nothing is left to pay when the gift card covers it all', () => {
    const t = text({ giftCardCents: 10856, giftCardBalanceCents: 50000, payableCents: 0 });
    expect(t).toContain('Left to pay ₹0.00');
    expect(t).toContain('Nothing left to pay');
  });
  it('explains a coupon or gift card that could not be applied', () => {
    const t = text({ couponError: 'This coupon has expired.', giftCardError: 'This gift card has been cancelled.' });
    expect(t).toContain('Coupon not applied: This coupon has expired.');
    expect(t).toContain('Gift card not applied: This gift card has been cancelled.');
  });
});

describe('buying a gift card', () => {
  it('checks the amount and the email before paying', () => {
    expect(giftCardProblem(1000, 'friend@example.com')).toBeUndefined();
    expect(giftCardProblem(100, 'friend@example.com')).toMatch(/between ₹500 and ₹10,000/);
    expect(giftCardProblem(20000, 'friend@example.com')).toMatch(/between/);
    expect(giftCardProblem(750.5, 'friend@example.com')).toMatch(/whole amount/);
    expect(giftCardProblem(1000, 'not-an-email')).toMatch(/email/);
  });
});
