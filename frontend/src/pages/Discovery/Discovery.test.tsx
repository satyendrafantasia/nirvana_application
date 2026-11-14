import React from 'react';
import { describe, it, expect } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import Discovery, { DiscoveryCard, mapsUrl } from './Discovery';
const render = (node: React.ReactNode) =>
  renderToStaticMarkup(<StaticRouter location="/discover">{node}</StaticRouter>);
describe('Google discovery', () => {
  it('only offers Nirvana booking for matched results', () => {
    const unmatched = render(
      <DiscoveryCard place={{ id: 'abc', displayName: { text: 'A spa' } }} />
    );
    expect(unmatched).not.toContain('View on Google Maps');
    expect(unmatched).toContain('/discover/places/abc');
    expect(unmatched).not.toContain('Book on Nirvana');
    expect(unmatched).toContain('Google Maps');
    const matched = render(<DiscoveryCard place={{ id: 'abc', nirvanaSpaId: 42 }} />);
    expect(matched).toContain('Book on Nirvana');
    expect(matched).toContain('/spas/42');
  });
  it('rejects unsafe provider links and escapes provider text', () => {
    expect(mapsUrl({ id: 'abc', googleMapsUri: 'javascript:alert(1)' })).toContain(
      'https://www.google.com/maps/'
    );
    const html = render(
      <DiscoveryCard
        place={{
          id: 'abc',
          displayName: { text: '<script>alert(1)</script>' },
          attributions: [{ provider: 'Source', providerUri: 'javascript:alert(1)' }]
        }}
      />
    );
    expect(html).not.toContain('<script>');
    expect(html).not.toContain('javascript:');
    expect(html).toContain('Source');
  });
  it('offers city and location modes without loading provider data on render', () => {
    const html = render(<Discovery />);
    expect(html).toContain('City or spa name');
    expect(html).toContain('>Spa name</label>');
    expect(html.indexOf('>City</label>')).toBeLessThan(html.indexOf('>Spa name</label>'));
    expect(html).toContain('Near me');
    expect(html).toContain('Near a location');
    expect(html).toContain('Locality (optional)');
    expect(html).not.toContain('Save these Place ID references');
  });
});
