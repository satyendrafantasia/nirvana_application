import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { useQuery } from '@tanstack/react-query';
import AddOnPicker, { toggleAddOn } from './AddOnPicker';
import BookedAddOnLines from './BookedAddOnLines';
import { AddOn, addOnParams, addOnTotals, extraTime } from '../../api/addOnApi';
import { checkoutLines, CheckoutPreview } from '../../api/rewardsApi';
import CheckoutSummary from '../../pages/Booking/CheckoutSummary';
import { toCents } from '../../pages/Owner/OwnerAddOns';

vi.mock('@tanstack/react-query', async (actual) => ({
  ...(await actual<typeof import('@tanstack/react-query')>()),
  useQuery: vi.fn()
}));

const stones: AddOn = { id: 3, name: 'Hot stones', description: 'Warm basalt stones', priceCents: 50000, currency: 'INR', extraMinutes: 0 };
const extension: AddOn = { id: 7, name: 'Extension', priceCents: 40000, currency: 'INR', extraMinutes: 30 };
const oil: AddOn = { id: 9, name: 'Aromatherapy oil', priceCents: 15000, currency: 'INR', extraMinutes: 0 };
const menu = [stones, extension, oil];
const plain = (html: string) => html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

const picker = (selected: AddOn[], guests = 1, data: AddOn[] | undefined = menu) => {
  vi.mocked(useQuery).mockReturnValue({ data, isError: false } as ReturnType<typeof useQuery>);
  return renderToStaticMarkup(
    <AddOnPicker spaId="1" serviceId="5" durationMinutes={60} guests={guests} selected={selected} onChange={vi.fn()} />
  );
};

describe('add-on picker', () => {
  it('lists what applies to the treatment, with prices and extra time', () => {
    const t = plain(picker([]));
    expect(t).toContain('Add-ons and upgrades');
    expect(t).toContain('Hot stones');
    expect(t).toContain('Warm basalt stones');
    expect(t).toContain('+₹500.00');
    expect(t).toContain('Extension +30 min');
    expect(t).not.toContain('Add-ons: ');
  });
  it('shows nothing when the spa offers no add-ons for the treatment', () => {
    expect(picker([], 1, [])).toBe('');
  });
  it('ticks the chosen add-ons and totals them per guest, with the longer appointment', () => {
    const html = picker([stones, extension], 2);
    expect((html.match(/checked=""/g) || []).length).toBe(2);
    const t = plain(html);
    expect(t).toContain('priced per guest');
    expect(t).toContain('Add-ons: ₹1,800.00 · appointment 90 min');
  });
  it('adds and removes add-ons in menu order', () => {
    expect(toggleAddOn(menu, [], oil)).toEqual([oil]);
    expect(toggleAddOn(menu, [oil], stones)).toEqual([stones, oil]);
    expect(toggleAddOn(menu, [stones, oil], stones)).toEqual([oil]);
  });
});

describe('add-on totals', () => {
  it('prices each add-on for every guest but adds its minutes once', () => {
    expect(addOnTotals([stones, extension], 2)).toEqual({ cents: 180000, minutes: 30 });
    expect(addOnTotals([], 3)).toEqual({ cents: 0, minutes: 0 });
  });
  it('sends ids as one comma-separated parameter, and nothing when none are chosen', () => {
    expect(addOnParams([3, 7])).toEqual({ addOnIds: '3,7' });
    expect(addOnParams([])).toEqual({});
    expect(extraTime(0)).toBe('');
  });
  it('reads the owner’s price in rupees as paise', () => {
    expect(toCents('500')).toBe(50000);
    expect(toCents('12.5')).toBe(1250);
    expect(toCents('')).toBeNaN();
    expect(toCents('-1')).toBeNaN();
  });
});

const preview: CheckoutPreview = {
  currency: 'INR',
  priceCents: 102000,
  serviceCents: 12000,
  addOns: [
    { addOnId: 3, name: 'Hot stones', unitPriceCents: 50000, quantity: 1, amountCents: 50000, currency: 'INR', extraMinutes: 0 },
    { addOnId: 7, name: 'Extension', unitPriceCents: 40000, quantity: 1, amountCents: 40000, currency: 'INR', extraMinutes: 30 }
  ],
  extraMinutes: 30,
  discountCents: 600,
  pointsRedeemed: 0,
  pointsCents: 0,
  pointsBalance: 0,
  taxCents: 18252,
  totalCents: 119652,
  giftCardCents: 0,
  giftCardBalanceCents: 0,
  payableCents: 119652
};

describe('checkout total with add-ons', () => {
  it('lists the treatment and each add-on separately, and they add up to the price', () => {
    const lines = checkoutLines(preview);
    expect(lines.slice(0, 3)).toEqual([
      { label: 'Treatment', cents: 12000, kind: 'charge' },
      { label: 'Hot stones', cents: 50000, kind: 'charge' },
      { label: 'Extension', cents: 40000, kind: 'charge' }
    ]);
    expect(lines.slice(0, 3).reduce((sum, l) => sum + l.cents, 0)).toBe(preview.priceCents);
    expect(lines.find((l) => l.label === 'Total')?.cents).toBe(119652);
  });
  it('shows a quantity for several guests', () => {
    const two = { ...preview, addOns: [{ ...preview.addOns![0], quantity: 2, amountCents: 100000 }] };
    expect(checkoutLines(two)[1]).toEqual({ label: 'Hot stones × 2', cents: 100000, kind: 'charge' });
  });
  it('keeps the old single treatment line without add-ons', () => {
    expect(checkoutLines({ ...preview, addOns: [], serviceCents: 12000, priceCents: 12000 })[0]).toEqual({
      label: 'Treatment',
      cents: 12000,
      kind: 'charge'
    });
  });
  it('renders the add-on lines in the price summary', () => {
    const t = plain(renderToStaticMarkup(<CheckoutSummary preview={preview} />));
    expect(t).toContain('Treatment ₹120.00');
    expect(t).toContain('Hot stones ₹500.00');
    expect(t).toContain('Extension ₹400.00');
    expect(t).toContain('Total ₹1,196.52');
  });
});

describe('add-ons on a booking', () => {
  it('shows each add-on as sold, on its own line', () => {
    const t = plain(renderToStaticMarkup(<BookedAddOnLines addOns={preview.addOns} />));
    expect(t).toContain('Hot stones ₹500.00');
    expect(t).toContain('Extension +30 min ₹400.00');
  });
  it('shows nothing for a booking without add-ons', () => {
    expect(renderToStaticMarkup(<BookedAddOnLines addOns={[]} />)).toBe('');
  });
});
