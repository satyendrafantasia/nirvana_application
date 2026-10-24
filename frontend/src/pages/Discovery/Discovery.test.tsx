import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import Discovery, { DiscoveryCard, mapsUrl } from './Discovery';
const render = (node: React.ReactNode) =>
  renderToStaticMarkup(<StaticRouter location="/discover">{node}</StaticRouter>);
describe('Google discovery', () => {
  it('only offers Nirvana booking for matched results', () => {
    const unmatched = render(
      <DiscoveryCard
        place={{ id: 'abc', displayName: { text: 'A spa' } }}
        onDetails={vi.fn()}
        busy={false}
      />
    );
    expect(unmatched).toContain('View on Google Maps');
    expect(unmatched).not.toContain('Book on Nirvana');
    expect(unmatched).toContain('Google Maps');
    const matched = render(
      <DiscoveryCard place={{ id: 'abc', nirvanaSpaId: 42 }} onDetails={vi.fn()} busy={false} />
    );
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
        onDetails={vi.fn()}
        busy={false}
      />
    );
    expect(html).not.toContain('<script>');
    expect(html).not.toContain('javascript:');
    expect(html).toContain('Source');
  });
  it('offers city and location modes without loading provider data on render', () => {
    const html = render(<Discovery />);
    expect(html).toContain('City &amp; locality');
    expect(html).toContain('Near a location');
    expect(html).toContain('Locality (optional)');
    expect(html).not.toContain('Save these Place ID references');
  });
});
