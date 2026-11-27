import { showToast } from '../../components/common/Toast';
import { Link } from 'react-router-dom';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Fields from '../../features/Fields';
import { Schema } from '../../features/model';
import Button from '../../components/common/Button';
import HealthQuestions, { HealthTerms } from './HealthQuestions';
type RecordValue = Record<string, unknown>;
const serviceSchema: Schema = {
  type: 'object',
  required: ['name', 'durationMin', 'bufferMin', 'basePriceCents', 'currency'],
  properties: {
    name: { type: 'string', maxLength: 255 },
    description: { type: 'string', maxLength: 2000 },
    category: { type: 'string', maxLength: 100 },
    visibleOnMarketplace: { type: 'boolean' },
    durationMin: { type: 'integer', minimum: 1, maximum: 1440 },
    bufferMin: { type: 'integer', minimum: 0, maximum: 1440 },
    basePriceCents: { type: 'integer', minimum: 0 },
    currency: { type: 'string', minLength: 3, maxLength: 3 },
    active: { type: 'boolean' }
  }
};
const therapistSchema: Schema = {
  type: 'object',
  required: ['name'],
  properties: {
    name: { type: 'string', maxLength: 255 },
    publicName: { type: 'string', maxLength: 128 },
    type: { type: 'string', maxLength: 128 },
    bio: { type: 'string', maxLength: 2000 },
    active: { type: 'boolean' },
    available: { type: 'boolean' }
  }
};
const defaults = (kind: string): RecordValue =>
  kind === 'therapists'
    ? { name: '', publicName: '', type: '', bio: '', active: true, available: true, serviceIds: [] }
    : kind === 'services'
      ? {
          name: '',
          description: '',
          durationMin: 60,
          visibleOnMarketplace: true,
          bufferMin: 0,
          basePriceCents: 0,
          currency: 'INR',
          active: true
        }
      : { level: 'SILVER', price: 1, freeSessionsCount: 1, status: 'INACTIVE' };
