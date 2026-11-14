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
export function seoHead(home: boolean, origin: string) {
  const title = home ? homeTitle : 'Nirvana Wellness';
  const tags = [
    `<title>${title}</title>`,
    `<meta name="robots" content="${home && origin ? 'index,follow' : 'noindex,follow'}">`
  ];
  if (home) {
    tags.push(
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
export function robots(origin: string) {
  return origin
    ? `User-agent: *\nAllow: /\nDisallow: /api/\nSitemap: ${origin}/sitemap.xml\n`
    : 'User-agent: *\nDisallow: /\n';
}
export function sitemap(origin: string) {
  return `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${origin ? '<url><loc>' + origin + '/</loc></url>' : ''}</urlset>`;
}
