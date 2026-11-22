import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { EligibleSpas, PackageSpaSearch, matchSpas } from './Packages';
import type { CatalogPackage } from '../../api/packageApi';

const render = (node: React.ReactNode) =>
  renderToStaticMarkup(<StaticRouter location="/packages">{node}</StaticRouter>);

describe('where a package can be used', () => {
  it('shows a count and a button instead of every spa name', () => {
    const spas = Array.from({ length: 36 }, (_, i) => ({ id: i + 1, name: `Spa number ${i + 1}` }));
    const html = render(<EligibleSpas packageId={7} spas={spas} />);
    expect(html).toContain('Available at 36 spas');
    expect(html).toContain('View spas');
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain('Spa number 1');
  });
  it('names a single spa directly and links to it', () => {
    const html = render(
      <EligibleSpas packageId={7} spas={[{ id: 4, name: 'Nirvana Marylebone' }]} />
    );
    expect(html).toContain('Nirvana Marylebone');
    expect(html).toContain('/spas/4');
    expect(html).not.toContain('View spas');
  });
  it('says so when no spa is configured', () => {
    expect(render(<EligibleSpas packageId={7} spas={[]} />)).toContain(
      'No eligible spas configured yet'
    );
  });
  it('opens as a searchable list with the city, capped for long lists', () => {
    const spas = Array.from({ length: 120 }, (_, i) => ({
      id: i + 1,
      name: `Spa ${i + 1}`,
      city: 'Pune'
    }));
    const html = render(<EligibleSpas packageId={7} spas={spas} initiallyOpen />);
    expect(html).toContain('Search spas for this package');
    expect(html).toContain('· Pune');
    expect(html).toContain('and 70 more');
  });
});

const spas = {
  fern: { id: 1, name: 'The Fern House', city: 'Lucknow' },
  cafe: { id: 2, name: 'Café Zen Spa', city: 'Pune' },
  ferns: { id: 3, name: 'Ferns & Petals Day Spa', city: 'Bengaluru' },
  lotus: { id: 4, name: 'Lotus Wellness', city: 'Lucknow' }
};
const pkg = (
  id: number,
  packageType: string,
  eligibleSpas: CatalogPackage['eligibleSpas']
): CatalogPackage => ({
  id,
  packageType,
  description: '',
  amountCents: 500000,
  currency: 'INR',
  sessions: 5,
  validityDays: 90,
  eligibleSpas
});

describe('finding a spa', () => {
  const all = Object.values(spas);
  it('matches names and cities, ignoring case and accents', () => {
    expect(matchSpas('cafe', all)).toEqual([spas.cafe]);
    expect(matchSpas('LUCKNOW', all).map((s) => s.id)).toEqual([4, 1]);
    expect(matchSpas('fern lucknow', all)).toEqual([spas.fern]);
    expect(matchSpas('kolkata', all)).toEqual([]);
  });
  it('lists names that start with the search first', () => {
    expect(matchSpas('fern', all).map((s) => s.id)).toEqual([3, 1]);
  });
  it('shows which packages can be used at each matching spa', () => {
    const packages = [pkg(10, 'SILVER', all), pkg(11, 'GOLD', [spas.fern, spas.lotus])];
    const html = render(<PackageSpaSearch packages={packages} initialQuery="cafe" />);
    expect(html).toContain('Packages can be used at 4 spas');
    expect(html).toContain('1 spa found');
    expect(html).toContain('Café Zen Spa');
    expect(html).toContain('✓ silver');
    expect(html).toContain('Not available: </span>gold');
  });
  it('says when no spa matches', () => {
    const html = render(
      <PackageSpaSearch packages={[pkg(10, 'SILVER', [spas.fern])]} initialQuery="Kolkata" />
    );
    expect(html).toContain('No spa matching “Kolkata” takes Nirvana packages yet.');
  });
});
