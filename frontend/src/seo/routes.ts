// Static hosting must send these client-side routes to the private app shell.
const paths = new Set([
  '/discover',
  '/admin/city-discovery',
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
    /^\/(booking|owner)(\/|$)/.test(path) ||
    /^\/(spas|features)\/[^/]+$/.test(path)
  );
}
