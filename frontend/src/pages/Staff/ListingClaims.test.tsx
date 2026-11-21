import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext } from '../../contexts/AuthContext';
import ListingClaims, { ClaimCard, googleListingUrl, type Claim } from './ListingClaims';
import { isAppRoute } from '../../seo/routes';

function render(node: React.ReactNode, roles = ['ROLE_ADMIN']) {
  const auth = {
    user: { id: '1', firstName: 'A', lastName: 'D', email: 'a@example.test', roles },
    token: 't',
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    hydrateUser: vi.fn(),
    setUser: vi.fn(),
    acceptAuth: vi.fn()
  };
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <AuthContext.Provider value={auth}>
        <StaticRouter location="/admin/listing-claims">{node}</StaticRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

const claim: Claim = {
  id: 7,
  spaId: 42,
  spaName: 'Lotus Day Spa',
  city: 'Bengaluru',
  addressLine: '12 MG Road',
  placeId: 'ChIJ_claim',
  status: 'PENDING',
  consentedAt: '2026-11-20T10:00:00Z',
  termsVersion: 'partner-2026-11',
  ownerEmail: 'asha@example.test',
  ownerName: 'Asha Rao',
  phone: '+91 80 5550 1234'
};

describe('listing claim review', () => {
  it('is for admins only', () => {
    expect(render(<ListingClaims />, ['ROLE_SPA_OWNER'])).toContain('Admin access required');
    const html = render(<ListingClaims />);
    expect(html).toContain('Listing claims');
    expect(html).toContain('Waiting for review');
    expect(isAppRoute('/admin/listing-claims')).toBe(true);
  });
  it('shows what the owner submitted next to the Google listing, with the decision', () => {
    const html = render(<ClaimCard claim={claim} />);
    for (const text of ['Asha Rao', 'asha@example.test', '+91 80 5550 1234', '12 MG Road, Bengaluru', 'partner-2026-11'])
      expect(html).toContain(text);
    expect(html).toContain('query_place_id=ChIJ_claim');
    expect(html).toContain('Approve');
    expect(html).toContain('Reject');
    expect(html).toContain('call the number shown on the Google listing');
  });
  it('shows the outcome instead of actions once reviewed', () => {
    const html = render(
      <ClaimCard claim={{ ...claim, status: 'REJECTED', reviewNote: 'Could not reach the spa', reviewedAt: '2026-11-21T10:00:00Z' }} />
    );
    expect(html).toContain('Could not reach the spa');
    expect(html).not.toContain('>Approve<');
  });
  it('links to the listing by name and Place ID', () => {
    expect(googleListingUrl({ spaName: 'A & B', placeId: 'x' })).toBe(
      'https://www.google.com/maps/search/?api=1&query=A%20%26%20B&query_place_id=x'
    );
  });
});
