import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EligibleSpas, SpaMatches } from './Packages';
import type { CatalogPackage } from '../../api/packageApi';

const render = (node: React.ReactNode) =>
  renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <StaticRouter location="/packages">{node}</StaticRouter>
    </QueryClientProvider>
  );

const pkg = (id: number, packageType: string, eligibleSpaCount = 3182): CatalogPackage => ({
  id,
  packageType,
  description: '',
  amountCents: 500000,
  currency: 'INR',
  sessions: 5,
  validityDays: 90,
  eligibleSpaCount
});

describe('where a package can be used', () => {
  it('shows a count and a button, not a list of every spa', () => {
    const html = render(<EligibleSpas packageId={7} spaCount={3182} />);
    expect(html).toContain('Available at 3,182 spas');
    expect(html).toContain('View spas');
    expect(html).toContain('aria-expanded="false"');
  });
  it('says so when no spa is configured', () => {
    expect(render(<EligibleSpas packageId={7} spaCount={0} />)).toContain(
      'No eligible spas configured yet'
    );
  });
  it('opens with a search box and loads spas from the server', () => {
    const html = render(<EligibleSpas packageId={7} spaCount={1} initiallyOpen />);
    expect(html).toContain('Available at 1 spa');
    expect(html).toContain('Search spas for this package');
    expect(html).toContain('Loading spas…');
  });
});

describe('spas found by a search', () => {
  const packages = [pkg(10, 'SILVER'), pkg(11, 'GOLD')];
  it('lists each spa with the packages usable there', () => {
    const html = render(
      <SpaMatches
        packages={packages}
        total={1}
        query="cafe"
        spas={[{ id: 2, name: 'Café Zen Spa', city: 'Pune', packageIds: [10] }]}
      />
    );
    expect(html).toContain('1 spa found');
    expect(html).toContain('Café Zen Spa');
    expect(html).toContain('· Pune');
    expect(html).toContain('✓ silver');
    expect(html).toContain('Not available: </span>gold');
  });
  it('says how many more there are when the list is cut short', () => {
    const spas = Array.from({ length: 50 }, (_, i) => ({ id: i, name: `Spa ${i}`, packageIds: [10] }));
    expect(render(<SpaMatches packages={packages} total={462} query="spa" spas={spas} />)).toContain(
      '462 spas found, showing the first 50.'
    );
  });
  it('says when no spa matches', () => {
    expect(render(<SpaMatches packages={packages} total={0} query="Kolkata" spas={[]} />)).toContain(
      'No spa matching “Kolkata” takes Nirvana packages yet.'
    );
  });
});

describe('asking the server', () => {
  it('searches by words, package and limit, and filters the catalog by spa', async () => {
    vi.resetModules();
    const get = vi.fn(async () => ({ data: { total: 0, spas: [] } }));
    vi.doMock('../../api/client', () => ({ default: { get } }));
    const api = await import('../../api/packageApi');
    await api.searchPackageSpas('  fern  ', 4, 20);
    expect(get).toHaveBeenCalledWith('/api/packages/spas', {
      params: { q: 'fern', packageId: 4, limit: 20 },
      signal: undefined
    });
    await api.listPackages(127);
    expect(get).toHaveBeenCalledWith('/api/packages/catalog', { params: { spaId: 127 } });
    vi.doUnmock('../../api/client');
  });
});

describe('browsing packages without an account', () => {
  it('shows the catalog and a buy button to a guest who is not signed in', async () => {
    const { default: Packages } = await import('./Packages');
    const { AuthContext } = await import('../../contexts/AuthContext');
    const qc = new QueryClient();
    qc.setQueryData(['packages'], [pkg(1, 'GOLD', 12)]);
    const guest = {
      user: null,
      token: null,
      isLoading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      hydrateUser: vi.fn(),
      setUser: vi.fn(),
      acceptAuth: vi.fn()
    };
    const html = renderToStaticMarkup(
      <QueryClientProvider client={qc}>
        <AuthContext.Provider value={guest}>
          <StaticRouter location="/packages">
            <Packages />
          </StaticRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    );
    expect(html).toMatch(/gold package/i);
    expect(html).toContain('Buy package');
  });
});
