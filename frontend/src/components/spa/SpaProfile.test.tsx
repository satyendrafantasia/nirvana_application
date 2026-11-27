import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import SpaProfile, { SpaPackages, directionsUrl, embedUrl } from './SpaProfile';
import { placeProfile } from '../../pages/Discovery/PlacePage';
import { spaProfile } from '../../pages/SpaDetails/SpaDetails';
import { AuthContext } from '../../contexts/AuthContext';
import { BookingProvider } from '../../contexts/BookingContext';

const render = (node: React.ReactNode) =>
  renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <AuthContext.Provider value={auth(false)}>
        <StaticRouter location="/discover/places/abc">{node}</StaticRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );

const place = {
  id: 'ChIJ_test-1',
  displayName: { text: 'Lotus Day Spa' },
  formattedAddress: '12 MG Road, Bengaluru',
  rating: 4.6,
  userRatingCount: 1234,
  businessStatus: 'OPERATIONAL',
  internationalPhoneNumber: '+91 80 5550 1234',
  websiteUri: 'https://lotus.example/',
  editorialSummary: { text: 'A calm day spa with Ayurvedic treatments.' },
  regularOpeningHours: { weekdayDescriptions: ['Monday: 10:00 AM – 9:00 PM'] },
  currentOpeningHours: { openNow: true },
  reviews: [{ name: 'places/x/reviews/1', rating: 5, text: { text: 'Wonderful massage.' } }]
};

const auth = (signedIn: boolean) =>
  ({
    user: signedIn ? { id: '1', email: 'guest@example.test' } : null,
    token: null,
    isLoading: false
  }) as unknown as React.ContextType<typeof AuthContext>;

