import { AppointmentForm, usePolicy, modeText } from '../../pages/Owner/AppointmentOperations';
import { ReactNode, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { getSpaDetails, getSpaServices } from '../../api/spaApi';
import {
  listPackages,
  listSpaPackages,
  packagePrice,
  payForPackage,
  startPackage,
  startSpaPackage
} from '../../api/packageApi';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { track } from '../../telemetry';
import { Service } from '../../types/spa';
import Button from '../common/Button';
import RatingBadge from './RatingBadge';
import ServiceCard from './ServiceCard';

export type ProfileReview = {
  id: string;
  author?: ReactNode;
  avatar?: string;
  rating?: number;
  title?: string;
  text?: string;
  when?: string;
  link?: string;
};

export type SpaProfileProps = {
  back: { to: string; label: string };
  name: string;
  address?: string;
  rating?: number;
  reviewCount?: number;
  about?: string;
  /** Shown as a warning chip, for example "temporarily closed". */
  notice?: string;
  openNow?: boolean;
  /** Who the information comes from, when it is not Nirvana's own. */
  source?: ReactNode;
  media: ReactNode;
  hours?: string[];
  phone?: string;
  website?: string;
  email?: string;
  amenities?: string[];
  /** Set once the spa is onboarded on Nirvana: turns on its services, prices and packages. */
  spaId?: string;
  reviews?: ProfileReview[];
  reviewsTitle: string;
  reviewsNote?: string;
  reviewsState?: 'loading' | 'error';
  /** What to search Google Maps for: an address, "lat,lng" or the spa's name and address. */
  mapQuery: string;
  googlePlaceId?: string;
  mapUrl: string;
  children?: ReactNode;
};

const SECTIONS = [
  ['about', 'About'],
  ['services', 'Services'],
  ['packages', 'Packages'],
  ['reviews', 'Reviews'],
  ['location', 'Location']
] as const;

export const directionsUrl = (destination: string, placeId?: string) =>
  `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(destination)}` +
  (placeId ? `&destination_place_id=${encodeURIComponent(placeId)}` : '');

/** A Google map of the spa. Needs a browser key for the free Maps Embed API; without one there is no map. */
export const embedUrl = (query: string, placeId?: string, key?: string) =>
  key
    ? `https://www.google.com/maps/embed/v1/place?key=${encodeURIComponent(key)}&q=${encodeURIComponent(
        placeId ? `place_id:${placeId}` : query
      )}`
    : undefined;

function Section({
  id,
  eyebrow,
  title,
  children
}: {
  id: string;
  eyebrow: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <section id={id} aria-labelledby={`${id}-heading`} className="spa-section">
      <p className="eyebrow">
        <span /> {eyebrow}
      </p>
      <h2 id={`${id}-heading`}>{title}</h2>
      {children}
    </section>
  );
}

/** Where an owner starts claiming this listing; sign-in returns them here. */
export const claimUrl = (placeId?: string) =>
  placeId ? `/owner/claim?place=${encodeURIComponent(placeId)}` : '/owner/claim';

function NotListed({
  what,
  website,
  placeId
}: {
  what: string;
  website?: string;
  placeId?: string;
}) {
  return (
    <div className="listing-empty space-y-2 text-sm text-slate-600">
      <p>
        This spa has not published its {what} on Nirvana yet. Google Maps does not provide them, so
        they appear here once the spa joins Nirvana.
      </p>
      <p>
        {website && (
          <>
            <a className="underline" href={website} target="_blank" rel="noreferrer">
              Check the spa's website
            </a>{' '}
            ·{' '}
          </>
        )}
        <Link className="underline" to={claimUrl(placeId)}>
          Own this spa? Claim this listing
        </Link>
      </p>
    </div>
  );
}

function SpaServices({ spaId, spaName }: { spaId: string; spaName: string }) {
  const navigate = useNavigate();
  const { setSpa, setService } = useBookingFlow();
  const services = useQuery({
    queryKey: ['spa-services', spaId],
    queryFn: () => getSpaServices(spaId)
  });
  const book = (service: Service) => {
    setSpa(spaId, spaName);
    setService(service);
    track('select_service');
    navigate('/booking');
  };
  return (
    <>
      {services.isLoading && <p role="status">Loading treatments…</p>}
      {services.isError && (
        <p role="alert">
          Treatments could not be loaded.{' '}
          <button className="underline" onClick={() => void services.refetch()}>
            Retry
          </button>
        </p>
      )}
      {services.data?.length === 0 && <p>No treatments are currently available to book.</p>}
      <div className="space-y-3">
        {services.data?.map((service) => (
          <ServiceCard key={service.id} service={service} onBook={book} />
        ))}
      </div>
    </>
  );
}

type Offer = {
  key: string;
  title: string;
  price: string;
  sessions: number;
  note: string;
  mine: boolean;
  start: () => ReturnType<typeof startPackage>;
};
const packageName = (type: string) => `${type.toLowerCase().replaceAll('_', ' ')} package`;
/** Who sells the package: the spa itself, or Nirvana across many spas. */
type PackageTab = 'spa' | 'nirvana';

export function SpaPackages({ spaId }: { spaId: string }) {
  const qc = useQueryClient();
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const spa = useQuery({
    queryKey: ['spa', spaId],
    queryFn: () => getSpaDetails(spaId),
    staleTime: 60_000
  });
  const own = useQuery({
    queryKey: ['spa-packages', spaId],
    queryFn: () => listSpaPackages(spaId)
  });
  const includedServices=useQuery({queryKey:['spa-services',spaId],enabled:!!own.data?.some(p=>p.serviceIds?.length),queryFn:()=>getSpaServices(spaId)});
  // Only the Nirvana packages usable here, not the whole catalog with every spa.
  const catalog = useQuery({
    queryKey: ['packages', 'spa', spaId],
    queryFn: () => listPackages(spaId),
    staleTime: 5 * 60_000
  });
  const [bought, setBought] = useState<{ key: string; message: string }>();
  const buy = useMutation({
    mutationFn: async (offer: Offer) => payForPackage(await offer.start(), offer.mine),
    onMutate: () => setBought(undefined),
    onSuccess: (purchase, offer) =>
      setBought({ key: offer.key, message: `Your package is ${purchase.status.toLowerCase()}.` }),
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: ['my-packages'] });
    }
  });
  const currency = spa.data?.defaultCurrency || 'INR';
  const spaName = spa.data?.name || 'this spa';
  const spaOffers: Offer[] = (own.data || []).map((p) => ({
    key: `spa-${p.id}`,
    title: p.title || packageName(p.level),
    price: packagePrice(Math.round(p.price * 100), currency),
    sessions: p.freeSessionsCount,
    note: ['Use the sessions at this spa only',p.description,p.validityDays && 'Valid '+p.validityDays+' days',p.serviceIds?.length && ('Included services: '+p.serviceIds.map(id=>includedServices.data?.find(v=>String(v.id)===String(id))?.name||'Service '+id).join(', '))].filter(Boolean).join(' · '),
    mine: true,
    start: () => startSpaPackage(spaId, p.id)
  }));
  const nirvanaOffers: Offer[] = (catalog.data || []).map((p) => ({
      key: `nirvana-${p.id}`,
      title: packageName(p.packageType),
      price: packagePrice(p.amountCents, p.currency),
      sessions: p.sessions,
      note:
        `Use the sessions here or at ${Math.max(0, p.eligibleSpaCount - 1)} other Nirvana spas` +
        (p.validityDays ? ` · valid ${p.validityDays} days` : ''),
      mine: false,
      start: () => startPackage(p.id)
    }));
  const [chosen, setChosen] = useState<PackageTab>();
  // Open on the spa's own packages when it sells any; otherwise on Nirvana's.
  const tab: PackageTab = chosen ?? (spaOffers.length ? 'spa' : 'nirvana');
  const loading = own.isLoading || catalog.isLoading;
  const tabs: { id: PackageTab; label: string; count: number }[] = [
    { id: 'spa', label: "This spa's packages", count: spaOffers.length },
    { id: 'nirvana', label: 'Nirvana packages', count: nirvanaOffers.length }
  ];
  const offers = tab === 'spa' ? spaOffers : nirvanaOffers;
  const select = (next: PackageTab) => {
    setChosen(next);
    document.getElementById(`package-tab-${next}`)?.focus();
  };
  return (
    <>
      {loading && <p role="status">Loading packages…</p>}
      {(own.isError || catalog.isError) && (
        <p role="alert">{errorMessage(own.error || catalog.error)}</p>
      )}
      <div
        role="tablist"
        aria-label="Who sells the package"
        className="package-tabs mb-5 flex flex-wrap gap-2"
      >
        {tabs.map((t) => (
          <button
            key={t.id}
            id={`package-tab-${t.id}`}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            aria-controls={`package-panel-${t.id}`}
            tabIndex={tab === t.id ? 0 : -1}
            onClick={() => setChosen(t.id)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowRight' || e.key === 'ArrowLeft')
                select(t.id === 'spa' ? 'nirvana' : 'spa');
            }}
            className={`rounded-xl border px-4 py-2 text-sm font-semibold ${
              tab === t.id
                ? 'border-primary-700 bg-primary-700 text-white'
                : 'border-slate-300 bg-white text-slate-700'
            }`}
          >
            {t.label} ({t.count})
          </button>
        ))}
      </div>
      <div
        role="tabpanel"
        id={`package-panel-${tab}`}
        aria-labelledby={`package-tab-${tab}`}
        tabIndex={0}
        className="space-y-4"
      >
        <div>
          <h3 className="text-xl font-semibold text-slate-900">
            {tab === 'spa' ? `Packages from ${spaName}` : 'Nirvana packages'}
          </h3>
          <p className="text-sm text-slate-600">
            {tab === 'spa'
              ? 'Sold by this spa. Your sessions can only be used here.'
              : 'Sold by Nirvana. Your sessions can be used at this spa and at other Nirvana spas.'}
          </p>
        </div>
        {!loading && !offers.length && !own.isError && !catalog.isError && (
          <p>
            {tab === 'spa'
              ? 'This spa doesn’t sell its own packages yet.'
              : 'No Nirvana packages can be used at this spa yet.'}
          </p>
        )}
        <div className="grid gap-5 md:grid-cols-3">
          {offers.map((offer) => (
            <article key={offer.key} className="spa-package">
              <p
                className={`text-xs font-semibold uppercase tracking-wide ${offer.mine ? 'text-slate-500' : 'text-primary-700'}`}
              >
                {offer.mine ? 'Spa package' : 'Nirvana package'}
              </p>
              <h3 className="text-lg font-semibold capitalize text-slate-900">{offer.title}</h3>
              <p className="text-3xl font-bold text-primary-700">{offer.price}</p>
              <ul className="space-y-1 text-sm text-slate-600">
                <li>{offer.sessions} spa sessions</li>
                <li>{offer.note}</li>
              </ul>
              <Button
                className="mt-auto"
                disabled={buy.isPending}
                onClick={() =>
                  user
                    ? buy.mutate(offer)
                    : navigate(
                        `/auth/login?redirect=${encodeURIComponent(location.pathname + location.search + '#packages')}`
                      )
                }
              >
                {buy.isPending && buy.variables?.key === offer.key
                  ? 'Processing…'
                  : user
                    ? 'Buy package'
                    : 'Sign in to buy'}
              </Button>
              {buy.isError && buy.variables?.key === offer.key && (
                <p role="alert" className="text-sm text-rose-700">
                  {errorMessage(buy.error)}
                </p>
              )}
              {bought?.key === offer.key && (
                <p role="status" className="rounded-xl bg-emerald-50 p-3 text-sm">
                  {bought.message} Choose it at checkout when you book.
                </p>
              )}
            </article>
          ))}
        </div>
      </div>
    </>
  );
}

