import { isAppRoute } from './routes';
import { describe, expect, it } from 'vitest';
import { publicOrigin, robots, seoHead, sitemap } from './site';
describe('public discovery metadata', () => {
  const origin = 'https://nirvana.example.com';
  it('separates SPA navigation from unknown paths', () => {
    expect(isAppRoute('/spas/12')).toBe(true);
    expect(isAppRoute('/booking/receipt')).toBe(true);
    for (const path of ['/gift-cards', '/admin/gift-cards', '/support', '/admin/support']) expect(isAppRoute(path)).toBe(true);
    expect(isAppRoute('/unexpected')).toBe(false);
    expect(isAppRoute('/spas/12/missing')).toBe(false);
  });
  it('emits a canonical homepage and social preview for the configured origin', () => {
    const head = seoHead(true, origin);
    expect(head).toContain('href="' + origin + '/"');
    expect(head).toContain(origin + '/nirvana-social.png');
    expect(head).toContain('application/ld+json');
    expect(head).toContain('data-site-head');
    expect(sitemap(origin).match(/<loc>/g)).toHaveLength(1);
  });
  it('keeps private pages and unconfigured deployments out of the index', () => {
    expect(robots('')).toContain('Disallow: /');
    expect(sitemap('')).not.toContain('<loc>');
    expect(seoHead(false, origin)).toContain('noindex');
    expect(seoHead(false, origin)).not.toContain('canonical');
    expect(seoHead(true, '')).toContain('noindex');
  });
  it.each([
    'http://localhost:5173',
    'https://preview.test',
    'https://127.0.0.1',
    'https://example.com/private'
  ])('rejects invalid production origin %s', (url) => {
    expect(() => publicOrigin(url)).toThrow();
  });
});
