import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';

type Point = { latitude: number; longitude: number };
type Bounds = { low: Point; high: Point };
type Attribution = { provider: string; providerUri?: string };
export type GooglePlace = {
  id: string;
  displayName?: { text: string };
  formattedAddress?: string;
  rating?: number;
  userRatingCount?: number;
  businessStatus?: string;
  googleMapsUri?: string;
  websiteUri?: string;
  internationalPhoneNumber?: string;
  attributions?: Attribution[];
  nirvanaSpaId?: number;
  location?: Point;
  viewport?: Bounds;
};
type Search = {
  mode: 'TEXT' | 'NEARBY';
  city?: string;
  locality?: string;
  countryCode: string;
  bounds?: Bounds;
  latitude?: number;
  longitude?: number;
  radiusMeters?: number;
  rank?: 'DISTANCE' | 'POPULARITY';
  pageToken?: string;
};
const safeUrl = (url?: string) => (url && /^https?:\/\//i.test(url) ? url : undefined);
export const mapsUrl = (place: GooglePlace) =>
  safeUrl(place.googleMapsUri) ||
  `https://www.google.com/maps/search/?api=1&query=spa&query_place_id=${encodeURIComponent(place.id)}`;
function Attribution({ place }: { place: GooglePlace }) {
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
export function DiscoveryCard({
  place,
  onDetails,
  busy
}: {
  place: GooglePlace;
  onDetails: () => void;
  busy: boolean;
}) {
  return (
    <article className="card p-5 space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="text-xl font-semibold">{place.displayName?.text || 'Spa'}</h3>
        <span className="rounded-full bg-primary-50 px-3 py-1 text-xs">
          {place.nirvanaSpaId ? 'On Nirvana' : 'Google discovery'}
        </span>
      </div>
      <p className="text-sm text-slate-600">{place.formattedAddress}</p>
      {place.rating != null && (
        <p className="text-sm">
          Google rating: {place.rating} / 5 ({place.userRatingCount ?? 0} ratings)
        </p>
      )}
      {place.businessStatus && place.businessStatus !== 'OPERATIONAL' && (
        <p className="text-sm">{place.businessStatus.replaceAll('_', ' ').toLowerCase()}</p>
      )}
      <div className="flex flex-wrap gap-3 items-center">
        {place.nirvanaSpaId ? (
          <Button as={Link} to={`/spas/${place.nirvanaSpaId}`}>
            Book on Nirvana
          </Button>
        ) : (
          <a className="underline" href={mapsUrl(place)} target="_blank" rel="noreferrer">
            View on Google Maps
          </a>
        )}
        <Button variant="secondary" disabled={busy} onClick={onDetails}>
          View details
        </Button>
      </div>
      {!place.nirvanaSpaId && (
        <p className="text-xs text-slate-500">Not currently bookable on Nirvana.</p>
      )}
      <Attribution place={place} />
    </article>
  );
}

export default function Discovery({
  admin = false,
  onSaved
}: {
  admin?: boolean;
  onSaved?: () => void;
}) {
  const [mode, setMode] = useState<'TEXT' | 'NEARBY'>('TEXT');
  const [city, setCity] = useState('');
  const [locality, setLocality] = useState('');
  const [country, setCountry] = useState('IN');
  const [address, setAddress] = useState('');
  const [radius, setRadius] = useState(3000);
  const [rank, setRank] = useState<'DISTANCE' | 'POPULARITY'>('DISTANCE');
  const [selected, setSelected] = useState<GooglePlace>();
  const [locations, setLocations] = useState<GooglePlace[]>([]);
  const [results, setResults] = useState<GooglePlace[]>();
  const [details, setDetails] = useState<GooglePlace>();
  const [cursor, setCursor] = useState<{ request: Search; page: number }>();
  const [searched, setSearched] = useState<Search>();
  const [configured, setConfigured] = useState<boolean>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const abort = useRef<AbortController>();
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    const controller = new AbortController();
    client
      .get<{ configured: boolean }>('/api/discovery/status', { signal: controller.signal })
      .then(({ data }) => setConfigured(data.configured))
      .catch((e) => {
        if (!controller.signal.aborted) setError(errorMessage(e));
      });
    return () => {
      mounted.current = false;
      controller.abort();
      abort.current?.abort();
    };
  }, []);
  // Provider content and coordinates live only in component memory, never localStorage/query cache.
  const clear = () => {
    setResults(undefined);
    setDetails(undefined);
    setCursor(undefined);
    setSearched(undefined);
    setMessage('');
    setError('');
  };
  const resetLocation = () => {
    clear();
    setSelected(undefined);
    setLocations([]);
  };
  const run = async (work: (signal: AbortSignal) => Promise<void>) => {
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      await work(controller.signal);
    } catch (e) {
      if (!controller.signal.aborted) setError(errorMessage(e));
    } finally {
      if (mounted.current) setBusy(false);
    }
  };
  const search = async (request: Search, page = 1) =>
    run(async (signal) => {
      setResults(undefined);
      setDetails(undefined);
      setCursor(undefined);
      const { data } = await client.post<{ places: GooglePlace[]; nextPageToken: string }>(
        '/api/discovery/search',
        request,
        { signal }
      );
      setResults(Array.from(new Map(data.places.map((p) => [p.id, p])).values()));
      setSearched(request);
      setCursor(
        request.mode === 'TEXT' && data.nextPageToken && page < 3
          ? { request: { ...request, pageToken: data.nextPageToken }, page: page + 1 }
          : undefined
      );
      setMessage(
        request.mode === 'TEXT'
          ? `Google results · page ${page} of up to 3. Results are ranked, not a complete directory.`
          : 'Up to 20 nearby results. Change the radius or ranking to refine your search.'
      );
    });
  const resolve = () =>
    run(async (signal) => {
      setSelected(undefined);
      setLocations([]);
      clear();
      const query =
        mode === 'TEXT' ? [locality, city, country].filter(Boolean).join(', ') : address;
      const { data } = await client.post<{ places: GooglePlace[] }>(
        '/api/discovery/locations',
        { query, countryCode: country },
        { signal }
      );
      setLocations(data.places.filter((p) => p.location && (mode !== 'TEXT' || p.viewport)));
      setMessage('Choose the matching location below. If none match, refine the address or area.');
    });
  const nearMe = () =>
    run(async (signal) => {
      clear();
      setSelected(undefined);
      setLocations([]);
      if (!navigator.geolocation)
        throw new Error('Location is unavailable in this browser. Search for an address instead.');
      const position = await new Promise<GeolocationPosition>((resolve, reject) =>
        navigator.geolocation.getCurrentPosition(
          resolve,
          () =>
            reject(
              new Error(
                'Location could not be shared. Allow browser permission or choose an address instead.'
              )
            ),
          { timeout: 10000, maximumAge: 0, enableHighAccuracy: false }
        )
      );
      if (signal.aborted || !mounted.current) return;
      clear();
      setLocations([]);
      setSelected({
        id: 'current-location',
        displayName: { text: 'Your current location' },
        location: { latitude: position.coords.latitude, longitude: position.coords.longitude }
      });
      setMessage('Location selected. Choose a radius, then search nearby.');
    });
  const submit = () => {
    if (mode === 'TEXT')
      void search({
        mode,
        city: city.trim(),
        locality: locality.trim(),
        countryCode: country,
        bounds: selected?.viewport
      });
    else if (selected?.location)
      void search({ mode, countryCode: country, ...selected.location, radiusMeters: radius, rank });
  };
  const save = () =>
    run(async (signal) => {
      await client.post(
        '/api/admin/city-discovery/references',
        { city: city.trim(), countryCode: country, placeIds: results!.map((p) => p.id) },
        { signal }
      );
      setMessage('Place ID references saved. Business details and coordinates were not saved.');
      onSaved?.();
    });
  return (
    <section
      className={admin ? 'space-y-5 card p-5' : 'mx-auto max-w-6xl p-6 space-y-6'}
      aria-label="Google spa discovery"
    >
      <div>
        <p className="eyebrow">EXPLORE YOUR NEXT RESET</p>
        <h1 className="text-3xl font-semibold mt-3">Discover spas around you</h1>
        <p className="mt-3 text-slate-600">
          Explore live Google Maps listings. Book directly when a spa is available on Nirvana.
        </p>
      </div>
      {configured === false && (
        <p role="status" className="rounded-xl bg-amber-50 p-4">
          {admin
            ? 'Setup required: configure GOOGLE_PLACES_API_KEY on the backend with Places API (New) and billing enabled.'
            : 'Google discovery is not available yet. You can still browse spas on Nirvana.'}{' '}
          <Link className="underline" to="/spas">
            Browse Nirvana spas
          </Link>
        </p>
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <fieldset
          disabled={busy || configured !== true}
          className="card p-5 space-y-5 disabled:opacity-70"
        >
          <legend className="sr-only">Discovery search</legend>
          <div className="flex flex-wrap gap-3">
            {(['TEXT', 'NEARBY'] as const).map((m) => (
              <Button
                key={m}
                type="button"
                variant={mode === m ? 'primary' : 'secondary'}
                aria-pressed={mode === m}
                onClick={() => {
                  setMode(m);
                  resetLocation();
                }}
              >
                {m === 'TEXT' ? 'City & locality' : 'Near a location'}
              </Button>
            ))}
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Country code"
              value={country}
              required
              pattern="[A-Za-z]{2}"
              maxLength={2}
              onChange={(e) => {
                setCountry(e.target.value.toUpperCase());
                resetLocation();
              }}
            />
            {(mode === 'TEXT' || admin) && (
              <Input
                label={mode === 'TEXT' ? 'City' : 'Save references under city (optional)'}
                value={city}
                required={mode === 'TEXT'}
                maxLength={100}
                placeholder="Bengaluru, Karnataka"
                onChange={(e) => {
                  setCity(e.target.value);
                  resetLocation();
                }}
              />
            )}
            {mode === 'TEXT' ? (
              <Input
                label="Locality (optional)"
                value={locality}
                maxLength={100}
                placeholder="Indiranagar"
                onChange={(e) => {
                  setLocality(e.target.value);
                  resetLocation();
                }}
              />
            ) : (
              <Input
                label="Address, hotel or landmark"
                value={address}
                maxLength={250}
                placeholder="Enter an address to choose a location"
                onChange={(e) => {
                  setAddress(e.target.value);
                  resetLocation();
                }}
              />
            )}
          </div>
          {(mode === 'NEARBY' || locality.trim()) && (
            <div className="space-y-3">
              <Button
                type="button"
                variant="secondary"
                disabled={
                  country.length !== 2 ||
                  (mode === 'TEXT' ? !city.trim() || !locality.trim() : !address.trim())
                }
                onClick={() => void resolve()}
              >
                {mode === 'TEXT' ? 'Find locality boundary' : 'Find address'}
              </Button>
              {locations.length > 0 && (
                <div className="grid gap-3" aria-label="Choose a location">
                  {locations.map((p) => (
                    <div key={p.id} className="border rounded-xl p-3 space-y-2">
                      <button
                        type="button"
                        className="text-left underline"
                        onClick={() => {
                          setSelected(p);
                          setLocations([]);
                          clear();
                        }}
                      >
                        {p.displayName?.text} — {p.formattedAddress}
                      </button>
                      <Attribution place={p} />
                    </div>
                  ))}
                </div>
              )}
              {selected && (
                <p role="status">
                  Selected: {selected.displayName?.text}
                  {selected.formattedAddress ? ` — ${selected.formattedAddress}` : ''}
                </p>
              )}
            </div>
          )}
          {mode === 'NEARBY' && (
            <>
              <p className="text-xs text-slate-600">
                Use my location asks for browser permission. Searching nearby sends your selected
                coordinates to Nirvana and Google to find spas; they are not saved by this discovery
                feature.
              </p>
              <Button type="button" variant="secondary" onClick={() => void nearMe()}>
                Use my location
              </Button>
              <div className="grid sm:grid-cols-2 gap-4">
                <label className="grid gap-2 text-sm">
                  Radius
                  <select
                    className="border rounded-xl p-3"
                    value={radius}
                    onChange={(e) => {
                      setRadius(Number(e.target.value));
                      clear();
                    }}
                  >
                    {[1000, 3000, 5000, 10000, 25000, 50000].map((n) => (
                      <option key={n} value={n}>
                        {n / 1000} km
                      </option>
                    ))}
                  </select>
                </label>
                <label className="grid gap-2 text-sm">
                  Sort by
                  <select
                    className="border rounded-xl p-3"
                    value={rank}
                    onChange={(e) => {
                      setRank(e.target.value as typeof rank);
                      clear();
                    }}
                  >
                    <option value="DISTANCE">Distance</option>
                    <option value="POPULARITY">Popularity</option>
                  </select>
                </label>
              </div>
            </>
          )}
          <Button
            type="submit"
            disabled={
              mode === 'TEXT'
                ? !city.trim() || (!!locality.trim() && !selected?.viewport)
                : !selected?.location
            }
          >
            {mode === 'TEXT' ? 'Search city / locality' : 'Search nearby'}
          </Button>
        </fieldset>
      </form>
      {busy && <p role="status">Loading…</p>}
      {error && (
        <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">
          {error}
        </p>
      )}
      {message && (
        <p role="status" className="text-sm text-slate-600">
          {message}
        </p>
      )}
      {results && (
        <section aria-label="Live Google results" className="space-y-4">
          <p className="text-sm">
            Google Maps results · live information. Availability and prices come from onboarded
            spas.
          </p>
          {results.length === 0 && (
            <p>No spas found. Try another city, locality, address or radius.</p>
          )}
          <div className="grid md:grid-cols-2 gap-4">
            {results.map((place) => (
              <DiscoveryCard
                key={place.id}
                place={place}
                busy={busy}
                onDetails={() =>
                  void run(async (signal) => {
                    setDetails(undefined);
                    const { data } = await client.get<GooglePlace>(
                      `/api/discovery/places/${encodeURIComponent(place.id)}`,
                      { signal }
                    );
                    setDetails({ ...data, id: place.id });
                  })
                }
              />
            ))}
          </div>
          <div className="flex flex-wrap gap-3">
            {cursor && (
              <Button disabled={busy} onClick={() => void search(cursor.request, cursor.page)}>
                Next Google page
              </Button>
            )}
            {admin && results.length > 0 && (
              <Button
                disabled={busy || !city.trim() || !searched}
                variant="secondary"
                onClick={() => void save()}
              >
                Save these Place ID references
              </Button>
            )}
          </div>
        </section>
      )}
      {details && (
        <section aria-label="Live spa details" className="card p-5 space-y-3">
          <div className="flex justify-between gap-4">
            <h2 className="text-2xl">{details.displayName?.text || 'Spa details'}</h2>
            <button onClick={() => setDetails(undefined)}>Close details</button>
          </div>
          <p>{details.formattedAddress}</p>
          <p>{details.internationalPhoneNumber}</p>
          {safeUrl(details.websiteUri) && (
            <a
              className="underline block"
              href={safeUrl(details.websiteUri)}
              target="_blank"
              rel="noreferrer"
            >
              Business website
            </a>
          )}
          <a className="underline block" href={mapsUrl(details)} target="_blank" rel="noreferrer">
            View on Google Maps
          </a>
          <Attribution place={details} />
        </section>
      )}
      <p className="text-xs text-slate-500">
        Google discovery is ranked and does not list every spa. A Google listing alone does not make
        a business bookable on Nirvana. Owners provide their own business details, services, prices
        and availability during onboarding.
      </p>
    </section>
  );
}
