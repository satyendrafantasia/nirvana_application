import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import RatingBadge from '../../components/spa/RatingBadge';
import type { GooglePlace } from './Discovery';
import { Attribution, AuthorCredit, PhotoPlaceholder, SpaPhoto, safeUrl } from './PlaceMedia';

function PlaceProfile({ place }: { place: GooglePlace }) {
  const [index, setIndex] = useState(0);
  const photos = place.photos || [];
  const photo = photos[index];
  const phone = place.internationalPhoneNumber;
  const website = safeUrl(place.websiteUri);
  return (
    <>
      <section className="grid overflow-hidden rounded-3xl border border-primary-100 bg-white shadow-card md:grid-cols-2">
        <div className="flex min-w-0 flex-col justify-center gap-5 p-6 sm:p-10">
          <Attribution place={place} />
          <h1 className="break-words text-4xl font-semibold leading-tight text-primary-900 sm:text-5xl">
            {place.displayName?.text || 'Spa details'}
          </h1>
          {place.formattedAddress && (
            <address className="not-italic text-slate-600">{place.formattedAddress}</address>
          )}
          <div className="flex flex-wrap items-center gap-3">
            <RatingBadge rating={place.rating} count={place.userRatingCount} />
            {place.businessStatus && place.businessStatus !== 'OPERATIONAL' && (
              <span>{place.businessStatus.replaceAll('_', ' ').toLowerCase()}</span>
            )}
          </div>
          {(phone || website) && (
            <div className="flex flex-wrap gap-3 border-t border-primary-100 pt-5">
              {phone && (
                <Button as="a" href={'tel:' + phone.replace(/[^+0-9]/g, '')}>
                  Call {phone}
                </Button>
              )}
              {website && (
                <Button as="a" variant="secondary" href={website} target="_blank" rel="noreferrer">
                  Visit website ↗
                </Button>
              )}
            </div>
          )}
        </div>
        <div aria-label="Spa photos" className="bg-primary-50 p-3 sm:p-5">
          <div className="overflow-hidden rounded-2xl [&_figure]:relative [&_figure]:min-h-64 [&_figure_img]:h-72 [&_figure_img]:max-h-none [&_figure_img]:object-cover sm:[&_figure_img]:h-96">
            {photo ? (
              <SpaPhoto key={photo.name} photo={photo} place={place} />
            ) : (
              <div className="h-64">
                <PhotoPlaceholder />
              </div>
            )}
          </div>
          {photos.length > 1 && (
            <div className="mt-3 flex items-center justify-between gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={!index}
                onClick={() => setIndex(index - 1)}
              >
                Previous photo
              </Button>
              <span aria-live="polite" className="text-sm">
                {index + 1} / {photos.length}
              </span>
              <Button
                size="sm"
                variant="secondary"
                disabled={index + 1 === photos.length}
                onClick={() => setIndex(index + 1)}
              >
                Next photo
              </Button>
            </div>
          )}
        </div>
      </section>
      <section className="mt-10">
        <h2 className="text-2xl font-semibold text-primary-900">Google reviews</h2>
        {!!place.reviews?.length && (
          <p className="mt-1 text-sm text-slate-600">Sorted by relevance</p>
        )}
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          {place.reviews?.map((review) => (
            <article
              key={review.name}
              className="min-w-0 space-y-3 rounded-2xl border border-primary-100 bg-white p-5"
            >
              <div className="flex flex-wrap items-center gap-2 text-sm">
                {safeUrl(review.authorAttribution?.photoUri) && (
                  <img
                    src={review.authorAttribution?.photoUri}
                    alt=""
                    loading="lazy"
                    width={32}
                    height={32}
                    className="rounded-full"
                  />
                )}
                {review.authorAttribution && <AuthorCredit author={review.authorAttribution} />}
                {review.rating != null && <RatingBadge rating={review.rating} />}
              </div>
              <p className="whitespace-pre-line break-words leading-relaxed">
                {review.originalText?.text || review.text?.text}
              </p>
              <div className="flex flex-wrap items-center gap-3 text-xs text-slate-600">
                {review.relativePublishTimeDescription}
                {safeUrl(review.googleMapsUri) && (
                  <a
                    className="underline"
                    href={safeUrl(review.googleMapsUri)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Original review
                  </a>
                )}
              </div>
            </article>
          ))}
        </div>
        {!place.reviews?.length && <p>No Google reviews available.</p>}
      </section>
    </>
  );
}

export default function PlacePage() {
  const { placeId = '' } = useParams();
  const query = useQuery({
    queryKey: ['google-place-profile', placeId],
    queryFn: async ({ signal }) =>
      (
        await client.get<GooglePlace>('/api/discovery/places/' + encodeURIComponent(placeId), {
          signal
        })
      ).data,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false
  });
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [placeId]);
  useEffect(() => {
    if (query.data?.displayName?.text) document.title = query.data.displayName.text + ' | Nirvana';
  }, [query.data]);
  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:py-10">
      <Link to="/discover" className="mb-5 inline-block font-medium text-primary-700">
        ← Search spas
      </Link>
      {query.isPending && <p role="status">Loading spa details…</p>}
      {query.isError && (
        <div role="alert">
          {errorMessage(query.error)}{' '}
          <Button variant="secondary" onClick={() => void query.refetch()}>
            Retry
          </Button>
        </div>
      )}
      {query.data && <PlaceProfile key={placeId} place={query.data} />}
    </div>
  );
}
