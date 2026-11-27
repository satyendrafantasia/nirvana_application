import { pageMeta } from './pages';
export const homeTitle = 'Nirvana | Book Spa & Wellness Services';
export const description =
  'Explore spas by city, compare treatments and prices, choose an available appointment time and manage your wellness bookings in one place with Nirvana.';
export function publicOrigin(value?: string) {
  if (!value) return '';
  const url = new URL(value);
  if (
    url.protocol !== 'https:' ||
    url.username ||
    url.password ||
    url.pathname !== '/' ||
    url.search ||
    url.hash ||
    !url.hostname.includes('.') ||
    /(^|\.)(localhost|test|local|internal)$|^[0-9.]+$/.test(url.hostname)
  )
    throw new Error('VITE_PUBLIC_SITE_URL must be the approved HTTPS production origin.');
  return url.origin;
}
const escape = (value: string) => value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
export function pageHead(path: string, origin: string, page = pageMeta[path]): string {
  if (path === '/') return seoHead(true, origin);
  if (!page) return seoHead(false, origin);
  const title = escape(page.title + ' | Nirvana Wellness');
  const summary = escape(page.summary);
  const index = Boolean(page.index && origin);
  const tags = [`<title>${title}</title>`, `<meta name="description" content="${summary}">`,
    `<meta name="robots" content="${index ? 'index,follow' : 'noindex,follow'}">`,
    `<meta property="og:title" content="${title}">`, `<meta property="og:description" content="${summary}">`,
    '<meta property="og:type" content="website">', '<meta property="og:site_name" content="Nirvana Wellness">'];
  if (index) tags.push(`<link rel="canonical" href="${escape(origin + path)}">`, `<meta property="og:url" content="${escape(origin + path)}">`);
  if (origin) tags.push(`<meta property="og:image" content="${origin}/nirvana-social.png">`, '<meta name="twitter:card" content="summary_large_image">');
  return tags.map(tag => tag.replace(/^<(\w+)/, '<$1 data-site-head')).join('\n');
}
export function seoHead(home: boolean, origin: string) {
  const title = home ? homeTitle : 'Nirvana Wellness';
  const tags = [
    `<title>${title}</title>`,
    `<meta name="robots" content="${home && origin ? 'index,follow' : 'noindex,follow'}">`
  ];
  if (home) {
    tags.push(
      '<link rel="preload" as="image" type="image/webp" href="/hero-spa-800.webp" imagesrcset="/hero-spa-480.webp 480w, /hero-spa-800.webp 800w, /hero-spa.webp 1100w" imagesizes="(max-width: 1023px) 100vw, 560px" fetchpriority="high">',
      `<meta name="description" content="${description}">`,
      `<meta property="og:title" content="${title}">`,
      `<meta property="og:description" content="${description}">`,
      '<meta property="og:type" content="website">'
    );
    if (origin)
      tags.push(
        `<link rel="canonical" href="${origin}/">`,
        `<meta property="og:url" content="${origin}/">`,
        `<meta property="og:image" content="${origin}/nirvana-social.png">`,
        '<meta property="og:image:width" content="1200">',
        '<meta property="og:image:height" content="630">',
        '<meta name="twitter:card" content="summary_large_image">',
        `<script type="application/ld+json">${JSON.stringify({ '@context': 'https://schema.org', '@type': 'Organization', name: 'Nirvana Wellness', url: origin, logo: origin + '/apple-touch-icon.png' })}</script>`
      );
  }
  return tags.map((tag) => tag.replace(/^<(\w+)/, '<$1 data-site-head')).join('\n');
}
// Keep in step with SeoPages.robots() on the backend, which serves robots.txt in production.
const privatePaths = ['/api/', '/owner', '/admin', '/me', '/booking', '/auth', '/staff', '/b/'];
export function robots(origin: string) {
  return origin
    ? `User-agent: *\nAllow: /\n${privatePaths.map((path) => `Disallow: ${path}\n`).join('')}Sitemap: ${origin}/sitemap.xml\n`
    : 'User-agent: *\nDisallow: /\n';
}
export function sitemap(origin: string) {
  return `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${origin ? ['/', ...Object.keys(pageMeta).filter(path => pageMeta[path].index)].map(path => '<url><loc>' + origin + path + '</loc></url>').join('') : ''}</urlset>`;
}
