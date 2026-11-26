import { useEffect, useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { useAuth } from '../../hooks/useAuth';
import { isAdmin } from '../../auth/roles';
import Fields from '../../features/Fields';
import { resolve, clean } from '../../features/model';
import { embedUrl } from '../../components/spa/SpaProfile';
import Button from '../../components/common/Button';
export default function SpaSettings({ initialSpaId = '' }: { initialSpaId?: string }) {
  const { user } = useAuth();
  const admin = isAdmin(user?.roles);
  const [id, setId] = useState(initialSpaId);
  const [value, setValue] = useState<Record<string, unknown>>();
  const [pinMessage, setPinMessage] = useState('');
  const [deactivate, setDeactivate] = useState(false);
  const spas = useQuery({
    queryKey: ['settings-spas', user?.id],
    queryFn: async () =>
      admin
        ? (await client.get('/api/spas/search', { params: { size: 50 } })).data.content
        : (await client.get('/api/onboarding/spas')).data
  });
  const schema = resolve({ $ref: '#/components/schemas/SpaRequest' });
  const visible = {
    ...schema,
    properties: Object.fromEntries(
      Object.entries(schema.properties || {}).filter(
        ([k]) => k !== 'version' && (admin || !['commissionPct', 'featured', 'active'].includes(k))
      )
    )
  };
  const action = useMutation({
    mutationFn: async (mode: 'load' | 'save' | 'deactivate') => {
      if (!id) throw new Error('Select a spa.');
      if (mode === 'save') await client.put(`/api/spas/${id}`, clean(value, schema));
      if (mode === 'deactivate') await client.delete(`/api/spas/${id}`);
      const { data } = await client.get(`/api/spas/${id}/settings`);
      setValue(data);
      setDeactivate(false);
      return mode;
    }
  });
  useEffect(() => {
    if (initialSpaId) action.mutate('load');
  }, [initialSpaId]);
  const address = (value?.address || {}) as Record<string, unknown>;
  const pin =
    address.latitude != null && address.longitude != null
      ? String(address.latitude) + ',' + String(address.longitude)
      : '';
  const map = pin
    ? embedUrl(pin, undefined, import.meta.env.VITE_GOOGLE_MAPS_EMBED_KEY)
    : undefined;
  return (
    <div className="max-w-5xl mx-auto p-8 space-y-5">
      <h1 className="text-3xl font-bold">Full Spa Settings</h1>
      <p>
        Verify the contact details and map pin before saving. Times use HH:mm:ss in the spa’s
        timezone. Service and therapist identifiers must belong to this spa or be unassigned.
      </p>
      <label className="block">
        Spa
        <select
          className="block border rounded-xl p-3 w-full"
          disabled={action.isPending || !!initialSpaId}
          value={id}
          onChange={(e) => {
            setId(e.target.value);
            setValue(undefined);
            action.reset();
          }}
        >
          <option value="">Choose spa</option>
          {spas.data?.map((s: { id: number; name: string }) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>
      {admin && (
        <label className="block">
          Spa ID (including inactive or unlisted spas)
          <input
            className="block border rounded-xl p-3 w-full"
            type="number"
            min="1"
            disabled={action.isPending}
            value={id}
            onChange={(e) => {
              setId(e.target.value);
              setValue(undefined);
              action.reset();
            }}
          />
        </label>
      )}
      <Button disabled={!id || action.isPending} onClick={() => action.mutate('load')}>
        Load settings
      </Button>
      {value && (
        <>
          <form
            className="space-y-5 bg-white p-6 rounded-xl"
            onSubmit={(e) => {
              e.preventDefault();
              action.mutate('save');
            }}
          >
            <Fields
              schema={visible}
              value={value}
              onChange={(v) => setValue(v as Record<string, unknown>)}
            />
            <section className="space-y-2">
              <h2 className="text-lg font-semibold">Map pin</h2>
              <p>{pin || 'Enter latitude and longitude in the address fields.'}</p>
              {pin && (
                <a
                  className="underline"
                  target="_blank"
                  rel="noreferrer"
                  href={
                    'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(pin)
                  }
                >
                  Check pin on Google Maps
                </a>
              )}
              {map && (
                <iframe
                  title="Spa map pin"
                  className="w-full h-48 rounded-xl"
                  loading="lazy"
                  src={map}
                />
              )}
              <button
                type="button"
                className="block underline"
                onClick={() => {
                  if (!navigator.geolocation) {
                    setPinMessage('Enter coordinates in the address fields on this device.');
                    return;
                  }
                  setPinMessage('Locating…');
                  navigator.geolocation.getCurrentPosition(
                    (pos) => {
                      setValue((v) => ({
                        ...v,
                        address: {
                          ...(v?.address as object),
                          latitude: Number(pos.coords.latitude.toFixed(6)),
                          longitude: Number(pos.coords.longitude.toFixed(6))
                        }
                      }));
                      setPinMessage('Pin updated. Check the pin and save when ready.');
                    },
                    () =>
                      setPinMessage(
                        'Location unavailable. Enter coordinates in the address fields.'
                      )
                  );
                }}
              >
                Use my current location for the pin
              </button>
              <p role="status" className="text-sm">
                {pinMessage}
              </p>
            </section>
            <Button disabled={action.isPending}>Save settings</Button>
          </form>
          <section className="border rounded-xl p-5 space-y-3">
            <label className="block">
              <input
                type="checkbox"
                checked={deactivate}
                onChange={(e) => setDeactivate(e.target.checked)}
              />{' '}
              I want to remove this spa from discovery and new bookings.
            </label>
            <Button
              disabled={!deactivate || action.isPending}
              onClick={() => action.mutate('deactivate')}
            >
              Deactivate spa
            </Button>
          </section>
        </>
      )}
      {action.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(action.error)}
        </p>
      )}
      {spas.isError && <p role="alert">{errorMessage(spas.error)}</p>}
      {action.isSuccess && action.data !== 'load' && <p role="status">Changes saved.</p>}
    </div>
  );
}
