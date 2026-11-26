import { AppointmentForm, Requests, BookingPolicy } from './AppointmentOperations';
import FinancialReports from './FinancialReports';
import GstDocuments from './GstDocuments';
import GiftCardReport from './GiftCardReport';
import { isAdmin } from '../../auth/roles';
import {useState} from 'react';
import OwnerDesk from './OwnerDesk';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import { isDeskStaff, isStaff } from '../../auth/roles';
import client, { errorMessage } from '../../api/client';
import { Workspace } from '../../features/FeaturePage';
import Drafts from '../Staff/Drafts';
import SpaSettings from '../Staff/SpaSettings';
import Schedule from './Schedule';
import OwnerCatalog from './OwnerCatalog';
import ClaimListing from './ClaimListing';
import Media from '../Staff/Media';
import FrontDeskTeam from './FrontDeskTeam';
import Payouts from './Payouts';
type Spa = {
  id: number;
  name: string;
  active: boolean;
  verified?: boolean;
  kycStatus?: string;
  kycRejectedReason?: string;
};
type Link_ = { label: string; to: string; section: string };
/** Main workspaces and their related editing tools. */
export const ownerTabs: { label: string; to: string; sections: string[]; pages?: Link_[] }[] = [
  { label: 'Overview', to: '/owner', sections: ['overview'] },
  { label: 'Bookings', to: '/owner/bookings', sections: ['bookings'] },
  { label: 'Payments', to: '/owner/payments', sections: ['payments'] },
  { label: 'Payouts', to: '/owner/payouts', sections: ['payouts'] },
  { label: 'Customers', to: '/owner/customers', sections: ['customers'] },
  { label: 'Analytics', to: '/owner/analytics', sections: ['analytics'] },
  { label: 'Schedule', to: '/owner/availability', sections: ['availability'] },
  {
    label: 'Services & team',
    to: '/owner/services',
    sections: ['services', 'therapists'],
    pages: [
      { label: 'Treatments & prices', to: '/owner/services', section: 'services' },
      { label: 'Therapists', to: '/owner/therapists', section: 'therapists' }
    ]
  },
  { label: 'Packages', to: '/owner/packages', sections: ['packages'] },
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

/** What front-desk staff can open: the day's appointments and taking payments. */
export const deskSections = ['overview', 'bookings', 'payments'];
function Tabs({ section, full }: { section: string; full: boolean }) {
  const [params] = useSearchParams();
  const spaQuery = params.get('spa') ? '?spa=' + encodeURIComponent(params.get('spa')!) : '';
  const tabs = full ? ownerTabs : ownerTabs.filter((t) => deskSections.includes(t.sections[0]));
  const current = tabs.find((t) => t.sections.includes(section));
  return (
    <div className="space-y-3">
      <nav aria-label="Owner workspace" className="flex flex-wrap lg:flex-col gap-1">
        {tabs.map((t) => (
          <Link
            key={t.label}
            to={t.to + spaQuery}
            aria-current={t === current ? 'page' : undefined}
            className={`px-3 py-2 rounded-xl text-sm font-semibold ${t === current ? 'bg-primary-700 !text-white' : 'text-primary-900 hover:bg-primary-50'}`}
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
              to={p.to + spaQuery}
              aria-current={p.section === section ? 'page' : undefined}
              className={`-mb-px border-b-2 pb-2 ${p.section === section ? 'border-primary-700 font-semibold text-primary-800' : 'border-transparent text-slate-600 hover:text-slate-900'}`}
            >
              {p.label}
            </Link>
          ))}
          {current.label === 'Settings' && (
            <Link
              className="-mb-px border-b-2 border-transparent pb-2 text-slate-600 hover:text-slate-900"
              to="/features/spa-management"
            >
              More tools
            </Link>
          )}
        </nav>
      )}
    </div>
  );
}
import PushAlerts from './PushAlerts';

