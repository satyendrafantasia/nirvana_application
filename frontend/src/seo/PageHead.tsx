import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { publicOrigin, seoHead } from './site';
const titles: Record<string, string> = {
  '/terms': 'Booking terms',
  '/privacy': 'Privacy policy',
  '/spas': 'Browse spas',
  '/discover': 'Discover nearby spas',
  '/booking': 'Book an appointment',
  '/auth/login': 'Sign in',
  '/auth/register': 'Create an account',
  '/me/bookings': 'My appointments',
  '/me/settings': 'Account settings',
  '/packages': 'Wellness packages',
  '/404': 'Page not found'
};
export default function PageHead() {
  const { pathname } = useLocation();
  useEffect(() => {
    document.head.querySelectorAll('[data-site-head]').forEach((node) => node.remove());
    document.head.insertAdjacentHTML(
      'beforeend',
      seoHead(
        pathname === '/',
        publicOrigin(
          import.meta.env.MODE === 'production' && import.meta.env.PROD
            ? import.meta.env.VITE_PUBLIC_SITE_URL
            : undefined
        )
      )
    );
    if (pathname !== '/') document.title = (titles[pathname] || 'Nirvana Wellness') + ' | Nirvana';
  }, [pathname]);
  return null;
}
