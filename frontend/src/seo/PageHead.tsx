import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getSpaDetails } from '../api/spaApi';
import { publicOrigin, pageHead } from './site';
import { pageMeta, type PageMeta } from './pages';
import { isAppRoute } from './routes';

export function pageKey(pathname: string, search: string) {
  const params = new URLSearchParams(search);
  return (
    pathname.replace(/\/+$/, '') +
    '|' +
    (params.get('city')?.trim().toLowerCase() || '') +
    (Array.from(params.keys()).some((key) => key !== 'city') ? '|filtered' : '')
  );
}
const initial = typeof window !== 'undefined' && {
  key: pageKey(location.pathname, location.search),
  head: Array.from(document.querySelectorAll('head [data-server-head]'), (n) => n.outerHTML).join(
    ''
  )
};
const origin = publicOrigin(
  import.meta.env.PROD ? import.meta.env.VITE_PUBLIC_SITE_URL : undefined
);
const privateTitles: Record<string, string> = {
  booking: 'Your appointment',
  owner: 'Owner dashboard',
  admin: 'Administration',
  me: 'My account',
  staff: 'Staff workspace',
  discover: 'Google Places spa discovery',
  features: 'Account tools',
  'gift-cards': 'Gift cards',
  b: 'Appointment response'
};
export default function PageHead() {
  const { pathname, search } = useLocation();
  const path = pathname.replace(/\/+$/, '') || '/';
  const key = pageKey(path, search);
  const spaId = /^\/spas\/(\d+)$/.exec(path)?.[1];
  const spa = useQuery({
    queryKey: ['spa', spaId],
    enabled: !!spaId,
    queryFn: () => getSpaDetails(spaId!),
    staleTime: 60000
  });
  useEffect(() => {
    const served = initial && initial.key === key && initial.head;
    if (served && document.head.querySelector('[data-server-head]')) return;
    let canonical = path;
    let meta: PageMeta | undefined = pageMeta[path];
    if (spaId && spa.data)
      meta = {
        title: spa.data.name + (spa.data.city ? ' in ' + spa.data.city : ''),
        summary:
          spa.data.description ||
          'Explore treatments, prices and booking options at ' + spa.data.name + '.',
        index: true
      };
    if (path === '/spas') {
      const city = new URLSearchParams(search).get('city')?.trim();
      meta = {
        ...pageMeta[path],
        title: city ? 'Spas in ' + city : pageMeta[path].title,
        index: !search
      };
      if (city) canonical += '?city=' + encodeURIComponent(city);
    }
    if (!meta && path !== '/') {
      const title = privateTitles[path.split('/')[1]];
      meta =
        isAppRoute(path) && (title || spaId)
          ? { title: title || 'Spa details', summary: 'View details on Nirvana Wellness.' }
          : pageMeta['/404'];
    }
    document.head
      .querySelectorAll('[data-server-head],[data-site-head]')
      .forEach((n) => n.remove());
    document.head.insertAdjacentHTML('beforeend', served || pageHead(canonical, origin, meta));
  }, [key, path, search, spaId, spa.data]);
  return null;
}
