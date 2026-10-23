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
    <div className="home-shell mx-auto max-w-6xl px-4 py-10">
      <section className="wellness-hero">
        <div className="hero-copy">
          <p className="eyebrow"><span /> YOUR EVERYDAY ESCAPE</p>
          <h1>A little pause.<br /><span>A better you.</span></h1>
          <p className="hero-description">Make room for feeling good. Discover spa experiences, find your moment, and leave the everyday behind.</p>
          <a href="#find-your-spa" className="hero-discover">Find your kind of calm <span aria-hidden="true">↗</span></a>
          <div className="hero-footnote"><span className="tiny-spark" aria-hidden="true">✳</span> A little time for yourself changes everything.</div>
        </div>
        <div className="hero-art" role="img" aria-label="Sculptural green arches and a lime sun, an abstract moment of calm">
          <div className="art-sun" /><div className="art-arch arch-back" /><div className="art-arch arch-front" />
          <span className="art-topline">THE ART OF SLOWING DOWN</span>
          <div className="art-caption"><span>Less rush.<br />More ritual.</span><span className="art-star" aria-hidden="true">✳</span></div>
          <span className="art-number">NIRVANA / WELLNESS, REIMAGINED</span>
        </div>
      </section>
      <section id="find-your-spa" className="search-panel" aria-label="Find a spa">
        <div className="search-heading"><span className="eyebrow">YOUR NEXT RESET</span><h2>Where will you unwind?</h2></div>
        <form className="home-search" onSubmit={handleSearch}>
          <Input name="q" label="Treatment or experience" placeholder="Massage, facial, sauna" />
          <Input name="city" label="City" placeholder="Bengaluru" />
          <Button type="submit" className="search-submit">Search spas <span aria-hidden="true">↗</span></Button>
        </form>
      </section>
      <div className="wellness-strip"><span>TIME WELL SPENT</span><p>Discover your space</p><span aria-hidden="true">✳</span><p>Choose your ritual</p><span aria-hidden="true">✳</span><p>Make it your moment</p></div>
      <section className="ritual-grid" aria-label="Explore wellness">
        <Link to="/spas?q=Massage" className="ritual-card"><span className="ritual-index">01 / RESTORE</span><span className="ritual-symbol" aria-hidden="true">◌</span><h3>Release the day.</h3><p>Explore massages & body treatments</p><span className="ritual-arrow" aria-hidden="true">↗</span></Link>
        <Link to="/spas?q=Facial" className="ritual-card"><span className="ritual-index">02 / RENEW</span><span className="ritual-symbol" aria-hidden="true">✳</span><h3>Find your glow.</h3><p>Discover facials & skin rituals</p><span className="ritual-arrow" aria-hidden="true">↗</span></Link>
        <Link to="/packages" className="ritual-card"><span className="ritual-index">03 / REPEAT</span><span className="ritual-symbol" aria-hidden="true">∞</span><h3>Make it a ritual.</h3><p>Explore wellness packages</p><span className="ritual-arrow" aria-hidden="true">↗</span></Link>
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
            <p className="listing-empty text-sm text-slate-500">
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