/** The full page for one spa: the same layout whether the facts come from Google or from Nirvana. */
export default function SpaProfile(p: SpaProfileProps) {
  const policy=usePolicy(p.spaId);
  const mode=policy.data?.mode;
  const [more, setMore] = useState(false);
  const directions = directionsUrl(p.mapQuery, p.googlePlaceId);
  const map = embedUrl(p.mapQuery, p.googlePlaceId, import.meta.env.VITE_GOOGLE_MAPS_EMBED_KEY);
  const hasMore = !!(p.phone || p.website || p.email || p.hours?.length);
  return (
    <div className="spa-profile mx-auto max-w-6xl px-4 py-6 sm:py-10">
      <Link to={p.back.to} className="mb-5 inline-block font-medium text-primary-700">
        ← {p.back.label}
      </Link>

      <header className="spa-hero">
        <div className="flex min-w-0 flex-col justify-center gap-5 p-6 sm:p-10">
          {p.source}
          <h1 className="break-words text-4xl font-semibold leading-tight text-primary-900 sm:text-5xl">
            {p.name}
          </h1>
          {p.address && <address className="not-italic text-slate-600">{p.address}</address>}
          <div className="flex flex-wrap items-center gap-2">
            <RatingBadge rating={p.rating} count={p.reviewCount} />
            {p.openNow != null && (
              <span className={p.openNow ? 'spa-chip spa-chip-open' : 'spa-chip'}>
                {p.openNow ? 'Open now' : 'Closed now'}
              </span>
            )}
            {p.notice && <span className="spa-chip spa-chip-warn">{p.notice}</span>}
            {p.spaId && mode && <span className="spa-chip spa-chip-open">{modeText[mode]}</span>}
          </div>
          <div className="flex flex-wrap gap-3 border-t border-primary-100 pt-5">
            <Button as="a" href="#services">
              {p.spaId ? 'Choose a treatment' : 'Services & prices'}
            </Button>
            <Button as="a" variant="secondary" href={directions} target="_blank" rel="noreferrer">
              Get directions ↗
            </Button>
          </div>
        </div>
        <div className="spa-hero-media">{p.media}</div>
      </header>

      <nav aria-label="On this page" className="spa-tabs">
        {SECTIONS.map(([id, label]) => (
          <a key={id} href={`#${id}`}>
            {label}
          </a>
        ))}
      </nav>

      <Section id="about" eyebrow="THE SPA" title={`About ${p.name}`}>
        {p.about ? (
          <p className="max-w-3xl leading-relaxed text-slate-700">{p.about}</p>
        ) : (
          <p className="text-slate-600">This spa has not added a description yet.</p>
        )}
        {!!p.amenities?.length && (
          <ul className="flex flex-wrap gap-2" aria-label="Amenities">
            {p.amenities.map((a) => (
              <li key={a} className="spa-chip">
                {a}
              </li>
            ))}
          </ul>
        )}
        {hasMore && (
          <div>
            <Button
              variant="secondary"
              aria-expanded={more}
              aria-controls="spa-more-details"
              onClick={() => setMore(!more)}
            >
              {more ? 'Hide details' : 'View more details'}
            </Button>
            {more && (
              <dl id="spa-more-details" className="spa-facts">
                {p.phone && (
                  <div>
                    <dt>Phone</dt>
                    <dd>
                      <a className="underline" href={'tel:' + p.phone.replace(/[^+0-9]/g, '')}>
                        {p.phone}
                      </a>
                    </dd>
                  </div>
                )}
                {p.website && (
                  <div>
                    <dt>Website</dt>
                    <dd>
                      <a
                        className="underline break-all"
                        href={p.website}
                        target="_blank"
                        rel="noreferrer"
                      >
                        {p.website.replace(/^https?:\/\//i, '').replace(/\/$/, '')}
                      </a>
                    </dd>
                  </div>
                )}
                {p.email && (
                  <div>
                    <dt>Email</dt>
                    <dd>
                      <a className="underline break-all" href={'mailto:' + p.email}>
                        {p.email}
                      </a>
                    </dd>
                  </div>
                )}
                {!!p.hours?.length && (
                  <div>
                    <dt>Opening hours</dt>
                    <dd>
                      <ul>
                        {p.hours.map((line) => (
                          <li key={line}>{line}</li>
                        ))}
                      </ul>
                    </dd>
                  </div>
                )}
              </dl>
            )}
          </div>
        )}
      </Section>

      <Section id="services" eyebrow="THE MENU" title="Services and prices">
        {p.spaId ? (
          mode==='INSTANT' ? <SpaServices spaId={p.spaId} spaName={p.name} /> :
          mode==='REQUEST' ? <AppointmentForm key={p.spaId} spa={p.spaId}/> :
          mode==='ENQUIRY' ? <p>Contact the spa to check prices and availability. {p.phone && <a href={'tel:'+p.phone}>{p.phone}</a>} {p.email && <a href={'mailto:'+p.email}>{p.email}</a>}</p> :
          <p role="status">{policy.isError?'Unable to load booking options.':'Loading booking options…'}</p>
        ) : (
          <NotListed what="treatments and prices" website={p.website} placeId={p.googlePlaceId} />
        )}
      </Section>

      <Section id="packages" eyebrow="SAVE ON REGULAR VISITS" title="Packages">
        {p.spaId && mode==='INSTANT' ? (
          <SpaPackages spaId={p.spaId} />
        ) : (
          <NotListed what="packages" placeId={p.googlePlaceId} />
        )}
      </Section>

      <Section id="reviews" eyebrow="WHAT GUESTS SAY" title={p.reviewsTitle}>
        {p.rating != null && (
          <p className="flex items-baseline gap-3">
            <span className="text-5xl font-semibold text-primary-900">{p.rating.toFixed(1)}</span>
            <span className="text-sm text-slate-600">
              out of 5{p.reviewCount ? ` · ${p.reviewCount.toLocaleString()} ratings` : ''}
              {p.reviewsNote ? ` · ${p.reviewsNote}` : ''}
            </span>
          </p>
        )}
        {p.reviewsState === 'loading' && <p role="status">Loading reviews…</p>}
        {p.reviewsState === 'error' && <p role="alert">Reviews could not be loaded.</p>}
        {!p.reviewsState && !p.reviews?.length && <p>No reviews yet.</p>}
        <div className="grid gap-4 md:grid-cols-2">
          {p.reviews?.map((review) => (
            <article key={review.id} className="spa-review">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                {review.avatar && (
                  <img
                    src={review.avatar}
                    alt=""
                    loading="lazy"
                    width={32}
                    height={32}
                    className="rounded-full"
                  />
                )}
                {review.author}
                {review.rating != null && <RatingBadge rating={review.rating} />}
              </div>
              {review.title && <p className="font-semibold text-slate-900">{review.title}</p>}
              <p className="whitespace-pre-line break-words leading-relaxed">{review.text}</p>
              <div className="flex flex-wrap items-center gap-3 text-xs text-slate-600">
                {review.when}
                {review.link && (
                  <a className="underline" href={review.link} target="_blank" rel="noreferrer">
                    Original review
                  </a>
                )}
              </div>
            </article>
          ))}
        </div>
      </Section>

      <Section id="location" eyebrow="GETTING THERE" title="Location">
        <div className="spa-location">
          {map && (
            <iframe
              title={`Map showing ${p.name}`}
              src={map}
              loading="lazy"
              referrerPolicy="no-referrer-when-downgrade"
              allowFullScreen
            />
          )}
          <div className="flex flex-col gap-4 p-6">
            {p.address && <address className="not-italic text-slate-700">{p.address}</address>}
            <div className="flex flex-wrap gap-3">
              <Button as="a" href={directions} target="_blank" rel="noreferrer">
                Navigate with Google Maps ↗
              </Button>
              <Button as="a" variant="secondary" href={p.mapUrl} target="_blank" rel="noreferrer">
                View on Google Maps ↗
              </Button>
            </div>
            <p className="text-xs text-slate-500">
              Directions open in Google Maps and start from your current location.
            </p>
          </div>
        </div>
      </Section>

      {p.children}
    </div>
  );
}
