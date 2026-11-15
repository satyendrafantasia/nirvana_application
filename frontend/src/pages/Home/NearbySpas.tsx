import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import { DiscoveryCard, type GooglePlace } from '../Discovery/Discovery';

export const NEARBY_RADIUS_METERS = 5000;
export const NEARBY_LIMIT = 6;

/** Why the browser would not give a position, in words the visitor can act on. */
export const locationProblem = (code?: number) =>
  code === 1
    ? 'Location permission is blocked. Allow it for this site in your browser, or search by city instead.'
    : code === 3
      ? 'Finding your location took too long. Try again, or search by city instead.'
      : 'Your location is unavailable right now. Try again, or search by city instead.';

/** Live Google Places results around the visitor, shown on the home page. */
export default function NearbySpas({ signedIn }: { signedIn: boolean }) {
  // Results and coordinates stay in component memory only: never in storage, the URL or the query cache.
  const [places, setPlaces] = useState<GooglePlace[]>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [unavailable, setUnavailable] = useState(false);
  const abort = useRef<AbortController>();

  const locate = () => {
    if (!navigator.geolocation) {
      setError('Location is unavailable in this browser. Search by city instead.');
      return;
    }
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    setBusy(true);
    setError('');
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const position = { latitude: coords.latitude, longitude: coords.longitude };
          // Signed-out visitors use the public endpoint: fixed radius and count, no photos.
          const { data } = signedIn
            ? await client.post<{ places: GooglePlace[] }>(
                '/api/discovery/search',
                {
                  mode: 'NEARBY',
                  countryCode: 'IN', // required by the API; a nearby search is bounded by the circle, not the country
                  ...position,
                  radiusMeters: NEARBY_RADIUS_METERS,
                  rank: 'DISTANCE'
                },
                { signal: controller.signal }
              )
            : await client.post<{ places: GooglePlace[] }>('/api/public/discovery/nearby', position, {
                signal: controller.signal
              });
          if (!controller.signal.aborted) setPlaces(data.places.slice(0, NEARBY_LIMIT));
        } catch (e) {
          if (!controller.signal.aborted) setError(errorMessage(e));
        } finally {
          if (!controller.signal.aborted) setBusy(false);
        }
      },
      (failure) => {
        if (controller.signal.aborted) return;
        setBusy(false);
        setError(locationProblem(failure.code));
      },
      { timeout: 10000, maximumAge: 5 * 60 * 1000, enableHighAccuracy: false }
    );
  };

  useEffect(() => {
    const controller = new AbortController();
    setPlaces(undefined);
    setError('');
    (async () => {
      try {
        const { data } = await client.get<{ configured: boolean }>(
          signedIn ? '/api/discovery/status' : '/api/public/discovery/status',
          { signal: controller.signal }
        );
        if (!data.configured) {
          setUnavailable(true);
          return;
        }
        // Only look without asking when the visitor has already allowed location for this site.
        const permission = await navigator.permissions?.query({ name: 'geolocation' });
        if (!controller.signal.aborted && permission?.state === 'granted') locate();
      } catch {
        // The button below still works; a failed status check is not worth an error on the home page.
      }
    })();
    return () => {
      controller.abort();
      abort.current?.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signedIn]);

  return (
    <section className="nearby-spas" aria-labelledby="nearby-heading">
      <div className="nearby-heading">
        <div>
          <p className="eyebrow"><span /> CLOSE TO YOU</p>
          <h2 id="nearby-heading">Spas near you</h2>
        </div>
        {places && (
          <Link to="/discover" className="text-sm font-semibold text-primary-600">
            Search another area
          </Link>
        )}
      </div>
      {unavailable ? (
        <p className="listing-empty text-sm text-slate-500">
          Nearby search is not available yet.{' '}
          <Link className="underline" to="/spas">
            Browse Nirvana spas
          </Link>
        </p>
      ) : (
        <>
          {!places && (
            <div className="listing-empty space-y-3 text-sm text-slate-600">
              <p>
                See spas within {NEARBY_RADIUS_METERS / 1000} km of you, from Google Maps. Your
                location is sent to Nirvana and Google for this search and is not saved.
              </p>
              <Button type="button" disabled={busy} onClick={locate}>
                {busy ? 'Finding spas near you…' : 'Show spas near me'}
              </Button>
            </div>
          )}
          {error && (
            <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">
              {error}
            </p>
          )}
          {places?.length === 0 && (
            <p className="listing-empty text-sm text-slate-500">
              No spas found within {NEARBY_RADIUS_METERS / 1000} km.{' '}
              <Link className="underline" to="/discover">
                Search a wider area
              </Link>
            </p>
          )}
          {!!places?.length && (
            <>
              <p className="text-sm text-slate-600">
                Nearest first, from Google Maps. Spas you can book on Nirvana are marked.
                {!signedIn && (
                  <>
                    {' '}
                    <Link className="underline" to="/auth/login?redirect=/">
                      Sign in
                    </Link>{' '}
                    to see photos and full details.
                  </>
                )}
              </p>
              <div className="grid gap-6 md:grid-cols-3">
                {places.map((place) => (
                  <DiscoveryCard key={place.id} place={place} />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
