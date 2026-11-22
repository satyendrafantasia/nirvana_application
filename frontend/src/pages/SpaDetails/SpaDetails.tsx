import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import client from '../../api/client';
import { getSpaDetails } from '../../api/spaApi';
import Spinner from '../../components/common/Spinner';
import SpaInformation from '../../components/spa/SpaInformation';
import SpaProfile, { type SpaProfileProps } from '../../components/spa/SpaProfile';
import { Spa } from '../../types/spa';
import type { GooglePlace } from '../Discovery/Discovery';
import { PlaceGallery } from '../Discovery/PlacePage';
import { Attribution } from '../Discovery/PlaceMedia';

type GuestReview = { id: number; rating: number; title: string; text: string; createdAt?: string };
const safeUrl = (url?: string) => (url && /^https?:\/\//i.test(url) ? url : undefined);

/** A Nirvana spa's own record, shaped for the spa page. */
export function spaProfile(spa: Spa): Omit<SpaProfileProps, 'media' | 'back' | 'reviewsTitle'> {
  const mapQuery =
    spa.latitude != null && spa.longitude != null
      ? `${spa.latitude},${spa.longitude}`
      : [spa.name, spa.address].filter(Boolean).join(', ');
  return {
    name: spa.name,
    address: spa.address,
    rating: spa.rating,
    reviewCount: spa.reviewCount,
    about: spa.description,
    hours: spa.openTimeLocal
      ? [
          `${spa.workingDays?.join(', ') || 'Every day'} · ${spa.openTimeLocal} – ${spa.closeTimeLocal || ''} ${spa.timezone || ''}`.trim()
        ]
      : undefined,
    phone: spa.phone,
    website: safeUrl(spa.websiteUrl),
    email: spa.email,
    amenities: spa.amenities,
    spaId: spa.id,
    mapQuery,
    googlePlaceId: spa.googlePlaceId,
    mapUrl: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(mapQuery)}${
      spa.googlePlaceId ? `&query_place_id=${encodeURIComponent(spa.googlePlaceId)}` : ''
    }`
  };
}

const SpaDetails = () => {
  const { spaId = '' } = useParams();
  const spa = useQuery({
    queryKey: ['spa', spaId],
    queryFn: () => getSpaDetails(spaId),
    enabled: Boolean(spaId)
  });
  const reviews = useQuery({
    queryKey: ['reviews', spaId],
    queryFn: async () =>
      (await client.get<{ page: { content: GuestReview[] } }>(`/api/spas/${spaId}/reviews`)).data
        .page.content,
    enabled: Boolean(spaId)
  });

  // A spa with no photos of its own shows its Google Maps photos, loaded live with Google's credits.
  const googlePhotos = useQuery({
    queryKey: ['spa-google-photos', spaId],
    queryFn: async ({ signal }) =>
      (await client.get<GooglePlace>(`/api/spas/${spaId}/google-photos`, { signal })).data,
    enabled: Boolean(spa.data && !spa.data.coverImage && spa.data.googlePlaceId),
    retry: false,
    refetchOnWindowFocus: false,
    staleTime: 5 * 60 * 1000
  });

  if (spa.isError)
    return (
      <p role="alert" className="p-10">
        Unable to load this spa. Please retry from search.
      </p>
    );
  if (!spa.data) {
    return (
      <div className="flex h-96 items-center justify-center">
        <Spinner />
      </div>
    );
  }

  return (
    <SpaProfile
      {...spaProfile(spa.data)}
      back={{ to: '/spas', label: 'All spas' }}
      source={googlePhotos.data?.photos?.length ? <Attribution place={googlePhotos.data} /> : undefined}
      media={
        googlePhotos.data?.photos?.length ? (
          <PlaceGallery
            // Google sends only the photos (its cheapest tier); the name is the spa's own.
            place={{ ...googlePhotos.data, displayName: { text: spa.data.name } }}
            imagePath={(photoId) =>
              `/api/spas/${encodeURIComponent(spaId)}/google-photos/${encodeURIComponent(photoId)}/image`
            }
          />
        ) : (
        <img
          src={
            spa.data.coverImage ||
            'https://images.unsplash.com/photo-1556228724-4a44ebcdf3a8?auto=format&fit=crop&w=1200&q=80'
          }
          width={1200}
          height={720}
          alt={spa.data.coverImage ? spa.data.name : ''}
          className="h-72 w-full rounded-2xl object-cover sm:h-96"
        />
        )
      }
      reviewsTitle="Guest reviews"
      reviewsState={reviews.isLoading ? 'loading' : reviews.isError ? 'error' : undefined}
      reviews={reviews.data?.map((r) => ({
        id: String(r.id),
        author: <span>Verified guest</span>,
        rating: r.rating,
        title: r.title,
        text: r.text,
        when: r.createdAt ? new Date(r.createdAt).toLocaleDateString() : undefined
      }))}
    >
      <SpaInformation />
    </SpaProfile>
  );
};

export default SpaDetails;
