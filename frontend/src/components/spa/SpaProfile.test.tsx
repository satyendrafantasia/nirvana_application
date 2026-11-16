import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import SpaProfile, { directionsUrl, embedUrl } from './SpaProfile';
import { placeProfile } from '../../pages/Discovery/PlacePage';
import { spaProfile } from '../../pages/SpaDetails/SpaDetails';

const render = (node: React.ReactNode) =>
  renderToStaticMarkup(<StaticRouter location="/discover/places/abc">{node}</StaticRouter>);

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
