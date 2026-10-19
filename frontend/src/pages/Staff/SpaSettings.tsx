import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { useAuth } from '../../hooks/useAuth';
import { isAdmin } from '../../auth/roles';
import Fields from '../../features/Fields';
import { resolve, clean } from '../../features/model';
import Button from '../../components/common/Button';
export default function SpaSettings() {
  const { user } = useAuth();
  const admin = isAdmin(user?.roles);
  const [id, setId] = useState('');
  const [value, setValue] = useState<Record<string, unknown>>();
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
        ([k]) => k !== 'version' && (admin || !['commissionPct', 'featured'].includes(k))
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
  return (
    <div className="max-w-5xl mx-auto p-8 space-y-5">
      <h1 className="text-3xl font-bold">Full Spa Settings</h1>
      <p>
        Load a spa before editing. Times use HH:mm:ss in the spa’s timezone. Service and therapist
        identifiers must belong to this spa or be unassigned.
      </p>
      <label className="block">
        Spa
        <select
          className="block border rounded-xl p-3 w-full"
          disabled={action.isPending}
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
