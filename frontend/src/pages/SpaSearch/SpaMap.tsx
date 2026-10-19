import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
type Spa = { id: number; name: string; latitude: number | null; longitude: number | null };
export default function SpaMap() {
  const element = useRef<HTMLDivElement>(null),
    map = useRef<L.Map>(),
    markers = useRef<L.LayerGroup>();
  const [lat, setLat] = useState(''),
    [lon, setLon] = useState(''),
    [radius, setRadius] = useState('25'),
    [page, setPage] = useState(0),
    [notice, setNotice] = useState('');
  const provider = useQuery({
    queryKey: ['map-provider'],
    queryFn: async () => (await client.get('/api/maps/provider')).data as { provider: string }
  });
  useEffect(() => {
    if (!element.current) return;
    const m = L.map(element.current).setView([20, 0], 2);
    map.current = m;
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution:
        '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(m);
    markers.current = L.layerGroup().addTo(m);
    m.on('click', (e) => {
      setLat(e.latlng.lat.toFixed(6));
      setLon(e.latlng.lng.toFixed(6));
    });
    return () => {
      m.remove();
      map.current = undefined;
    };
  }, []);
  const search = useMutation({
    mutationFn: async (nextPage: number) => {
      const a = Number(lat),
        b = Number(lon),
        r = Number(radius);
      if (
        !lat ||
        !lon ||
        !Number.isFinite(a) ||
        !Number.isFinite(b) ||
        Math.abs(a) > 90 ||
        Math.abs(b) > 180 ||
        !(r > 0 && r <= 200)
      )
        throw new Error('Enter valid coordinates and a radius between 1 and 200 km.');
      const { data } = await client.get('/api/spas/nearby', {
        params: { lat: a, lon: b, radiusKm: r, page: nextPage, size: 20 }
      });
      const details = await Promise.allSettled(
        data.content.map((s: { id: number }) =>
          client.get<Spa>(`/api/spas/${s.id}`).then((r) => r.data)
        )
      );
      const spas = details.flatMap((r) => (r.status === 'fulfilled' ? [r.value] : []));
      setNotice(
        details.some((r) => r.status === 'rejected')
          ? 'Some spa details could not be loaded. Retry the search.'
          : ''
      );
      setPage(nextPage);
      const layer = markers.current;
      layer?.clearLayers();
      const bounds: L.LatLngExpression[] = [[a, b]];
      L.circleMarker([a, b], { radius: 7, color: '#1d4ed8' })
        .bindTooltip('Search location')
        .addTo(layer!);
      spas.forEach((s) => {
        if (s.latitude == null || s.longitude == null) return;
        const position: L.LatLngExpression = [s.latitude, s.longitude];
        bounds.push(position);
        const link = document.createElement('a');
        link.href = `/spas/${s.id}`;
        link.textContent = s.name + ' — view treatments';
        L.circleMarker(position, { radius: 10, color: '#0f766e' }).bindPopup(link).addTo(layer!);
      });
      map.current?.fitBounds(L.latLngBounds(bounds), { padding: [40, 40], maxZoom: 13 });
      return { spas, totalPages: data.totalPages, origin: { lat: a, lon: b } };
    }
  });
  const eta = useMutation({
    mutationFn: async (id: number) => {
      const { data } = await client.get(`/api/maps/eta/spa/${id}`, {
        params: {
          originLat: search.data!.origin.lat,
          originLon: search.data!.origin.lon,
          mode: 'driving'
        }
      });
      return {
        id,
        minutes: Math.ceil(data.estimatedDurationSeconds / 60),
        distance: data.distanceKm
      };
    }
  });
  return (
    <div className="max-w-6xl mx-auto p-8 space-y-5">
      <h1 className="text-3xl font-bold">Interactive Spa Map</h1>
      <p>
        Click the map to choose a search location, or enter coordinates. Drag and zoom to explore.
        Travel estimates use {provider.data?.provider || 'the configured routing provider'}.
      </p>
      <form
        className="flex flex-wrap gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          search.mutate(0);
        }}
      >
        <label>
          Latitude
          <input
            className="block border rounded p-2"
            type="number"
            step="any"
            min="-90"
            max="90"
            value={lat}
            onChange={(e) => setLat(e.target.value)}
            required
          />
        </label>
        <label>
          Longitude
          <input
            className="block border rounded p-2"
            type="number"
            step="any"
            min="-180"
            max="180"
            value={lon}
            onChange={(e) => setLon(e.target.value)}
            required
          />
        </label>
        <label>
          Radius (km)
          <input
            className="block border rounded p-2"
            type="number"
            min="1"
            max="200"
            value={radius}
            onChange={(e) => setRadius(e.target.value)}
            required
          />
        </label>
        <Button disabled={search.isPending}>Find nearby spas</Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() =>
            navigator.geolocation.getCurrentPosition(
              (p) => {
                setLat(String(p.coords.latitude));
                setLon(String(p.coords.longitude));
                setNotice('Location selected. Choose Find nearby spas to search.');
              },
              () =>
                setNotice('Location unavailable. Choose a point on the map or enter coordinates.')
            )
          }
        >
          Use my location
        </Button>
      </form>
      <div
        ref={element}
        className="h-[450px] rounded-xl border relative z-0"
        aria-label="Interactive map of nearby spas"
      />
      {notice && <p role="status">{notice}</p>}
      {search.isError && <p role="alert">{errorMessage(search.error)}</p>}
      {search.data?.spas.length === 0 && <p>No spas found in this area.</p>}
      <div className="grid md:grid-cols-2 gap-4">
        {search.data?.spas.map((s) => (
          <article key={s.id} className="rounded-xl bg-white p-4 space-y-3">
            <Link className="font-semibold text-primary-700" to={`/spas/${s.id}`}>
              {s.name}
            </Link>
            <Button variant="secondary" disabled={eta.isPending} onClick={() => eta.mutate(s.id)}>
              Estimate travel time
            </Button>
            {eta.data && eta.data.id === s.id && (
              <p>
                {eta.data.minutes} min · {eta.data.distance.toFixed(1)} km (estimate)
              </p>
            )}
          </article>
        ))}
      </div>
      {eta.isError && <p role="alert">{errorMessage(eta.error)}</p>}
      {search.data && (
        <div className="flex gap-3">
          <Button disabled={page === 0 || search.isPending} onClick={() => search.mutate(page - 1)}>
            Previous
          </Button>
          <span>Page {page + 1}</span>
          <Button
            disabled={page + 1 >= search.data.totalPages || search.isPending}
            onClick={() => search.mutate(page + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
