import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { EligibleSpas } from './Packages';

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
    const html = render(<EligibleSpas packageId={7} spas={[{ id: 4, name: 'Nirvana Marylebone' }]} />);
    expect(html).toContain('Nirvana Marylebone');
    expect(html).toContain('/spas/4');
    expect(html).not.toContain('View spas');
  });
  it('says so when no spa is configured', () => {
    expect(render(<EligibleSpas packageId={7} spas={[]} />)).toContain('No eligible spas configured yet');
  });
});
