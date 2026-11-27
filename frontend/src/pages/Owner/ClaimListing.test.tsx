import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext } from '../../contexts/AuthContext';
import OwnerPortal from './OwnerPortal';
import { ClaimDetails, ClaimSearch, ClaimSubmitted, PhoneCheck, type ClaimForm } from './ClaimListing';
import { claimUrl } from '../../components/spa/SpaProfile';

function render(node: React.ReactNode, location = '/owner/claim') {
  const auth = {
    user: { id: '1', firstName: 'O', lastName: 'W', email: 'o@example.test', roles: ['ROLE_SPA_OWNER'] },
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
        <StaticRouter location={location}>{node}</StaticRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

const form: ClaimForm = {
  placeId: 'ChIJ_claim',
  name: 'Lotus Day Spa',
  phone: '+91 80 5550 1234',
  websiteUrl: 'https://lotus.example/',
  addressLine: '12 MG Road, Bengaluru',
  city: 'Bengaluru',
  state: 'Karnataka',
  countryCode: 'IN',
  latitude: 12.97,
  longitude: 77.59,
  timezone: 'Asia/Kolkata',
  termsVersion: 'partner-2026-11',
  declaration: 'I own or am authorised to manage this spa.'
};

describe('claiming a Google listing', () => {
  it('opens from the owner workspace at /owner/claim', () => {
    const html = render(<OwnerPortal />);
    expect(html).toContain('Claim your spa’s listing');
    expect(html).toContain('aria-current="step"');
  });
  it('starts with a search of Google Maps and a way to skip it', () => {
    const html = render(<ClaimSearch onPick={() => {}} />);
    expect(html).toContain('Find your spa on Google Maps');
    expect(html).toContain('Spa name and area');
    expect(html).toContain('value="IN"');
  });
  it('pre-fills an editable form and needs the owner’s declaration', () => {
    const html = render(<ClaimDetails initial={form} onBack={() => {}} onDone={() => {}} />);
    expect(html).toContain('value="Lotus Day Spa"');
    expect(html).toContain('value="+91 80 5550 1234"');
    expect(html).toContain('value="Bengaluru"');
    expect(html).toContain('We filled this in from your Google Maps listing');
    expect(html).toContain('I own or am authorised to manage this spa.');
    expect(html).toContain('query=12.97,77.59');
    // Submitting stays disabled until the declaration is ticked.
    expect(html).toMatch(/<button[^>]*type="submit"[^>]*disabled/);
  });
  it('asks the owner to verify the listing phone before submitting', () => {
    const html = render(<ClaimDetails initial={form} onBack={() => {}} onDone={() => {}} />);
    expect(html).toContain('Verify you manage this spa');
    expect(html).toContain('Text me a code');
    expect(html).toContain('not the one you');
    expect(html).toContain('I can’t get texts on that number');
  });
  it('lets an owner whose listing has no phone submit without a code', () => {
    const html = render(
      <ClaimDetails initial={{ ...form, listingHasPhone: false }} onBack={() => {}} onDone={() => {}} />
    );
    expect(html).toContain('has no phone number we can text');
    expect(html).not.toContain('Text me a code');
  });
  it('shows a verified phone and the skip path', () => {
    const props = { placeId: 'p', hasPhone: true, onVerified: () => {}, onSkip: () => {} };
    expect(render(<PhoneCheck {...props} skipped={false} verifiedId="v1" />)).toContain('Phone verified');
    expect(render(<PhoneCheck {...props} skipped />)).toContain('Our team will call the number');
  });
  it('tells the owner what happens next', () => {
    const html = render(<ClaimSubmitted spaId={42} />);
    expect(html).toContain('we’re reviewing it');
    expect(html).toContain('/owner/services');
  });
  it('links a spa page to the claim for that listing', () => {
    expect(claimUrl('ChIJ_x-1')).toBe('/owner/claim?place=ChIJ_x-1');
    expect(claimUrl()).toBe('/owner/claim');
  });
  it('keeps management links out of the pending transfer screen', () => {
    const html = render(<ClaimSubmitted spaId={42} existingInventory />);
    expect(html).toContain('Ownership claim submitted');
    expect(html).toContain('spa #42');
    expect(html).not.toContain('/owner/services');
  });
});
