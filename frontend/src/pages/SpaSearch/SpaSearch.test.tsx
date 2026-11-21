import { describe, expect, it } from 'vitest';
import routes from '../../routes/AppRouter.tsx?raw';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import SpaCard from '../../components/spa/SpaCard';
import { PAGE_SIZE, resultsSummary } from './SpaSearch';

describe('spa search', () => {
  it('says how many spas matched and how many are shown', () => {
    expect(resultsSummary(24, 163)).toBe('Showing 24 of 163 spas');
    expect(resultsSummary(163, 163)).toBe('163 spas');
    expect(resultsSummary(1, 1)).toBe('1 spa');
    expect(PAGE_SIZE).toBeGreaterThan(10);
  });
  it('lets anyone open search and spa pages, but not booking', () => {
    expect(routes).toContain('<Route path="/spas" element={<SpaSearch />} />');
    expect(routes).toContain('<Route path="/spas/:spaId" element={<SpaDetails />} />');
    expect(routes).toMatch(/path="\/booking\/\*"[\s\S]*?<RequireAuth/);
  });
  it('shows no stars for a spa without reviews', () => {
    const card = (rating: number, reviewCount: number) =>
      renderToStaticMarkup(
        <StaticRouter location="/spas">
          <SpaCard spa={{ id: '1', name: 'New Spa', address: 'Gurugram', rating, reviewCount } as never} />
        </StaticRouter>
      );
    expect(card(0, 0)).not.toContain('0.0');
    expect(card(4.8, 8)).toContain('4.8');
  });
});
