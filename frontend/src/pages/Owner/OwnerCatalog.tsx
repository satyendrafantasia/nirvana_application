import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Fields from '../../features/Fields';
import { Schema } from '../../features/model';
import Button from '../../components/common/Button';
type RecordValue = Record<string, unknown>;
const serviceSchema: Schema = {
  type: 'object',
  required: ['name', 'durationMin', 'bufferMin', 'basePriceCents', 'currency'],
  properties: {
    name: { type: 'string', maxLength: 255 },
    description: { type: 'string', maxLength: 2000 },
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
    type: { type: 'string', maxLength: 128 },
    bio: { type: 'string', maxLength: 2000 },
    active: { type: 'boolean' },
    available: { type: 'boolean' }
  }
};
const defaults = (kind: string): RecordValue =>
  kind === 'therapists'
    ? { name: '', type: '', bio: '', active: true, available: true, serviceIds: [] }
    : kind === 'services'
      ? {
          name: '',
          description: '',
          durationMin: 60,
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
  const services = useQuery({
    queryKey: ['owner-catalog', spaId, 'services'],
    enabled: kind === 'therapists',
    queryFn: async () => (await client.get<RecordValue[]>(`/api/owner/spas/${spaId}/services`)).data
  });
  const action = useMutation({
    mutationFn: async () => {
      const body = { ...value };
      delete body.id;
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
        {records.isPending && <p>Loading…</p>}
        {records.isError && <p role="alert">{errorMessage(records.error)}</p>}
        {records.data?.length === 0 && <p>No {kind} yet. Add your first one.</p>}
        {records.data?.map((row) => (
          <article className="border rounded-xl p-4 space-y-2" key={String(row.id)}>
            <h3 className="font-semibold">{String(row.name || row.level)}</h3>
            <p>
              {kind === 'packages'
                ? `${row.price} · ${row.freeSessionsCount} sessions · ${row.status}`
                : kind === 'services'
                  ? `${row.durationMin} minutes · ${Number(row.basePriceCents) / 100} ${row.currency} · ${row.active ? 'Active' : 'Inactive'}`
                  : `${row.type || 'Therapist'} · ${row.active ? 'Active' : 'Inactive'} · ${row.available ? 'Available' : 'Unavailable'}`}
            </p>
            <Button
              variant="secondary"
              disabled={action.isPending}
              onClick={() => {
                setEditing(String(row.id));
                setValue({ ...row });
                action.reset();
              }}
            >
              Edit {String(row.name || row.level)}
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
        <Fields schema={schema} value={value} onChange={(v) => setValue(v as RecordValue)} />
        {kind === 'services' && (
          <p className="text-sm">
            Base price is in minor currency units (for example, 10000 = INR 100). Inactive services
            cannot accept new bookings.
          </p>
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
