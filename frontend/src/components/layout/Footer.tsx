import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import client from '../../api/client';
import { ProductAttribution, SupportEmailLink } from '../common/CompanyInfo';

export default function Footer() {
  const { user } = useAuth();
  const unread = useQuery({
    queryKey: ['support-unread', user?.id],
    enabled: !!user,
    refetchInterval: 60000,
    queryFn: async () => (await client.get<number>('/api/me/support/unread')).data
  });
  return (
    <footer className="site-footer border-t">
      <div className="mx-auto grid max-w-6xl gap-5 px-4 py-8 text-sm sm:grid-cols-2">
        <div className="space-y-2">
          <ProductAttribution />
          <SupportEmailLink />
          <p>© {new Date().getFullYear()} Bluemantrix Technologies Pvt Ltd</p>
        </div>
        <nav aria-label="Footer" className="grid grid-cols-2 gap-x-3">
          <Link to="/spas">Explore spas</Link>
          <Link to="/packages">Packages</Link>
          <Link to="/about">About</Link>
          <Link to="/contact">Contact us</Link>
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Booking terms</Link>
          <Link to="/support">
            Booking support{user && !!unread.data && ` (${unread.data} updated)`}
          </Link>
        </nav>
      </div>
    </footer>
  );
}
