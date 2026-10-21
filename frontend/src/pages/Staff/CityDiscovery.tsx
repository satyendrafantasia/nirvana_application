import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { useAuth } from '../../hooks/useAuth';
import { isAdmin } from '../../auth/roles';

type City = { city: string; countryCode: string; places: number };
type Place = { placeId: string; discoveredAt: string };
type Details = {
  displayName?: { text: string };
  formattedAddress?: string;
  internationalPhoneNumber?: string;
  websiteUri?: string;
  googleMapsUri?: string;
  attributions?: { provider: string; providerUri?: string }[];
};
const safeUrl = (url?: string) => (url && /^https?:\/\//i.test(url) ? url : undefined);
export default function CityDiscovery() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const allowed = isAdmin(user?.roles);
  const [city, setCity] = useState('');
  const [country, setCountry] = useState('IN');
  const [selected, setSelected] = useState<City>();
  const [page, setPage] = useState(0);
  const [cursor, setCursor] = useState<{ city: string; country: string; token: string }>();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [details, setDetails] = useState<Details>();
  const status = useQuery({
    queryKey: ['city-discovery-status', user?.id],
    enabled: allowed,
    queryFn: async () =>
      (await client.get<{ configured: boolean }>('/api/admin/city-discovery/status')).data
  });
  const cities = useQuery({
    queryKey: ['city-discovery-cities', user?.id],
    enabled: allowed,
    queryFn: async () => (await client.get<City[]>('/api/admin/city-discovery/cities')).data
  });
  const saved = useQuery({
    queryKey: ['city-discovery-places', user?.id, selected?.city, selected?.countryCode, page],
    enabled: allowed && !!selected,
    queryFn: async () =>
      (
        await client.get<{ content: Place[]; total: number }>('/api/admin/city-discovery/places', {
          params: { city: selected!.city, countryCode: selected!.countryCode, page }
        })
      ).data
  });
  const search = async (next = false) => {
    setBusy(true);
    setError('');
    setMessage('');
    setDetails(undefined);
    const queryCity = next ? cursor!.city : city.trim();
    const queryCountry = next ? cursor!.country : country.trim().toUpperCase();
    try {
      const { data } = await client.post<{
        city: string;
        countryCode: string;
        found: number;
        nextPageToken: string;
      }>('/api/admin/city-discovery/search', {
        city: queryCity,
        countryCode: queryCountry,
        pageToken: next ? cursor?.token : undefined
      });
      setSelected({ city: data.city, countryCode: data.countryCode, places: 0 });
      setPage(0);
      setCursor(
        data.nextPageToken
          ? { city: queryCity, country: queryCountry, token: data.nextPageToken }
          : undefined
      );
      setMessage(
        `${data.found} results processed. Existing Place IDs are kept without duplicates.${data.nextPageToken ? ' More results are available.' : ' Google returned no further pages.'}`
      );
      await cities.refetch();
      await queryClient.invalidateQueries({ queryKey: ['city-discovery-places'] });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const view = async (id: string) => {
    setBusy(true);
    setError('');
    setDetails(undefined);
    try {
      setDetails(
        (await client.get<Details>(`/api/admin/city-discovery/places/${encodeURIComponent(id)}`))
          .data
      );
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  if (!allowed)
    return (
      <div className="max-w-6xl mx-auto p-8">Only platform administrators can import cities.</div>
    );
  return (
    <div className="max-w-6xl mx-auto p-6 space-y-6">
      <h1 className="text-3xl font-semibold">Discover spas by city</h1>
      <p>
        Search Google Maps for spa businesses, city by city. Save references for review, then
        onboard businesses using details provided by their owners.
      </p>
      <div className="rounded-xl bg-amber-50 p-4 text-sm space-y-2">
        <p>
          Only Place IDs and your search city are saved. Business details below are loaded live from
          Google Maps. Discovered businesses are not published or bookable on Nirvana.
        </p>
        <p>
          Each search loads up to 20 results. Google search is ranked and may include nearby places;
          it is not a complete city directory. API charges may apply.
        </p>
        {status.data && !status.data.configured && (
          <p className="font-semibold">
            Setup required: configure GOOGLE_PLACES_API_KEY on the backend with Places API (New)
            enabled and billing configured, then restart the backend.
          </p>
        )}
      </div>
      <form
        className="flex flex-wrap items-end gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          void search();
        }}
      >
        <label className="grid gap-1">
          City
          <input
            className="border rounded p-2"
            required
            maxLength={100}
            placeholder="Bengaluru, Karnataka"
            value={city}
            onChange={(e) => {
              setCity(e.target.value);
              setCursor(undefined);
            }}
          />
        </label>
        <label className="grid gap-1">
          Country code
          <input
            className="border rounded p-2 w-24"
            required
            pattern="[A-Za-z]{2}"
            maxLength={2}
            value={country}
            onChange={(e) => {
              setCountry(e.target.value);
              setCursor(undefined);
            }}
          />
        </label>
        <button
          className="rounded bg-primary-700 text-white px-4 py-2 disabled:opacity-50"
          disabled={busy || !status.data?.configured}
        >
          Search and save references
        </button>
        {cursor && (
          <button
            type="button"
            className="border rounded px-4 py-2"
            disabled={busy}
            onClick={() => void search(true)}
          >
            Import next Google page
          </button>
        )}
      </form>
      {(error || status.error || cities.error || saved.error) && (
        <p role="alert" className="text-red-700">
          {error || errorMessage(status.error || cities.error || saved.error)}
        </p>
      )}
      {message && <p role="status">{message}</p>}
      {busy && <p role="status">Contacting Google Places…</p>}
      <h2 className="text-xl font-semibold">Imported cities</h2>
      <div className="flex flex-wrap gap-3">
        {cities.data?.map((c) => (
          <button
            key={`${c.city}-${c.countryCode}`}
            disabled={busy}
            className="border rounded p-3"
            onClick={() => {
              setSelected(c);
              setPage(0);
              setDetails(undefined);
            }}
          >
            {c.city}, {c.countryCode} · {c.places} places
          </button>
        ))}
      </div>
      {cities.data?.length === 0 && <p>No cities imported yet.</p>}
      {selected && (
        <section className="space-y-3">
          <h2 className="text-xl font-semibold">
            {selected.city}, {selected.countryCode} · {saved.data?.total ?? 0} saved references
          </h2>
          <div className="grid sm:grid-cols-2 gap-3">
            {saved.data?.content.map((p) => (
              <div className="border rounded bg-white p-3 space-y-2" key={p.placeId}>
                <p className="break-all text-sm">{p.placeId}</p>
                <button
                  className="text-primary-700 underline mr-4"
                  disabled={busy}
                  onClick={() => void view(p.placeId)}
                >
                  Load business details
                </button>
                <a
                  className="underline"
                  href={`https://www.google.com/maps/search/?api=1&query=spa&query_place_id=${encodeURIComponent(p.placeId)}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Open Google Maps
                </a>
              </div>
            ))}
          </div>
          <div className="flex gap-4">
            <button disabled={page === 0 || busy} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>Page {page + 1}</span>
            <button
              disabled={busy || (page + 1) * 20 >= (saved.data?.total ?? 0)}
              onClick={() => setPage(page + 1)}
            >
              Next saved page
            </button>
          </div>
        </section>
      )}
      {details && (
        <section
          className="bg-white border rounded-xl p-5 space-y-2"
          aria-label="Live Google Maps details"
        >
          <div className="flex justify-between">
            <h2 className="text-xl font-semibold">
              {details.displayName?.text || 'Business details'}
            </h2>
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
          {safeUrl(details.googleMapsUri) && (
            <a
              className="underline block"
              href={safeUrl(details.googleMapsUri)}
              target="_blank"
              rel="noreferrer"
            >
              View on Google Maps
            </a>
          )}
          <p translate="no" className="font-normal text-[#5e5e5e] whitespace-nowrap">
            Google Maps
          </p>
          {details.attributions?.map((a, i) => (
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
          <p className="text-sm text-gray-500">
            Live information from Google Maps; not saved to the Nirvana database.
          </p>
        </section>
      )}
    </div>
  );
}
