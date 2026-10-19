import { Link, useNavigate } from 'react-router-dom';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import SpaCard from '../../components/spa/SpaCard';
import { useQuery } from '@tanstack/react-query';
import { searchSpas } from '../../api/spaApi';
import { useAuth } from '../../hooks/useAuth';
import { Spa } from '../../types/spa';

const Home = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const {
    data: featuredSpas,
    isError,
    isLoading
  } = useQuery({
    queryKey: ['featured-spas'],
    enabled: !!user,
    queryFn: async () => searchSpas({ sort: 'featured', size: 6 }),
    staleTime: 1000 * 60 * 5
  });

  const handleSearch = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const q = formData.get('q');
    const city = formData.get('city');
    navigate(`/spas?${new URLSearchParams({ q: String(q || ''), city: String(city || '') })}`);
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <section className="grid gap-10 rounded-3xl bg-gradient-to-br from-primary-50 to-white p-10 shadow-card md:grid-cols-2">
        <div className="space-y-6">
          <p className="inline-flex rounded-full bg-white/70 px-4 py-2 text-xs font-semibold uppercase tracking-widest text-primary-600">
            Spa & Wellness bookings
          </p>
          <h1 className="text-3xl font-bold text-slate-900 md:text-4xl">
            A little time for yourself.
          </h1>
          <p className="text-lg text-slate-600">
            Search, book, and manage appointments across premium spas with realtime availability.
          </p>
          <form className="grid gap-3 md:grid-cols-3" onSubmit={handleSearch}>
            <Input
              name="q"
              label="What are you looking for?"
              placeholder="Massage, facial, sauna"
            />
            <Input name="city" label="City" placeholder="Bengaluru" />
            <div className="flex items-end">
              <Button type="submit" className="w-full">
                Search spas
              </Button>
            </div>
          </form>
          <div className="flex items-center gap-4 text-sm text-slate-600">
            <span className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-white shadow-card">
              ⭐
            </span>
            <p>Choose your treatment, find a time, and make room for wellbeing.</p>
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div className="card p-5">
            <p className="text-sm font-semibold text-primary-700">Your next moment of calm</p>
            <h3 className="text-xl font-semibold text-slate-900">Wellness, on your schedule</h3>
            <p className="text-sm text-slate-600">
              Explore services and available appointment times.
            </p>
            <Button as={Link} to="/me/bookings" className="mt-4 w-full" variant="secondary">
              Manage bookings
            </Button>
          </div>
          <div className="card p-5">
            <p className="text-sm font-semibold text-primary-700">Exclusive perks</p>
            <ul className="list-disc space-y-2 pl-5 text-slate-600">
              <li>Curated spas with genuine reviews</li>
              <li>Secure payments or pay at spa</li>
              <li>Instant confirmations & reminders</li>
            </ul>
          </div>
        </div>
      </section>

      <section className="mt-12 space-y-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-semibold text-slate-900">Featured spas</h2>
          <Link to="/spas" className="text-sm font-semibold text-primary-600">
            View all
          </Link>
        </div>
        <div className="grid gap-6 md:grid-cols-3">
          {featuredSpas?.length ? (
            featuredSpas.map((spa: Spa) => <SpaCard spa={spa} key={spa.id} />)
          ) : (
            <p className="text-sm text-slate-500">
              {!user ? (
                <Link to="/auth/login?redirect=/spas">
                  Sign in to explore spas and live availability.
                </Link>
              ) : isError ? (
                'Spa listings are unavailable. Please retry once the service is connected.'
              ) : isLoading ? (
                'Loading spas…'
              ) : (
                'No spas are listed yet.'
              )}
            </p>
          )}
        </div>
      </section>
    </div>
  );
};

export default Home;