describe('spa page packages', () => {
  const renderPackages = (spaPackages: unknown[], signedIn = true) => {
    const qc = new QueryClient();
    qc.setQueryData(['spa', '7'], { id: '7', name: 'Lotus Day Spa', defaultCurrency: 'INR' });
    qc.setQueryData(['spa-packages', '7'], spaPackages);
    qc.setQueryData(
      // The server returns only the packages usable at spa 7.
      ['packages', 'spa', '7'],
      [
        {
          id: 2,
          packageType: 'SILVER',
          description: '',
          amountCents: 500000,
          currency: 'INR',
          sessions: 5,
          validityDays: 90,
          eligibleSpaCount: 2
        }
      ]
    );
    return renderToStaticMarkup(
      <QueryClientProvider client={qc}>
        <AuthContext.Provider value={auth(signedIn)}>
          <StaticRouter location="/spas/7">
            <SpaPackages spaId="7" />
          </StaticRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    );
  };
  it('lets signed-out guests see packages, and asks them to sign in to buy', () => {
    const out = renderPackages([{ id: 1, level: 'GOLD', price: 3999, freeSessionsCount: 6 }], false);
    expect(out).toContain('gold package');
    expect(out).toContain('Sign in to buy');
    expect(out).not.toContain('Buy package');
    expect(renderPackages([{ id: 1, level: 'GOLD', price: 3999, freeSessionsCount: 6 }])).toContain('Buy package');
  });
  it("keeps the spa's own packages and Nirvana's in separate, labelled tabs", () => {
    const html = renderPackages([{ id: 1, level: 'GOLD', price: 3999, freeSessionsCount: 6 }]);
    expect(html).toContain('role="tablist"');
    expect(html).toContain('This spa&#x27;s packages (1)');
    expect(html).toContain('Nirvana packages (1)');
    // Opens on the spa's own packages, with its own headline.
    expect(html).toMatch(/aria-selected="true"[^>]*>This spa&#x27;s packages/);
    expect(html).toContain('Packages from Lotus Day Spa');
    expect(html).toContain('Your sessions can only be used here.');
    expect(html).toContain('Spa package');
    expect(html).toContain('gold package');
    expect(html).not.toContain('silver package');
  });
  it('opens on Nirvana packages when the spa sells none of its own', () => {
    const html = renderPackages([]);
    expect(html).toContain('This spa&#x27;s packages (0)');
    expect(html).toMatch(/aria-selected="true"[^>]*>Nirvana packages/);
    expect(html).toContain('Sold by Nirvana.');
    expect(html).toContain('silver package');
    expect(html).toContain('Use the sessions here or at 1 other Nirvana spas');
  });
});

describe('the spa page', () => {
  const html = render(
    <SpaProfile
      {...placeProfile(place)}
      back={{ to: '/discover', label: 'Search spas' }}
      media={<div />}
    />
  );
  it('shows the spa, its sections, reviews and a way to navigate there', () => {
    expect(html).toContain('Lotus Day Spa');
    expect(html).toContain('A calm day spa with Ayurvedic treatments.');
    expect(html).toContain('Open now');
    for (const id of ['about', 'services', 'packages', 'reviews', 'location'])
      expect(html).toContain(`href="#${id}"`);
    expect(html).toContain('Wonderful massage.');
    expect(html).toContain('Navigate with Google Maps');
    expect(html).toContain('destination_place_id=ChIJ_test-1');
  });
  it('keeps the phone number out of the page until more details are opened', () => {
    expect(html).toContain('View more details');
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain('5550');
    expect(html).not.toContain('tel:');
  });
  it('says plainly that a spa not yet on Nirvana has no menu or packages here', () => {
    expect(html).toContain('has not published its treatments and prices on Nirvana yet');
    expect(html).toContain('has not published its packages on Nirvana yet');
    expect(html).not.toContain('Buy package');
  });
  it('turns on services and packages only for a place linked to a Nirvana spa', () => {
    expect(placeProfile(place).spaId).toBeUndefined();
    expect(placeProfile({ ...place, nirvanaSpaId: 42 }).spaId).toBe('42');
  });
  it.each([
    { signedIn: false, mode: undefined, canBook: false },
    { signedIn: false, mode: 'INSTANT', canBook: false },
    { signedIn: true, mode: 'INSTANT', canBook: true },
    { signedIn: true, mode: 'REQUEST', canBook: false },
    { signedIn: true, mode: 'ENQUIRY', canBook: false }
  ])('shows the public menu for signedIn=$signedIn and mode=$mode', ({ signedIn, mode, canBook }) => {
    const qc = new QueryClient();
    qc.setQueryData(['spa-services', '7'], [
      { id: '2', name: 'Swedish Massage', durationMinutes: 60, price: 1999, currency: 'INR' }
    ]);
    if (mode) qc.setQueryData(['booking-policy', '7'], { mode });
    const out = renderToStaticMarkup(
      <QueryClientProvider client={qc}>
        <AuthContext.Provider value={auth(signedIn)}>
          <StaticRouter location="/spas/7">
            <BookingProvider>
              <SpaProfile {...placeProfile(place)} spaId="7" back={{ to: '/spas', label: 'All spas' }} media={null} />
            </BookingProvider>
          </StaticRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    );
    expect(out).toContain('Swedish Massage');
    expect(out).toContain('₹1,999.00');
    expect(out).toContain('60 mins');
    expect(out.includes('Book this service')).toBe(canBook);
    expect(out.includes('Request an appointment')).toBe(signedIn && mode === 'REQUEST');
    expect(out.includes('/auth/login?redirect=%2Fspas%2F7%23services')).toBe(!signedIn);
    if (!signedIn) expect(out).not.toContain('Loading booking options');
  });
  it('builds the same page from a Nirvana spa record', () => {
    const profile = spaProfile({
      id: '42',
      name: 'Nirvana Indiranagar',
      address: '100 Feet Road',
      latitude: 12.97,
      longitude: 77.64,
      phone: '+91 80 5550 9999',
      websiteUrl: 'javascript:alert(1)',
      openTimeLocal: '10:00',
      closeTimeLocal: '21:00',
      workingDays: ['MONDAY', 'TUESDAY']
    });
    expect(profile.spaId).toBe('42');
    expect(profile.mapQuery).toBe('12.97,77.64');
    expect(profile.website).toBeUndefined();
    expect(profile.hours?.[0]).toContain('MONDAY, TUESDAY · 10:00 – 21:00');
  });
  it('builds Google Maps links, and a map only when a key is set', () => {
    expect(directionsUrl('Lotus Day Spa, MG Road')).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=Lotus%20Day%20Spa%2C%20MG%20Road'
    );
    expect(embedUrl('x', 'ChIJ_test-1')).toBeUndefined();
    expect(embedUrl('x', 'ChIJ_test-1', 'k')).toContain('q=place_id%3AChIJ_test-1');
  });
});
