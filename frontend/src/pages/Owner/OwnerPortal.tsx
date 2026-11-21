import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import { isStaff } from '../../auth/roles';
import client, { errorMessage } from '../../api/client';
import { Workspace } from '../../features/FeaturePage';
import Drafts from '../Staff/Drafts';
import SpaSettings from '../Staff/SpaSettings';
import Schedule from './Schedule';
import OwnerCatalog from './OwnerCatalog';
import ClaimListing from './ClaimListing';
import Media from '../Staff/Media';
type Spa = {
  id: number;
  name: string;
  active: boolean;
  verified: boolean;
  kycStatus: string;
  kycRejectedReason?: string;
};
type Link_ = { label: string; to: string; section: string };
/** Five tabs; the two that hold several pages show those pages as sub-tabs. */
export const ownerTabs: { label: string; to: string; sections: string[]; pages?: Link_[] }[] = [
  { label: 'Overview', to: '/owner', sections: ['overview'] },
  { label: 'Bookings', to: '/owner/bookings', sections: ['bookings'] },
  { label: 'Schedule', to: '/owner/availability', sections: ['availability'] },
  {
    label: 'Services & team',
    to: '/owner/services',
    sections: ['services', 'packages', 'therapists'],
    pages: [
      { label: 'Treatments & prices', to: '/owner/services', section: 'services' },
      { label: 'Packages', to: '/owner/packages', section: 'packages' },
      { label: 'Therapists', to: '/owner/therapists', section: 'therapists' }
    ]
  },
  {
    label: 'Settings',
    to: '/owner/settings',
    sections: ['settings', 'photos', 'onboarding'],
    pages: [
      { label: 'Spa details & hours', to: '/owner/settings', section: 'settings' },
      { label: 'Photos', to: '/owner/photos', section: 'photos' },
      { label: 'Setup & verification', to: '/owner/onboarding', section: 'onboarding' }
    ]
  }
];