export default function OwnerPortal() {
  const { user } = useAuth();
  if (!isStaff(user?.roles) && !isDeskStaff(user?.roles))
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
  // The owner (or manager) runs the whole spa; front-desk staff only the desk of the spas they were added to.
  const full = isStaff(user?.roles);
  const path = location.pathname.split('/')[2] || 'overview';
  const section = full || deskSections.includes(path) ? path : 'overview';
  const [params, setParams] = useSearchParams();
  const selected = params.get('spa');
  const admin=isAdmin(user?.roles);
  const [spaSearch,setSpaSearch]=useState('');
  const spas = useQuery({
    queryKey: ['owner-spas', user?.id, full, admin, spaSearch],
    queryFn: async () =>
      (await client.get<Spa[]>(admin ? '/api/admin/finance/spas' : full ? '/api/onboarding/spas' : '/api/owner/desk-spas',{params:admin?{search:spaSearch}:undefined})).data
  });
  const spa = spas.data?.find((s) => String(s.id) === selected) || spas.data?.[0];
  const id = spa ? String(spa.id) : '';
  return (
    <div className="max-w-7xl mx-auto p-4 md:p-8 space-y-6">
      <header className="rounded-3xl bg-primary-900 text-white px-5 py-6 md:px-8 md:py-8 flex flex-wrap justify-between items-center gap-4">
        <div>
          <p className="text-sm text-primary-100">Nirvana Partners</p>
          <h1 className="text-3xl font-bold mt-1">{spa?.name || 'Your spa workspace'}</h1>
          {spa && full && (
            <p className="mt-2 text-sm">
              {spa.active ? 'Published' : 'Unpublished'} · Verification {spa.kycStatus || 'pending'}
            </p>
          )}
        </div>
        {admin && <label>Find spa by name or ID<input value={spaSearch} onChange={e=>setSpaSearch(e.target.value)} className="block text-slate-900"/></label>}
        {!!spas.data?.length && (
          <label className="grid gap-1 text-sm">
            Your spa
            <select
              className="rounded-xl bg-white px-3 py-2 text-slate-900 max-w-full md:max-w-sm"
              aria-label="Your spa"
              value={id}
              onChange={(e) => setParams({ spa: e.target.value })}
            >
              {spas.data.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </header>
      <div className="grid items-start gap-5 lg:grid-cols-[185px_minmax(0,1fr)]">
        <aside className="rounded-2xl border bg-white p-2 lg:p-3">
          <Tabs section={section} full={full} />
        </aside>
        <div className="min-w-0 space-y-5">
          {spas.isPending && <p>Loading your spas…</p>}
          {spas.isError && <p role="alert">{errorMessage(spas.error)}</p>}
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
              {full && !spa.active && (
                <p>
                  Add your treatments and opening hours, and{' '}
                  <Link className="underline" to="/owner/onboarding">
                    finish setup & verification
                  </Link>
                  . Platform approval is required before this spa accepts new bookings.
                </p>
              )}

              {section === 'overview' && user && full && (
                <PushAlerts key={user.id} user={String(user.id)} spa={id} />
              )}
              {(['overview', 'bookings', 'payments', 'customers', 'analytics'] as const).map(
                (view) =>
                  section === view && (
                    <OwnerDesk key={id + view} id={id} section={view} owner={full} />
                  )
              )}
              {section==='overview' && <Requests key={id} spa={id}/>}
              {section==='bookings' && <div key={id}><AppointmentForm spa={id} desk/><Requests spa={id}/></div>}
              {section==='settings' && <BookingPolicy key={id} spa={id}/>}
              {section==='payments' && <FinancialReports key={id} spa={id} owner={full} finance={isAdmin(user?.roles)}/>}
              {section==='payments' && full && <GstDocuments key={'gst-'+id} spa={id}/>}
              {section==='payments' && full && <GiftCardReport key={'gift'+id} spa={id}/>}
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
              {section === 'settings' && (
                <Link className="underline text-primary-700" to={'/spas/' + id}>
                  Preview public spa profile
                </Link>
              )}
              {section === 'settings' && <SpaSettings key={id} initialSpaId={id} />}
              {section === 'settings' && <FrontDeskTeam key={'team' + id} spaId={id} />}
              {section === 'photos' && <Media key={id} spaId={id} />}
              {section === 'payouts' && <Payouts key={id} spaId={id} />}
            </>
          ) : !full ? (
            !spas.isPending &&
            !spas.isError && (
              <p className="card p-6">
                You aren&apos;t on any spa&apos;s front desk now. Ask the spa owner to add you again.
              </p>
            )
          ) : (
            !spas.isPending &&
            !spas.isError && (
              <div className="card p-8 space-y-4">
                <h2 className="text-2xl font-bold">Welcome. Let’s create your first spa.</h2>
                <p>
                  If your spa is on Google Maps, claim your listing and we will fill in your name,
                  address and contact details for you to check. Otherwise, add your details
                  yourself.
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
          {full && !!spas.data?.length && section !== 'claim' && (
            <Link className="inline-block text-sm underline mt-3" to="/owner/claim">
              Add another spa from Google Maps
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
export { bookingActions, canSpaCancel } from './bookingActions';
