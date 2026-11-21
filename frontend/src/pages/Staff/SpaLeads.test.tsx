import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext } from '../../contexts/AuthContext';
import SpaLeads, { LeadCard, type Lead } from './SpaLeads';
import { ADMIN_MENU } from '../../components/layout/FeatureMenu';

function render(node: React.ReactNode, roles = ['ROLE_ADMIN']) {
  const auth = {
    user: { id: '1', firstName: 'A', lastName: 'D', email: 'a@example.test', roles },
    token: 't', isLoading: false, login: vi.fn(), register: vi.fn(), logout: vi.fn(),
    hydrateUser: vi.fn(), setUser: vi.fn(), acceptAuth: vi.fn()
  };
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <AuthContext.Provider value={auth}>
        <StaticRouter location="/admin/spa-leads">{node}</StaticRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

const lead: Lead = {
  id: 1,
  name: 'Wellicon Luxury Spa',
  address: 'Sector 46, Gurugram',
  phone: '+91 81784 09586',
  email: 'info@wellicon.example.in',
  website: 'https://www.wellicon.example.in/',
  socialsJson: '{"instagram":"https://www.instagram.com/x","bad":"javascript:alert(1)"}',
  googleRating: 4.9,
  googleReviewCount: 484,
  mapsUrl: 'https://maps.google.com/?cid=1',
  status: 'NEW'
};

describe('spa leads', () => {
  it('is admin-only and linked from the Admin menu', () => {
    expect(render(<SpaLeads />, ['ROLE_SPA_OWNER'])).toContain('Admin access required');
    expect(render(<SpaLeads />)).toContain('Import spas from a file');
    expect(ADMIN_MENU.flatMap((g) => g.items.map((i) => i.to))).toContain('/admin/spa-leads');
  });
  it('shows how to reach the spa and lets you record progress', () => {
    const html = render(<LeadCard lead={lead} />);
    expect(html).toContain('href="tel:+918178409586"');
    expect(html).toContain('mailto:info@wellicon.example.in');
    expect(html).toContain('★ 4.9 · 484 reviews');
    expect(html).toContain('instagram');
    expect(html).not.toContain('javascript:');
    expect(html).toContain('Not interested');
  });
});
