import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { enquiryLink, ShareStorefront, storefrontPath } from '../../components/spa/StorefrontLinks';
import Button from '../../components/common/Button';
import { isAxiosError } from 'axios';
import client from '../../api/client';
import { getSpaDetails } from '../../api/spaApi';
import Spinner from '../../components/common/Spinner';
import SpaInformation from '../../components/spa/SpaInformation';
import SpaPhoto from '../../components/spa/SpaPhoto';
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

type Storefront = {spaId: number; name: string; whatsappNumber: string | null};

const SpaDetails = ({ storefront }: { storefront?: Storefront }) => {
  const { spaId = '' } = useParams();
  const spa = useQuery({
    queryKey: ['spa', spaId],
    queryFn: () => getSpaDetails(spaId),
    enabled: Boolean(spaId),
    staleTime: 60_000
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
    enabled: Boolean(!storefront && spa.data && !spa.data.coverImage && spa.data.googlePlaceId),
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
      storefrontActions={storefront && <>
        {enquiryLink(storefront.whatsappNumber, storefront.name) && <Button as="a" variant="secondary" href={enquiryLink(storefront.whatsappNumber, storefront.name)} target="_blank" rel="noopener noreferrer">Ask on WhatsApp</Button>}
        <ShareStorefront path={storefrontPath(spaId, storefront.name)}/>
      </>}
      source={!storefront && googlePhotos.data?.photos?.length ? <Attribution place={googlePhotos.data} /> : undefined}
      media={
        !storefront && googlePhotos.data?.photos?.length ? (
          <PlaceGallery
            // Google sends only the photos (its cheapest tier); the name is the spa's own.
            place={{ ...googlePhotos.data, displayName: { text: spa.data.name } }}
            imagePath={(photoId) =>
              `/api/spas/${encodeURIComponent(spaId)}/google-photos/${encodeURIComponent(photoId)}/image`
            }
          />
        ) : (
        <SpaPhoto src={spa.data.coverImage} sizes="(min-width:768px) 560px, 100vw"
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

export function ClaimedStorefront() {
  const {spaId = ''} = useParams();
  const storefront = useQuery({queryKey: ['public-storefront', spaId], retry: false,
    queryFn: async () => (await client.get<Storefront>(`/api/spas/${encodeURIComponent(spaId)}/storefront`)).data});
  if (storefront.isError) {
    const missing = isAxiosError(storefront.error) && storefront.error.response?.status === 404;
    return <section className="mx-auto max-w-xl p-8 space-y-3"><h1>{missing ? 'Storefront unavailable' : 'Could not load storefront'}</h1>
      {!missing && <Button onClick={() => void storefront.refetch()}>Try again</Button>} <Link className="underline" to="/spas">Browse spas</Link></section>;
  }
  return storefront.data ? <SpaDetails storefront={storefront.data}/> : <Spinner/>;
}

export default SpaDetails;
