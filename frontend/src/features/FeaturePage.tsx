import { useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import client, { errorMessage } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import { canOpen, isAdmin, isStaff } from '../auth/roles';
import { features, Operation, resolve, Schema, buildRequest } from './model';
import Fields, { Choice } from './Fields';
import Results from './Results';
import { payForPackage } from '../api/packageApi';
import Button from '../components/common/Button';
type Values = Record<string, unknown>;
export default function FeaturePage() {
  const { feature } = useParams();
  const { user } = useAuth();
  const item = features.find((f) => f.id === feature);
  if (!item) return <Navigate to="/404" replace />;
  if (!canOpen(item.access, user?.roles))
    return (
      <div className="max-w-4xl mx-auto p-8">
        <h1 className="text-2xl font-bold">Access restricted</h1>
        <p>This area is available only to authorized staff.</p>
      </div>
    );
  return <Workspace key={item.id} id={item.id} />;
}
function Workspace({ id }: { id: string }) {
  const item = features.find((f) => f.id === id)!;
  const { user } = useAuth();
  const [active, setActive] = useState(0);
  const [values, setValues] = useState<Values>(() =>
    Object.fromEntries(new URLSearchParams(window.location.search))
  );
  const [extraChoices, setExtraChoices] = useState<Record<string, Choice[]>>({});
  const spas = useQuery({
    queryKey: ['workspace-spas', user?.id],
    queryFn: async () => {
      if (isStaff(user?.roles) && !isAdmin(user?.roles))
        return (await client.get('/api/onboarding/spas')).data;
      return (await client.get('/api/spas/search', { params: { page: 0, size: 50 } })).data
        .content;
    }
  });
  const bookings = useQuery({
    queryKey: ['workspace-bookings', user?.id],
    enabled: item.access === 'customer',
    queryFn: async () =>
      (await client.get('/api/me/bookings', { params: { size: 50 } })).data.page.content
  });
  const services = useQuery({
    queryKey: ['workspace-services', values.spaId],
    enabled: !!values.spaId,
    queryFn: async () => (await client.get(`/api/spas/${values.spaId}/services`)).data
  });
  const mapChoices = (data: unknown, idKey: string, nameKey: string): Choice[] =>
    Array.isArray(data)
      ? data.map((v: Values) => ({
          value: String(v[idKey]),
          label: String(v[nameKey] || v[idKey])
        }))
      : [];
  const choices = {
    ...extraChoices,
    spaId: mapChoices(spas.data, 'spaId', 'name').map((v, i) =>
      v.value === 'undefined' ? mapChoices(spas.data, 'id', 'name')[i] : v
    ),
    bookingId: mapChoices(bookings.data, 'bookingId', 'bookingReference'),
    serviceId: mapChoices(services.data, 'id', 'name')
  };
  const remember = (data: unknown, op: Operation) => {
    const key =
      op.path === '/api/notifications/me'
        ? 'notificationId'
        : op.path === '/api/messages/threads'
          ? 'threadId'
          : op.path === '/api/v1/corporates'
            ? 'corporateId'
            : op.path.endsWith('/deals')
              ? 'dealId'
              : op.path.endsWith('/packages/available')
                ? 'spaPackageId'
                : op.path.endsWith('/slots/week')
                  ? 'slotId'
                  : op.path === '/api/v1/user/packages'
                    ? 'subscriptionId'
                    : op.path === '/api/v1/user/corporate/benefits'
                      ? 'couponId'
                      : undefined;
    if (!key) return;
    const records: Values[] = [];
    const walk = (x: unknown) => {
      if (Array.isArray(x)) x.forEach(walk);
      else if (x && typeof x === 'object') {
        const o = x as Values;
        if (o.id || o[key]) records.push(o);
        else Object.values(o).forEach(walk);
      }
    };
    walk(data);
    setExtraChoices((old) => ({
      ...old,
      [key]: records.map((x) => ({
        value: String(x[key] || x.id),
        label: String(x.subject || x.title || x.name || x.level || x.startTs || x[key] || x.id)
      }))
    }));
  };
  return (
    <div className="mx-auto max-w-6xl p-4 md:p-8 space-y-6">
      <header>
        <p className="text-primary-700 font-medium">
          {item.access === 'admin'
            ? 'Administration'
            : item.access === 'staff'
              ? 'Spa workspace'
              : 'Your Nirvana'}
        </p>
        <h1 className="text-3xl font-bold">{item.title}</h1>
        <p className="mt-2 text-slate-600">{item.description}</p>
      </header>
      <nav aria-label={`${item.title} sections`} className="flex flex-wrap gap-2">
        {item.actions.map((op, i) => (
          <button
            key={op.title}
            onClick={() => setActive(i)}
            className={`rounded-full px-4 py-2 text-sm border ${active === i ? 'bg-primary-700 text-white' : 'bg-white text-slate-700'}`}
          >
            {op.title}
          </button>
        ))}
      </nav>
      <Action
        key={item.actions[active].title}
        op={item.actions[active]}
        values={values}
        setValues={setValues}
        choices={choices}
        remember={remember}
      />
    </div>
  );
}
function Action({
  op,
  values,
  setValues,
  choices,
  remember
}: {
  op: Operation;
  values: Values;
  setValues: (v: Values) => void;
  choices: Record<string, Choice[]>;
  remember: (v: unknown, o: Operation) => void;
}) {
  const [file, setFile] = useState<File>();
  const [approved, setApproved] = useState(false);
  const [page, setPage] = useState(0);
  const schema = resolve(op.body || { type: 'object', properties: {} });
  const fields: Schema = {
    type: 'object',
    properties: {
      ...Object.fromEntries(
        op.parameters
          .filter((p) => !['page', 'size'].includes(p.name))
          .map((p) => [p.name, p.schema])
      ),
      ...schema.properties
    },
    required: [
      ...op.parameters.filter((p) => p.required).map((p) => p.name),
      ...(schema.required || [])
    ]
  };
  const [local, setLocal] = useState<Values>(() => ({
    ...Object.fromEntries(
      op.parameters
        .filter((p) => p.schema.default !== undefined)
        .map((p) => [p.name, p.schema.default])
    ),
    ...values
  }));
  const action = useMutation({
    mutationFn: async (v: Values) => {
      const request = buildRequest(op, v);
      if (op.multipart) {
        const data = new FormData();
        if (!file) throw new Error('Choose an employee file.');
        data.append('file', file);
        request.data = data;
      }
      const result = await client.request(request);
      if (op.path.startsWith('/api/v1/') && op.path.endsWith('/checkout'))
        return payForPackage(result.data, true);
      return result.data || { message: 'Saved successfully.' };
    },
    onSuccess: (data) => {
      setValues(local);
      remember(data, op);
      setApproved(false);
    }
  });
  useEffect(() => {
    if (op.method === 'GET' && !op.parameters.some((p) => p.required))
      action.mutate({ ...local, page: 0, size: 20 });
  }, []);
  const update = (v: unknown) => {
    const next = v as Values;
    setLocal(next);
    setValues(next);
  };
  const needsConfirm =
    op.method !== 'GET' &&
    /erase|approve|reject|confirm|redeem|buy|send|moderate|vouchers|resolve/i.test(
      op.path + ' ' + op.title
    );
  const useLocation = () =>
    navigator.geolocation.getCurrentPosition(
      (p) =>
        update({
          ...local,
          lat: p.coords.latitude,
          lon: p.coords.longitude,
          originLat: p.coords.latitude,
          originLon: p.coords.longitude
        }),
      () => action.reset()
    );
  const result = action.data as Values | undefined;
  const paging = (result?.page && typeof result.page === 'object' ? result.page : result) as
    Values | undefined;
  const totalPages = typeof paging?.totalPages === 'number' ? paging.totalPages : undefined;
  return (
    <section className="rounded-2xl bg-white border border-slate-100 p-5 md:p-7 space-y-5">
      <h2 className="text-xl font-semibold">{op.title}</h2>
      <form
        className="space-y-5"
        onSubmit={(e) => {
          e.preventDefault();
          if (needsConfirm && !approved) return;
          setPage(0);
          action.mutate({ ...local, page: 0, size: 20 });
        }}
      >
        <Fields schema={fields} value={local} onChange={update} choices={choices} />
        {Object.keys(fields.properties || {}).some((k) => k === 'lat' || k === 'originLat') && (
          <button type="button" onClick={useLocation} className="text-primary-700 underline">
            Use my current location
          </button>
        )}
        {op.multipart && (
          <label className="block">
            Employee spreadsheet
            <input
              className="block mt-2"
              type="file"
              accept=".xlsx,.xls,.csv"
              required
              onChange={(e) => setFile(e.target.files?.[0])}
            />
          </label>
        )}
        {needsConfirm && (
          <label className="flex gap-3">
            <input
              type="checkbox"
              required
              checked={approved}
              onChange={(e) => setApproved(e.target.checked)}
            />
            <span>
              I have reviewed these details and want to {op.title.toLowerCase()}.
              {op.path.endsWith('/erase') ? ' This may permanently remove my personal data.' : ''}
            </span>
          </label>
        )}
        <Button type="submit" disabled={action.isPending}>
          {action.isPending ? 'Please wait…' : op.title}
        </Button>
      </form>
      {action.isError && (
        <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-800">
          {errorMessage(action.error)}
        </p>
      )}
      {action.isSuccess && (
        <div aria-live="polite" className="border-t pt-5">
          <Results data={action.data} />
        </div>
      )}
      {op.parameters.some((p) => p.name === 'page') && action.isSuccess && (
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            disabled={page === 0 || action.isPending}
            onClick={() => {
              setPage(page - 1);
              action.mutate({ ...local, page: page - 1, size: 20 });
            }}
          >
            Previous
          </Button>
          <span>Page {page + 1}</span>
          <Button
            variant="ghost"
            disabled={action.isPending || (totalPages !== undefined && page + 1 >= totalPages)}
            onClick={() => {
              setPage(page + 1);
              action.mutate({ ...local, page: page + 1, size: 20 });
            }}
          >
            Next
          </Button>
        </div>
      )}
    </section>
  );
}
