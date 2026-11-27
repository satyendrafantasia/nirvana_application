import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import client from '../../api/client';
import { ProductAttribution } from '../common/CompanyInfo';

const Footer = () => {
  const { user } = useAuth();
  const unread = useQuery({
    queryKey: ['support-unread', user?.id], enabled: !!user, refetchInterval: 60000,
    queryFn: async () => (await client.get<number>('/api/me/support/unread')).data
  });
  return (
    <footer className="border-t border-gray-100 bg-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-6 gap-y-3 px-4 py-6 text-sm text-slate-600">
        <div>
          <ProductAttribution />
          <p className="text-slate-500">Make time for your wellbeing.</p>
        </div>
        <div className="flex flex-wrap gap-4">
          <a href="/about">About</a>
          <a href="/contact">Contact us</a>
          <a href="/privacy">Privacy</a><a href="/terms">Terms</a><a href="/support">Booking support{user && !!unread.data && ` (${unread.data} updated)`}</a>
          <a href="/packages">Packages</a>
          <a href="/me/packages">My Packages</a>
          <a href="/spas">Explore spas</a>
          <a href="/me/bookings">Your appointments</a>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
