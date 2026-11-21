// Static hosting must send these client-side routes to the private app shell.
const paths = new Set([
  '/discover',
  '/admin/city-discovery',
  '/admin/listing-claims',
  '/admin/spa-leads',
  '/staff/media',
  '/packages',
  '/me/packages',
  '/spas',
  '/auth/login',
  '/auth/register',
  '/auth/otp',
  '/auth/password',
  '/auth/social/callback',
  '/me/bookings',
  '/me/settings',
  '/privacy',
  '/terms'
]);
export function isAppRoute(value: string) {
  const path = value.replace(/\/+$/, '');
  return (
    paths.has(path) ||
    /^\/discover\/places\/[^/]+$/.test(path) ||
    /^\/(booking|owner)(\/|$)/.test(path) ||
    /^\/(spas|features|b)\/[^/]+$/.test(path)
  );
}
