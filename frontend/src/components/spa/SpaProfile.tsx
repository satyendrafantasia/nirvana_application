import { ReactNode, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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

function NotListed({ what, website, placeId }: { what: string; website?: string; placeId?: string }) {
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

function SpaPackages({ spaId }: { spaId: string }) {
  const qc = useQueryClient();
  const spa = useQuery({ queryKey: ['spa', spaId], queryFn: () => getSpaDetails(spaId) });
  const own = useQuery({
    queryKey: ['spa-packages', spaId],
    queryFn: () => listSpaPackages(spaId)
  });
  const catalog = useQuery({ queryKey: ['packages'], queryFn: listPackages });
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
  const offers: Offer[] = [
    ...(own.data || []).map((p) => ({
      key: `spa-${p.id}`,
      title: packageName(p.level),
      price: packagePrice(Math.round(p.price * 100), currency),
      sessions: p.freeSessionsCount,
      note: 'For visits to this spa only',
      mine: true,
      start: () => startSpaPackage(spaId, p.id)
    })),
    ...(catalog.data || [])
      .filter((p) => p.eligibleSpas.some((s) => String(s.id) === spaId))
      .map((p) => ({
        key: `nirvana-${p.id}`,
        title: `Nirvana ${packageName(p.packageType)}`,
        price: packagePrice(p.amountCents, p.currency),
        sessions: p.sessions,
        note:
          `Use it here and at ${p.eligibleSpas.length - 1} other Nirvana spas` +
          (p.validityDays ? ` · valid ${p.validityDays} days` : ''),
        mine: false,
        start: () => startPackage(p.id)
      }))
  ];
  const loading = own.isLoading || catalog.isLoading;
  return (
    <>
      {loading && <p role="status">Loading packages…</p>}
      {(own.isError || catalog.isError) && (
        <p role="alert">{errorMessage(own.error || catalog.error)}</p>
      )}
      {!loading && !offers.length && !own.isError && !catalog.isError && (
        <p>This spa is not selling any packages at the moment.</p>
      )}
      <div className="grid gap-5 md:grid-cols-3">
        {offers.map((offer) => (
          <article key={offer.key} className="spa-package">
            <h3 className="text-lg font-semibold capitalize text-slate-900">{offer.title}</h3>
            <p className="text-3xl font-bold text-primary-700">{offer.price}</p>
            <ul className="space-y-1 text-sm text-slate-600">
              <li>{offer.sessions} spa sessions</li>
              <li>{offer.note}</li>
            </ul>
            <Button
              className="mt-auto"
              disabled={buy.isPending}
              onClick={() => buy.mutate(offer)}
            >
              {buy.isPending && buy.variables?.key === offer.key ? 'Processing…' : 'Buy package'}
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
    </>
  );
}

/** The full page for one spa: the same layout whether the facts come from Google or from Nirvana. */
export default function SpaProfile(p: SpaProfileProps) {
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
            {p.spaId && <span className="spa-chip spa-chip-open">Bookable on Nirvana</span>}
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
                      <a className="underline break-all" href={p.website} target="_blank" rel="noreferrer">
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
          <SpaServices spaId={p.spaId} spaName={p.name} />
        ) : (
          <NotListed what="treatments and prices" website={p.website} placeId={p.googlePlaceId} />
        )}
      </Section>

      <Section id="packages" eyebrow="SAVE ON REGULAR VISITS" title="Packages">
        {p.spaId ? <SpaPackages spaId={p.spaId} /> : <NotListed what="packages" placeId={p.googlePlaceId} />}
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