function Tabs({ section }: { section: string }) {
  const current = ownerTabs.find((t) => t.sections.includes(section));
  return (
    <div className="space-y-3">
      <nav aria-label="Owner workspace" className="flex flex-wrap gap-2">
        {ownerTabs.map((t) => (
          <Link
            key={t.label}
            to={t.to}
            aria-current={t === current ? 'page' : undefined}
            className={`px-4 py-2 rounded-xl border ${t === current ? 'bg-primary-700 text-white' : 'bg-white'}`}
          >
            {t.label}
          </Link>
        ))}
      </nav>
      {current?.pages && (
        <nav aria-label={current.label} className="flex flex-wrap gap-x-5 gap-y-1 border-b text-sm">
          {current.pages.map((p) => (
            <Link
              key={p.to}
              to={p.to}
              aria-current={p.section === section ? 'page' : undefined}
              className={`-mb-px border-b-2 pb-2 ${p.section === section ? 'border-primary-700 font-semibold text-primary-800' : 'border-transparent text-slate-600 hover:text-slate-900'}`}
            >
              {p.label}
            </Link>
          ))}
          {current.label === 'Settings' && (
            <Link className="-mb-px border-b-2 border-transparent pb-2 text-slate-600 hover:text-slate-900" to="/features/spa-management">
              More tools
            </Link>
          )}
        </nav>
      )}
    </div>
  );
}
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
      <Tabs section={section} />
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
      {!!spas.data?.length && section !== 'claim' && (
        <Link className="inline-block text-sm underline" to="/owner/claim">
          Add another spa from Google Maps
        </Link>
      )}
      {section === 'claim' ? (
        <ClaimListing />
      ) : section === 'onboarding' ? (
        <>
          <p className="card p-4">
            Is your spa on Google Maps?{' '}
            <Link className="underline" to="/owner/claim">
              Claim your listing
            </Link>{' '}
            and we will fill in most of this for you.
          </p>
          <Drafts owner />
        </>
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
                Add your treatments and opening hours, and{' '}
                <Link className="underline" to="/owner/onboarding">
                  finish setup & verification
                </Link>
                . Platform approval is required before this spa accepts new bookings.
              </p>
            )}
          </div>
          {section === 'overview' && <Overview key={id} id={id} />}
          {section === 'bookings' && <Bookings key={id} id={id} />}
          {section === 'availability' && (
            <div className="space-y-6" key={id}>
              <Schedule spaId={id} />
              <details className="card p-4">
                <summary className="cursor-pointer font-semibold">
                  Additional availability tools
                </summary>
                <Workspace id="calendar" spaId={id} section="availability" />
              </details>
            </div>
          )}
          {['therapists', 'services', 'packages'].includes(section) && (
            <OwnerCatalog
              key={`${id}-${section}`}
              spaId={id}
              kind={section as 'therapists' | 'services' | 'packages'}
            />
          )}
          {section === 'settings' && <SpaSettings key={id} initialSpaId={id} />}
          {section === 'photos' && <Media key={id} spaId={id} />}
        </>
      ) : (
        !spas.isPending &&
        !spas.isError && (
          <div className="card p-8 space-y-4">
            <h2 className="text-2xl font-bold">Welcome. Let’s create your first spa.</h2>
            <p>
              If your spa is on Google Maps, claim your listing and we will fill in your name,
              address and contact details for you to check. Otherwise, add your details yourself.
            </p>
            <div className="flex flex-wrap gap-3">
              <Link
                className="inline-block rounded-xl bg-primary-700 text-white px-5 py-3"
                to="/owner/claim"
              >
                Find my spa on Google Maps
              </Link>
              <Link className="inline-block rounded-xl border px-5 py-3" to="/owner/onboarding">
                Create from scratch
              </Link>
            </div>
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
type OwnerBooking = {
  bookingId: number;
  bookingReference: string;
  startTs: string;
  endTs: string;
  status: string;
  paymentMode?: string;
  serviceName?: string;
  customerName?: string;
  customerPhone?: string;
  therapistName?: string;
};
const CHECK_IN_WINDOW_MS = 120 * 60 * 1000;
/** Front-desk actions the server will accept for this appointment right now. */
export const bookingActions = (b: Pick<OwnerBooking, 'status' | 'startTs'>, now = Date.now()) => {
  const start = Date.parse(b.startTs);
  const started = now >= start;
  const actions: { path: string; label: string }[] = [];
  if (b.status === 'CONFIRMED' && now >= start - CHECK_IN_WINDOW_MS)
    actions.push({ path: 'arrive', label: 'Check in' });
  if ((b.status === 'CONFIRMED' || b.status === 'ARRIVED') && started)
    actions.push({ path: 'complete', label: 'Mark completed' });
  if (b.status === 'CONFIRMED' && started) actions.push({ path: 'no-show', label: 'No-show' });
  return actions;
};
/** The spa can cancel an open booking until the appointment has ended. */
export const canSpaCancel = (b: Pick<OwnerBooking, 'status' | 'endTs'>, now = Date.now()) =>
  (b.status === 'CONFIRMED' || b.status === 'PENDING_PAYMENT') && now < Date.parse(b.endTs);
function Bookings({ id }: { id: string }) {
  const [window, setWindow] = useState('TODAY');
  const [page, setPage] = useState(0);
  const [cancelling, setCancelling] = useState<number>();
  const [reason, setReason] = useState('');
  const qc = useQueryClient();
  const action = useMutation({
    mutationFn: async ({ bookingId, path, reason }: { bookingId: number; path: string; reason?: string }) =>
      (
        await client.post(
          `/api/owner/spas/${id}/bookings/${bookingId}/${path}`,
          reason ? { reason } : undefined
        )
      ).data,
    onSuccess: () => {
      setCancelling(undefined);
      setReason('');
      void qc.invalidateQueries({ queryKey: ['owner-bookings', id] });
      void qc.invalidateQueries({ queryKey: ['owner-overview', id] });
    }
  });
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
          {['TODAY', 'ATTENDANCE_PENDING', 'LAST_7_DAYS', 'LAST_30_DAYS', 'ALL'].map((w) => (
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
      ) : data.data.content.length === 0 ? (
        <p className="text-slate-500">No appointments in this period.</p>
      ) : (
        <ul className="space-y-3">
          {(data.data.content as OwnerBooking[]).map((b) => (
            <li key={b.bookingId} className="rounded-xl border border-slate-100 bg-white p-4 space-y-2">
              <p className="font-semibold">
                {b.serviceName || 'Appointment'} · {new Date(b.startTs).toLocaleString()}
              </p>
              <p>
                {b.customerName || 'Guest'}
                {b.customerPhone ? ` · ${b.customerPhone}` : ''}
                {b.therapistName ? ` · Therapist: ${b.therapistName}` : ''}
              </p>
              <p>
                {b.bookingReference} · <strong>{b.status.replaceAll('_', ' ')}</strong> ·{' '}
                {b.paymentMode === 'OFFLINE' ? 'Pay at spa' : 'Online payment'}
              </p>
              <div className="flex flex-wrap gap-2">
                {bookingActions(b).map((a) => (
                  <button
                    key={a.path}
                    type="button"
                    className="rounded-xl border px-4 py-2"
                    disabled={action.isPending}
                    onClick={() => action.mutate({ bookingId: b.bookingId, path: a.path })}
                  >
                    {a.label}
                  </button>
                ))}
                {canSpaCancel(b) && cancelling !== b.bookingId && (
                  <button
                    type="button"
                    className="rounded-xl border px-4 py-2 text-rose-700"
                    disabled={action.isPending}
                    onClick={() => {
                      setCancelling(b.bookingId);
                      setReason('');
                    }}
                  >
                    Cancel booking
                  </button>
                )}
              </div>
              {cancelling === b.bookingId && (
                <div className="space-y-2 rounded-xl bg-rose-50 p-3">
                  <p>
                    The guest is told the spa cancelled, and gets a full refund of anything paid
                    online or their package session back.
                  </p>
                  <label className="block">
                    Reason shown to the guest
                    <input
                      className="block w-full rounded-xl border p-3"
                      value={reason}
                      maxLength={500}
                      onChange={(e) => setReason(e.target.value)}
                    />
                  </label>
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="rounded-xl border bg-white px-4 py-2 text-rose-700"
                      disabled={action.isPending || !reason.trim()}
                      onClick={() =>
                        action.mutate({ bookingId: b.bookingId, path: 'cancel', reason: reason.trim() })
                      }
                    >
                      Confirm cancellation
                    </button>
                    <button
                      type="button"
                      className="rounded-xl border bg-white px-4 py-2"
                      onClick={() => setCancelling(undefined)}
                    >
                      Keep booking
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {action.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(action.error)}
        </p>
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
