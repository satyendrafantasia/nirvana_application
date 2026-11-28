import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import SpaProfile, { type SpaProfileProps } from '../../components/spa/SpaProfile';
import type { GooglePlace } from './Discovery';
import {
  Attribution,
  AuthorCredit,
  PhotoPlaceholder,
  SpaPhoto,
  mapsUrl,
  safeUrl
} from './PlaceMedia';

/** One photo at a time: each Google photo is a separate paid request. */
export function PlaceGallery({
  place,
  imagePath
}: {
  place: GooglePlace;
  imagePath?: (photoId: string) => string;
}) {
  const [index, setIndex] = useState(0);
  const photos = place.photos || [];
  const photo = photos[index];
  return (
    <div aria-label="Spa photos">
      <div className="overflow-hidden rounded-2xl [&_figure]:relative [&_figure]:min-h-64 [&_figure_img]:h-72 [&_figure_img]:max-h-none [&_figure_img]:object-cover sm:[&_figure_img]:h-96">
        {photo ? (
          <SpaPhoto key={photo.name} photo={photo} place={place} imagePath={imagePath} />
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
  );
}

/** A Google Maps listing, shaped for the spa page. Services and packages need the Nirvana spa it links to. */
export function placeProfile(
  place: GooglePlace
): Omit<SpaProfileProps, 'media' | 'back' | 'source'> {
  const name = place.displayName?.text || 'Spa details';
  return {
    name,
    address: place.formattedAddress,
    rating: place.rating,
    reviewCount: place.userRatingCount,
    about: place.editorialSummary?.text,
    notice:
      place.businessStatus && place.businessStatus !== 'OPERATIONAL'
        ? place.businessStatus.replaceAll('_', ' ').toLowerCase()
        : undefined,
    openNow: place.currentOpeningHours?.openNow,
    hours: place.regularOpeningHours?.weekdayDescriptions,
    phone: place.internationalPhoneNumber,
    website: safeUrl(place.websiteUri),
    spaId: place.nirvanaSpaId != null ? String(place.nirvanaSpaId) : undefined,
    reviewsTitle: 'Google reviews',
    reviewsNote: place.reviews?.length ? 'sorted by relevance' : undefined,
    reviews: place.reviews?.map((review) => ({
      id: review.name,
      author: review.authorAttribution && <AuthorCredit author={review.authorAttribution} />,
      avatar: safeUrl(review.authorAttribution?.photoUri),
      rating: review.rating,
      text: review.originalText?.text || review.text?.text,
      when: review.relativePublishTimeDescription,
      link: safeUrl(review.googleMapsUri)
    })),
    mapQuery: [name, place.formattedAddress].filter(Boolean).join(', '),
    googlePlaceId: place.id,
    mapUrl: mapsUrl(place)
  };
}

export default function PlacePage({ fromSearch = false }: { fromSearch?: boolean }) {
  const navigate = useNavigate();
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
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, [placeId]);
  useEffect(() => {
    if (query.data?.displayName?.text) document.title = query.data.displayName.text + ' | Nirvana';
  }, [query.data]);
  if (query.data)
    return (
      <SpaProfile
        key={placeId}
        {...placeProfile(query.data)}
        back={fromSearch ? { onClick: () => navigate(-1), label: 'Back to results' } : { to: '/discover', label: 'Search spas' }}
        source={<Attribution place={query.data} />}
        media={<PlaceGallery place={query.data} />}
      />
    );
  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:py-10">
      {fromSearch && <button type="button" className="mb-5 font-medium text-primary-700" onClick={() => navigate(-1)}>← Back to results</button>}
      {query.isPending && <p role="status">Loading spa details…</p>}
      {query.isError && (
        <div role="alert">
          {errorMessage(query.error)}{' '}
          <Button variant="secondary" onClick={() => void query.refetch()}>
            Retry
          </Button>
        </div>
      )}
    </div>
  );
}
