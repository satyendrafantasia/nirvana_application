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
/** The page a URL describes: the path, plus the city for the spa listing. */
export function pageKey(pathname: string, search: string) {
  return pathname.replace(/\/+$/, '') + '|' + (new URLSearchParams(search).get('city')?.trim().toLowerCase() || '');
}
// The backend renders spa and listing pages with their real title, description and structured
// data (marked data-server-head). They describe the page the visitor arrived on, so they stay
// until the visitor moves to a different page.
const arrivedOn = typeof window === 'undefined' ? '' : pageKey(window.location.pathname, window.location.search);
export default function PageHead() {
  const { pathname, search } = useLocation();
  const page = pageKey(pathname, search);
  useEffect(() => {
    const served = document.head.querySelectorAll('[data-server-head]');
    if (served.length && page === arrivedOn) return;
    served.forEach((node) => node.remove());
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
  }, [page, pathname]);
  return null;
}