export default function OwnerCatalog({
  spaId,
  kind
}: {
  spaId: string;
  kind: 'therapists' | 'services' | 'packages';
}) {
  const qc = useQueryClient();
  const [value, setValue] = useState<RecordValue>(defaults(kind));
  const [editing, setEditing] = useState<string>();
  const path =
    kind === 'packages' ? `/api/v1/spa/${spaId}/packages` : `/api/owner/spas/${spaId}/${kind}`;
  const key = ['owner-catalog', spaId, kind];
  const records = useQuery({
    queryKey: key,
    queryFn: async () => (await client.get<RecordValue[]>(path)).data
  });
  const sales = useQuery({
    queryKey: ['desk', spaId, 'package-sales'],
    enabled: kind === 'packages',
    queryFn: async () =>
      (
        await client.get<{ id: number; currency: string; sales: number }[]>(
          '/api/owner/spas/' + spaId + '/desk/package-sales'
        )
      ).data
  });
  const stats = useQuery({
    queryKey: ['desk', spaId, 'packages'],
    enabled: kind === 'packages',
    queryFn: async () =>
      (
        await client.get<
          {
            id: number;
            used: number;
            sold: number;
            buyers: number;
            active: number;
            purchased: number;
            remaining: number;
            expiring: number;
          }[]
        >('/api/owner/spas/' + spaId + '/desk/packages')
      ).data
  });
  const services = useQuery({
    queryKey: ['owner-catalog', spaId, 'services'],
    enabled: kind === 'therapists' || kind === 'packages',
    queryFn: async () => (await client.get<RecordValue[]>(`/api/owner/spas/${spaId}/services`)).data
  });
  const visibilityPath = `/api/owner/spas/${spaId}/therapist-visibility`;
  const visibility = useQuery({
    queryKey: [visibilityPath],
    enabled: kind === 'therapists',
    queryFn: async () =>
      (await client.get<{ names: boolean; labels: boolean }>(visibilityPath)).data
  });
  const refreshAvailability = () => {
    for (const queryKey of [['therapist-directory'], ['slots'], ['reschedule'], ['my-bookings']])
      void qc.invalidateQueries({ queryKey });
  };
  const publish = useMutation({
    mutationFn: (mode: string) =>
      client.put(visibilityPath, { names: mode.includes('n'), labels: mode.includes('l') }),
    onSuccess: () => {
      showToast('Therapist visibility updated.', 'success');
      void qc.invalidateQueries({ queryKey: [visibilityPath] });
      refreshAvailability();
    }
  });
  const retire = useMutation({
    mutationFn: (id: string) =>
      client.delete(
        kind === 'packages' ? '/api/owner/spas/' + spaId + '/desk/packages/' + id : path + '/' + id
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: key })
  });
  const action = useMutation({
    mutationFn: async () => {
      const body = { ...value };
      delete body.id;
      if (kind === 'packages') {
        const sameLevel = records.data?.find((r) => r.level === body.level);
        if (sameLevel && String(sameLevel.id) !== editing)
          throw new Error('This tier already exists. Open its Edit action.');
        if (editing && !sameLevel)
          throw new Error('A package tier is fixed. Create a new tier instead.');
        if (body.archived) body.status = 'INACTIVE';
      }
      return (
        await client.request({
          method: editing && kind !== 'packages' ? 'PUT' : 'POST',
          url: editing && kind !== 'packages' ? `${path}/${editing}` : path,
          data: body
        })
      ).data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: key });
      refreshAvailability();
      showToast(kind === 'therapists' ? 'Therapist saved.' : kind === 'packages' ? 'Package saved.' : 'Service saved.', 'success');
      setValue(defaults(kind));
      setEditing(undefined);
    }
  });
  const schema =
    kind === 'therapists'
      ? therapistSchema
      : kind === 'services'
        ? serviceSchema
        : { $ref: '#/components/schemas/CreateOrUpdateSpaPackageRequest' };
  return (
    <section className="grid lg:grid-cols-2 gap-6">
      <div className="card p-6 space-y-4">
        <h2 className="text-2xl font-bold capitalize">{kind}</h2>
        {kind === 'packages' && (
          <p>
            Each level has one offer per spa. Editing that level updates its price, sessions and
            sale status.
          </p>
        )}
        {kind === 'services' && <HealthTerms spaId={spaId} />}
        {kind === 'therapists' && visibility.data && (
          <label className="block">
            Customer therapist choices
            <select
              className="block border rounded-xl p-2"
              disabled={publish.isPending}
              value={(visibility.data.names ? 'n' : '') + (visibility.data.labels ? 'l' : '')}
              onChange={(e) => publish.mutate(e.target.value)}
            >
              <option value="">Any available therapist only</option>
              <option value="n">Names and Any</option>
              <option value="l">Profile labels and Any</option>
              <option value="nl">Names, profile labels and Any</option>
            </select>
          </label>
        )}
        {(publish.isError || visibility.isError) && (
          <p role="alert">{errorMessage(publish.error || visibility.error)}</p>
        )}
        {stats.isError && <p role="alert">{errorMessage(stats.error)}</p>}
        {retire.isError && <p role="alert">{errorMessage(retire.error)}</p>}
        {records.isPending && <p>Loading…</p>}
        {records.isError && <p role="alert">{errorMessage(records.error)}</p>}
        {records.data?.length === 0 && <p>No {kind} yet. Add your first one.</p>}
        {records.data?.map((row) => (
          <article className="border rounded-xl p-4 space-y-2" key={String(row.id)}>
            <h3 className="font-semibold">{String(row.name || row.title || row.level)}</h3>
            <p>
              {kind === 'packages'
                ? `${row.price} · ${row.freeSessionsCount} sessions · ${row.archived ? 'Archived' : row.status}`
                : kind === 'services'
                  ? `${row.durationMin} minutes · ${Number(row.basePriceCents) / 100} ${row.currency} · ${row.active ? 'Active' : 'Inactive'}`
                  : `${row.type || 'Therapist'} · ${row.active ? 'Active' : 'Inactive'} · ${row.available ? 'Available' : 'Unavailable'}`}
            </p>
            {kind === 'packages' &&
              stats.data
                ?.filter((v) => v.id === row.id)
                .map((v) => (
                  <div key={v.id}>
                    <p>
                      {v.sold} sold · {v.buyers} buyers · {v.active} active subscriptions
                    </p>
                    <p>
                      {v.used} redemptions · {v.purchased} sessions purchased · {v.remaining}{' '}
                      remaining · {v.expiring} expiring within 30 days
                    </p>
                  </div>
                ))}
            {kind === 'packages' && (
              <div className="text-sm">
                {sales.isError && <p role="alert">{errorMessage(sales.error)}</p>}
                {sales.data
                  ?.filter((v) => v.id === row.id)
                  .map((v) => (
                    <p key={v.currency}>
                      Lifetime sales ·{' '}
                      {new Intl.NumberFormat('en-IN', {
                        style: 'currency',
                        currency: v.currency
                      }).format(v.sales)}
                    </p>
                  ))}
                <Link
                  className="underline"
                  to={
                    '/owner/customers?spa=' + spaId + '&view=BUYERS&period=YEAR&packageId=' + row.id
                  }
                >
                  View package buyers
                </Link>
              </div>
            )}
            {kind === 'packages' && row.status === 'INACTIVE' && (
              <Button
                variant="ghost"
                disabled={retire.isPending}
                onClick={() => {
                  if (
                    window.confirm(
                      'Delete this inactive package? A package with purchase history is protected.'
                    )
                  )
                    retire.mutate(String(row.id));
                }}
              >
                Delete unused package
              </Button>
            )}
            {kind === 'services' && <HealthQuestions spaId={spaId} serviceId={String(row.id)} />}
            {kind === 'therapists' && Boolean(row.active) && (
              <Button
                variant="ghost"
                onClick={() => {
                  if (window.confirm('Remove this therapist from active scheduling?'))
                    retire.mutate(String(row.id));
                }}
              >
                Remove therapist
              </Button>
            )}
            <Button
              variant="secondary"
              disabled={action.isPending}
              onClick={() => {
                setEditing(String(row.id));
                setValue({ ...row });
                action.reset();
              }}
            >
              Edit {String(row.name || row.title || row.level)}
            </Button>
          </article>
        ))}
      </div>
      <form
        className="card p-6 space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          action.mutate();
        }}
      >
        <h2 className="text-2xl font-bold">
          {editing ? 'Edit' : 'Add'}{' '}
          {kind === 'therapists' ? 'therapist' : kind === 'services' ? 'service' : 'package'}
        </h2>
        {kind === 'therapists' && (
          <p className="text-sm">
            Name identifies staff in your schedule. Enter a public name to offer individual
            selection. Type is the optional customer-facing profile label.
          </p>
        )}
        <Fields schema={schema} value={value} onChange={(v) => setValue(v as RecordValue)} />
        {kind === 'services' && (
          <p className="text-sm">
            Base price is in minor currency units (for example, 10000 = INR 100). Inactive services
            cannot accept new bookings.
          </p>
        )}
        {kind === 'packages' && (
          <fieldset className="space-y-3">
            <legend className="font-semibold">Offer terms</legend>
            {['title', 'description'].map((k) => (
              <label className="grid capitalize" key={k}>
                {k}
                <input
                  className="border rounded-xl p-2"
                  maxLength={k === 'title' ? 100 : 1500}
                  value={String(value[k] || '')}
                  onChange={(e) => setValue({ ...value, [k]: e.target.value })}
                />
              </label>
            ))}
            <label className="grid">
              Validity in days (blank for no expiry)
              <input
                className="border rounded-xl p-2"
                type="number"
                min="1"
                max="3650"
                value={String(value.validityDays || '')}
                onChange={(e) =>
                  setValue({
                    ...value,
                    validityDays: e.target.value ? Number(e.target.value) : null
                  })
                }
              />
            </label>
            <label className="block">
              <input
                type="checkbox"
                checked={Boolean(value.archived)}
                onChange={(e) => setValue({ ...value, archived: e.target.checked })}
              />{' '}
              Archive offer and stop new sales
            </label>
            <p className="font-semibold">Included services</p>
            <p className="text-sm">
              Leave all unchecked to include every service at this spa. Changes apply to future
              purchases.
            </p>
            {services.isError && <p role="alert">{errorMessage(services.error)}</p>}
            {services.data?.map((v) => (
              <label className="block" key={String(v.id)}>
                <input
                  type="checkbox"
                  checked={((value.serviceIds || []) as number[]).includes(Number(v.id))}
                  onChange={(e) => {
                    const ids = (value.serviceIds || []) as number[];
                    setValue({
                      ...value,
                      serviceIds: e.target.checked
                        ? [...ids, Number(v.id)]
                        : ids.filter((i) => i !== v.id)
                    });
                  }}
                />{' '}
                {String(v.name)}
              </label>
            ))}
          </fieldset>
        )}
        {kind === 'therapists' && (
          <fieldset className="space-y-2">
            <legend className="font-medium">Services this therapist can deliver</legend>
            {services.isError && <p role="alert">{errorMessage(services.error)}</p>}
            {services.data?.length === 0 && <p>Create services in the Services tab first.</p>}
            {services.data?.map((s) => (
              <label className="block" key={String(s.id)}>
                <input
                  type="checkbox"
                  checked={((value.serviceIds || []) as number[]).includes(Number(s.id))}
                  onChange={(e) =>
                    setValue((v) => ({
                      ...v,
                      serviceIds: e.target.checked
                        ? [...((v.serviceIds || []) as number[]), Number(s.id)]
                        : ((v.serviceIds || []) as number[]).filter((id) => id !== Number(s.id))
                    }))
                  }
                />{' '}
                {String(s.name)}
                {s.active ? '' : ' (inactive)'}
              </label>
            ))}
            <p className="text-sm">
              Turning off Active or Available prevents new therapist selection. Existing bookings
              are retained.
            </p>
          </fieldset>
        )}
        <div className="flex gap-3">
          <Button type="submit" disabled={action.isPending}>
            {action.isPending ? 'Saving…' : 'Save'}
          </Button>
          <Button
            variant="secondary"
            type="button"
            disabled={action.isPending}
            onClick={() => {
              setEditing(undefined);
              setValue(defaults(kind));
              action.reset();
            }}
          >
            New {kind === 'therapists' ? 'therapist' : kind === 'services' ? 'service' : 'package'}
          </Button>
        </div>
        {action.isError && <p role="alert">{errorMessage(action.error)}</p>}
        {action.isSuccess && (
          <p role="status" className="text-green-700">
            Saved successfully.
          </p>
        )}
      </form>
    </section>
  );
}
