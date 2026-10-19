import { useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import { isStaff } from '../../auth/roles';
import client, { errorMessage } from '../../api/client';
import { Workspace } from '../../features/FeaturePage';
import Results from '../../features/Results';
import Drafts from '../Staff/Drafts';
import SpaSettings from '../Staff/SpaSettings';
import OwnerCatalog from './OwnerCatalog';
type Spa = {
  id: number;
  name: string;
  active: boolean;
  verified: boolean;
  kycStatus: string;
  kycRejectedReason?: string;
};
const tabs = [
  'Overview',
  'Bookings',
  'Availability',
  'Therapists',
  'Services',
  'Packages',
  'Settings',
  'Onboarding'
];
export default function OwnerPortal() {
  const { user } = useAuth();
  if (!isStaff(user?.roles))
    return (
      <div className="max-w-xl mx-auto p-8 space-y-4">
        <h1 className="text-2xl font-bold">Owner access required</h1>
        <p>
          This account is a customer account. Sign in with your spa owner account or contact the
          platform to request access for an existing account.
        </p>
        <Link to="/owner/login">Owner sign in</Link>
      </div>
    );
  return <Portal />;
}
function Portal() {
  const { user } = useAuth();
  const location = useLocation();
  const section = location.pathname.split('/')[2] || 'overview';
  const [selected, setSelected] = useState('');
  const spas = useQuery({
    queryKey: ['owner-spas', user?.id],
    queryFn: async () => (await client.get<Spa[]>('/api/onboarding/spas')).data
  });
  const spa = spas.data?.find((s) => String(s.id) === selected) || spas.data?.[0];
  const id = spa ? String(spa.id) : '';
  return (
    <div className="max-w-7xl mx-auto p-4 md:p-8 space-y-6">
      <header className="rounded-2xl bg-primary-900 text-white p-6">
        <p>NIRVANA PARTNERS</p>
        <h1 className="text-3xl font-bold mt-2">Your spa workspace</h1>
        <p className="mt-2">Appointments, your team and the details that keep your spa running.</p>
      </header>
      <nav aria-label="Owner workspace" className="flex flex-wrap gap-2">
        {tabs.map((t) => (
          <NavLink
            key={t}
            to={t === 'Overview' ? '/owner' : `/owner/${t.toLowerCase()}`}
            end
            className={({ isActive }) =>
              `px-4 py-2 rounded-xl border ${isActive ? 'bg-primary-700 text-white' : 'bg-white'}`
            }
          >
            {t}
          </NavLink>
        ))}
      </nav>
      {spas.isPending && <p>Loading your spas…</p>}
      {spas.isError && <p role="alert">{errorMessage(spas.error)}</p>}
      {!!spas.data?.length && (
        <label className="block font-medium">
          Your spa
          <select
            className="block border rounded-xl p-3 mt-2 w-full"
            value={id}
            onChange={(e) => setSelected(e.target.value)}
          >
            {spas.data.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {section === 'onboarding' ? (
        <Drafts owner />
      ) : spa ? (
        <>
          <div className="rounded-xl border bg-white p-4">
            <strong>{spa.name}</strong>
            <p>
              {spa.active ? 'Published' : 'Not published'} · Verification:{' '}
              {spa.kycStatus || 'Pending'}
              {spa.kycRejectedReason ? ` — ${spa.kycRejectedReason}` : ''}
            </p>
            {!spa.active && (
              <p>
                Complete onboarding, services and opening hours. Platform approval is required
                before this spa accepts new bookings.
              </p>
            )}
          </div>
          {section === 'overview' && <Overview key={id} id={id} />}
          {section === 'bookings' && <Bookings key={id} id={id} />}
          {section === 'availability' && (
            <Workspace key={id} id="calendar" spaId={id} section="availability" />
          )}
          {['therapists', 'services', 'packages'].includes(section) && (
            <OwnerCatalog
              key={`${id}-${section}`}
              spaId={id}
              kind={section as 'therapists' | 'services' | 'packages'}
            />
          )}
          {section === 'settings' && <SpaSettings key={id} initialSpaId={id} />}
        </>
      ) : (
        !spas.isPending &&
        !spas.isError && (
          <div className="card p-8 space-y-4">
            <h2 className="text-2xl font-bold">Welcome. Let’s create your first spa.</h2>
            <p>
              Add your business details, location and verification documents. Save your progress at
              any time.
            </p>
            <Link
              className="inline-block rounded-xl bg-primary-700 text-white px-5 py-3"
              to="/owner/onboarding"
            >
              Create your spa
            </Link>
          </div>
        )
      )}
    </div>
  );
}
function Overview({ id }: { id: string }) {
  const data = useQuery({
    queryKey: ['owner-overview', id],
    queryFn: async () => (await client.get(`/api/admin/spas/${id}/bookings/analytics`)).data
  });
  return (
    <section className="space-y-4">
      <h2 className="text-2xl font-bold">Booking overview</h2>
      {data.isPending ? (
        <p>Loading summary…</p>
      ) : data.isError ? (
        <p role="alert">{errorMessage(data.error)}</p>
      ) : (
        <div className="grid md:grid-cols-3 gap-4">
          {[
            ['Total bookings', 'totalBookings'],
            ['Served', 'totalServed'],
            ['Cancelled', 'totalCancelled']
          ].map(([label, key]) => (
            <div className="card p-6" key={key}>
              <p>{label}</p>
              <strong className="text-3xl">{data.data[key]}</strong>
            </div>
          ))}
        </div>
      )}
      <p>
        Use Bookings to see appointments, Availability to set daily capacity and blackouts, and
        Therapists to manage your team.
      </p>
      <Link to="/owner/onboarding" className="text-primary-700 underline">
        Add another spa or continue onboarding
      </Link>
    </section>
  );
}
function Bookings({ id }: { id: string }) {
  const [window, setWindow] = useState('TODAY');
  const [page, setPage] = useState(0);
  const data = useQuery({
    queryKey: ['owner-bookings', id, window, page],
    queryFn: async () =>
      (await client.get(`/api/admin/spas/${id}/bookings`, { params: { window, page, size: 20 } }))
        .data.page
  });
  return (
    <section className="card p-6 space-y-4">
      <h2 className="text-2xl font-bold">Appointments</h2>
      <label>
        Period
        <select
          className="block border rounded-xl p-3"
          value={window}
          onChange={(e) => {
            setWindow(e.target.value);
            setPage(0);
          }}
        >
          {['TODAY', 'LAST_7_DAYS', 'LAST_30_DAYS', 'ALL'].map((w) => (
            <option key={w} value={w}>
              {w.replaceAll('_', ' ')}
            </option>
          ))}
        </select>
      </label>
      {data.isPending ? (
        <p>Loading appointments…</p>
      ) : data.isError ? (
        <p role="alert">{errorMessage(data.error)}</p>
      ) : (
        <Results data={data.data.content} />
      )}
      <div className="flex gap-4">
        <button disabled={page === 0 || data.isFetching} onClick={() => setPage((p) => p - 1)}>
          Previous
        </button>
        <span>Page {page + 1}</span>
        <button
          disabled={data.isFetching || !data.data || page + 1 >= data.data.totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          Next
        </button>
      </div>
    </section>
  );
}
