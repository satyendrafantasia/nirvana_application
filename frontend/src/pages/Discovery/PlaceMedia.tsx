import { useEffect, useRef, useState } from 'react';
import client from '../../api/client';
import PlaceDetailsLink from './PlaceDetailsLink';
import type { GooglePlace, Photo, Author } from './Discovery';
export const safeUrl = (url?: string) => (url && /^https?:\/\//i.test(url) ? url : undefined);
export const mapsUrl = (place: GooglePlace) =>
  safeUrl(place.googleMapsUri) ||
  `https://www.google.com/maps/search/?api=1&query=spa&query_place_id=${encodeURIComponent(place.id)}`;
export function Attribution({ place }: { place: GooglePlace }) {
  return (
    <div className="text-xs text-slate-600 space-y-1">
      <p translate="no" className="font-normal text-[#5e5e5e] whitespace-nowrap">
        Google Maps
      </p>
      {place.attributions?.map((a, i) => (
        <p key={i}>
          {safeUrl(a.providerUri) ? (
            <a href={safeUrl(a.providerUri)} target="_blank" rel="noreferrer">
              {a.provider}
            </a>
          ) : (
            a.provider
          )}
        </p>
      ))}
    </div>
  );
}
export function AuthorCredit({ author }: { author: Author }) {
  const label = author.displayName || 'Google Maps contributor';
  return safeUrl(author.uri) ? (
    <a className="underline" href={safeUrl(author.uri)} target="_blank" rel="noreferrer">
      {label}
    </a>
  ) : (
    <span>{label}</span>
  );
}

export function PhotoPlaceholder() {
  return (
    <div
      aria-label="No spa photo available"
      role="img"
      className="flex h-full items-center justify-center bg-primary-50"
    >
      <img src="/nirvana.svg" width="56" height="56" alt="" />
    </div>
  );
}

export function SpaPhoto({
  photo,
  place,
  cover = false,
  imagePath
}: {
  photo: Photo;
  place: GooglePlace;
  cover?: boolean;
  /** Where to load the image from, when not the signed-in discovery endpoint (for example a spa page guests can open). */
  imagePath?: (photoId: string) => string;
}) {
  const [url, setUrl] = useState('');
  const [failed, setFailed] = useState(false);
  const frame = useRef<HTMLElement>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    const photoId = photo.name.split('/').pop() || '';
    let objectUrl: string | undefined;
    setFailed(false);
    setUrl('');
    const load = () =>
      client
        .get<Blob>(
          imagePath
            ? imagePath(photoId)
            : `/api/discovery/places/${encodeURIComponent(place.id)}/photos/${encodeURIComponent(photoId)}/image`,
          { signal: controller.signal, responseType: 'blob' }
        )
        .then(({ data }) => {
          if (controller.signal.aborted) return;
          objectUrl = URL.createObjectURL(data);
          setUrl(objectUrl);
        })
        .catch(() => {
          if (!controller.signal.aborted) setFailed(true);
        });
    let observer: IntersectionObserver | undefined;
    if (cover && frame.current && typeof IntersectionObserver !== 'undefined') {
      observer = new IntersectionObserver(
        ([entry]) => {
          if (!entry.isIntersecting || controller.signal.aborted) return;
          observer?.disconnect();
          void load();
        },
        { rootMargin: '160px 0px' }
      );
      observer.observe(frame.current);
    } else {
      void load();
    }
    return () => {
      observer?.disconnect();
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photo.name, place.id, attempt, cover]);
  const photoImage = url && (
    <img
      className={cover ? 'h-full w-full object-cover' : 'w-full rounded-lg object-contain max-h-72'}
      src={url}
      alt={`Photo of ${place.displayName?.text || 'this spa'}`}
      onError={() => setFailed(true)}
    />
  );
  return (
    <figure ref={frame} className={cover ? 'relative h-full bg-primary-50' : 'space-y-1'}>
      {failed && cover ? (
        <PhotoPlaceholder />
      ) : failed ? (
        <p role="status">
          Photo unavailable.{' '}
          <button type="button" className="underline" onClick={() => setAttempt(attempt + 1)}>
            Retry photo
          </button>
        </p>
      ) : url ? (
        cover ? (
          <PlaceDetailsLink placeId={place.id}>{photoImage}</PlaceDetailsLink>
        ) : (
          <a href={safeUrl(photo.googleMapsUri) || mapsUrl(place)} target="_blank" rel="noreferrer">
            {photoImage}
          </a>
        )
      ) : cover ? null : (
        <p role="status">Loading photo…</p>
      )}
      {url && !failed && !!photo.authorAttributions?.length && (
        <figcaption
          className={
            cover
              ? 'absolute bottom-0 inset-x-0 bg-white/95 px-2 py-1 text-xs text-slate-800'
              : 'text-xs'
          }
        >
          {photo.authorAttributions?.map((author, index) => (
            <span className="mr-2" key={index}>
              <AuthorCredit author={author} />
            </span>
          ))}
        </figcaption>
      )}
    </figure>
  );
}
