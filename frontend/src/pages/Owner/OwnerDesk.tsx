import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { getSlots } from '../../api/bookingApi';
import { bookingActions, canSpaCancel } from './bookingActions';
import HealthFormPanel from './HealthFormPanel';
import { BookedAddOn } from '../../api/addOnApi';
import { DeskGroupBadge } from './GroupLinks';

const periods = {
  TODAY: 'Today',
  YESTERDAY: 'Yesterday',
  WEEK: 'Last 7 days',
  MONTH: 'Last 30 days',
  YEAR: 'Last 12 months',
  CUSTOM: 'Custom dates'
};
const input = 'rounded-xl border bg-white px-3 py-2 text-sm';
const button =
  'rounded-xl border border-primary-200 px-3 py-2 text-sm font-semibold text-primary-800 hover:bg-primary-50 disabled:opacity-50';
const label = (s?: string) => (s || 'Unknown').replaceAll('_', ' ').toLowerCase();
const money = (c: number, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR' }).format(
    c / 100
  );
const when = (v: string, zone: string) =>
  new Date(v).toLocaleString('en-IN', { timeZone: zone, dateStyle: 'medium', timeStyle: 'short' });
type Counts = {
  bookings: number;
  completed: number;
  confirmed: number;
  cancelled: number;
  pending: number;
};
type Summary = {
  arrivals: number;
  conflicts: number;
  packagesTrend: { day: string; sales: number; redemptions: number }[];
  /** Owner only: the desk sees the day's work, not the takings. */
  cashTrend?: { day: string; currency: string; collected: number; refunds: number }[];
  due: { currency: string; outstanding: number }[];
  timezone: string;
  today: string;
  dates: { from: string; to: string };
  counts: Counts;
  previous: Counts;
  activeSubscriptions: number;
  trend: {
    day: string;
    bookings: number;
    completed: number;
    confirmed: number;
    cancelled: number;
  }[];
  services: { name: string; visits: number; currency: string; valueCents: number }[];
  /** Owner only. DESK is money taken at the spa; ONLINE is held by Nirvana and paid out after commission. */
  money?: { currency: string; channel: 'DESK' | 'ONLINE'; collected: number; refunds: number; tips: number }[];
  payout?: { currency: string; online: number; commission: number; commissionPct: number; payout: number }[];
  access?: 'OWNER' | 'DESK';
};
type Row = {
  deskSource?: string;
  eventUtc: string;
  source: string;
  collected: number;
  refunded: number;
  receipt?: string;
  method: string;
  userId?: number;
  id: number;
  customer: string;
  service: string;
  reference: string;
  therapist?: string;
  therapistId?: number;
  therapistType?: string;
  preference?: string;
  guests: number;
  serviceId: number;
  room?: string;
  status: string;
  confirmation?: string;
  payment: string;
  currency: string;
  payable: number;
  /** Add-ons on the appointment, each its own line; payable already includes them. */
  addOns?: BookedAddOn[];
  startUtc: string;
  endUtc: string;
  lastUtc: string;
  visits: number;
  purchasedUtc: string;
  expiresUtc?: string;
  packageLevel: string;
  sessions: number;
  remaining: number;
  price: number;
  gatewayStatus?: string;
  deskMethod?: string;
  /** Taken at the desk so far (part payments included), its tips, and when the latest part was taken. */
  deskPaid?: number;
  deskTips?: number;
  deskCollectedUtc?: string;
  deskReturned?: number;
  channel?: 'DESK' | 'ONLINE';
  tip?: number;
};
type Records = {
  items: Row[];
  more: boolean;
  timezone: string;
  dates: { from: string; to: string };
};
type Section = 'overview' | 'analytics' | 'bookings' | 'payments' | 'customers';
const periodKeys = Object.keys(periods);
const localDay = (v: string, zone: string) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: zone }).format(new Date(v));
const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : Date.now().toString(36) + Math.random().toString(36).slice(2);
function Notice({ error, retry }: { error: unknown; retry: () => void }) {
  return (
    <div role="alert" className="rounded-xl bg-rose-50 p-4">
      {errorMessage(error)}{' '}
      <button className={button} onClick={retry}>
        Retry
      </button>
    </div>
  );
}

/** owner: the spa's owner or manager. Front-desk staff get the day's work without the money reports. */
export default function OwnerDesk({
  id,
  section,
  owner = true
}: {
  id: string;
  section: Section;
  owner?: boolean;
}) {
  const [url, setUrl] = useSearchParams();
  const param = (key: string) => url.get(key) || '';
  const change = (fields: Record<string, string>) => {
    const next = new URLSearchParams(url);
    next.delete('page');
    for (const [key, value] of Object.entries(fields)) {
      if (value) next.set(key, value);
      else next.delete(key);
    }
    setUrl(next);
  };
  const link = (section: string, fields: Record<string, string> = {}) => {
    const next = new URLSearchParams(url);
    for (const key of [
      'page',
      'status',
      'pending',
      'upcoming',
      'calendar',
      'serviceId',
      'therapistId',
      'packageId',
      'payment',
      'search',
      'view'
    ])
      next.delete(key);
    for (const [k, v] of Object.entries(fields)) next.set(k, v);
    return '/owner/' + section + '?' + next;
  };
  const defaultPeriod = section === 'customers' ? 'YEAR' : 'TODAY';
  const period = periodKeys.includes(param('period')) ? param('period') : defaultPeriod;
  const dates = period === 'CUSTOM' ? { from: param('from'), to: param('to') } : {};
  const valid = period !== 'CUSTOM' || Boolean(dates.from && dates.to && dates.from <= dates.to);
  const summaryView = section === 'overview' || section === 'analytics';
  const summary = useQuery({
    queryKey: ['desk', id, 'summary', period, dates],
    enabled: valid && summaryView,
    queryFn: async () =>
      (
        await client.get<Summary>('/api/owner/spas/' + id + '/desk/summary', {
          params: { period, ...dates }
        })
      ).data
  });
  const capacity = useQuery({
    queryKey: ['desk', id, 'capacity', period, dates],
    enabled: valid && section === 'analytics',
    queryFn: async () =>
      (
        await client.get<{
          utilization: Record<string, { availableSeconds: number; occupiedSeconds: number }>;
          heat: number[][];
        }>('/api/owner/spas/' + id + '/desk/capacity', { params: { period, ...dates } })
      ).data
  });
  const agenda = section === 'overview';
  const calendar = section === 'bookings' && ['DAY', 'WEEK'].includes(param('calendar'));
  const calendarDays = param('calendar') === 'WEEK' ? 7 : 1;
  const selectCalendar = (mode: string, day: string) => {
    if (!mode) {
      change({ calendar: '', period: 'TODAY', from: '', to: '' });
      return;
    }
    if (!day) return;
    const end = new Date(day + 'T12:00:00Z');
    end.setUTCDate(end.getUTCDate() + (mode === 'WEEK' ? 6 : 0));
    change({ calendar: mode, period: 'CUSTOM', from: day, to: end.toISOString().slice(0, 10) });
  };
  const buyers = section === 'customers' && param('view') === 'BUYERS';
  const ledger = owner && section === 'payments' && param('view') === 'TRANSACTIONS';
  const view = ledger
    ? 'TRANSACTIONS'
    : section === 'customers'
      ? buyers
        ? 'BUYERS'
        : 'VISITORS'
      : 'APPOINTMENTS';
  const page = agenda ? 0 : Math.max(0, Number(param('page')) || 0);
  const filters = agenda
    ? { period: 'TODAY', view, page }
    : {
        period,
        ...dates,
        view,
        page,
        search: param('search'),
        serviceId: param('serviceId') || undefined,
        therapistId: param('therapistId') || undefined,
        packageId: buyers ? param('packageId') || undefined : undefined,
        status: param('status'),
        pending: param('pending') === 'true',
        upcoming: param('upcoming') === 'true',
        payment: param('payment')
      };
  const catalogFilters = useQuery({
    queryKey: ['desk', id, 'filter-options'],
    enabled: section === 'bookings',
    queryFn: async () => {
      const base = '/api/owner/spas/' + id + '/';
      const [services, therapists] = await Promise.all(
        ['services', 'therapists'].map((path) =>
          client.get<{ id: number; name: string }[]>(base + path)
        )
      );
      return { serviceId: services.data, therapistId: therapists.data };
    }
  });
  const rows = useQuery({
    queryKey: ['desk', id, 'records', filters],
    enabled: (valid || agenda) && section !== 'analytics',
    queryFn: async () =>
      (await client.get<Records>('/api/owner/spas/' + id + '/desk/records', { params: filters }))
        .data
  });
  const download = useMutation({
    mutationFn: async () => {
      const response = await client.get('/api/owner/spas/' + id + '/desk/transactions.csv', {
        params: { period, ...dates, search: param('search') },
        responseType: 'blob'
      });
      const url = URL.createObjectURL(response.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'nirvana-transactions.csv';
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  });
  const [term, setTerm] = useState(param('search'));
  const s = summary.data;
  const r = rows.data;
  const header = {
    overview: 'Daily overview',
    analytics: 'Business performance',
    bookings: 'Appointments',
    payments: 'Appointment payments',
    customers: 'Customer directory'
  }[section];
  const periodInfo = summaryView ? s : r;
  return (
    <section className={agenda?"flex flex-col gap-4 md:block md:space-y-5":"space-y-5"}>
      <header className="-order-3 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-primary-900">{header}</h2>
          {periodInfo && valid && (
            <p className="text-sm text-slate-600">
              {periodInfo.dates.from} – {periodInfo.dates.to} · {periodInfo.timezone}
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2 items-end">
          <label className="grid text-sm">
            Reporting period
            <select
              className={input}
              value={period}
              onChange={(e) => change({ period: e.target.value, calendar: '' })}
            >
              {Object.entries(periods).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          {period === 'CUSTOM' &&
            ['from', 'to'].map((k) => (
              <label className="grid capitalize" key={k}>
                {k}
                <input
                  className={input}
                  type="date"
                  value={param(k)}
                  onChange={(e) => change({ [k]: e.target.value })}
                />
              </label>
            ))}
        </div>
      </header>
      {!valid && <p>Select a start and end date.</p>}
      {summaryView &&
        valid &&
        (summary.isError ? (
          <Notice error={summary.error} retry={() => void summary.refetch()} />
        ) : !s ? (
          <p role="status">Loading business overview…</p>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              {(['bookings', 'confirmed', 'completed', 'cancelled'] as const).map((k) => (
                <Link
                  key={k}
                  to={link('bookings', {
                    period,
                    ...(k === 'bookings' ? {} : { status: k.toUpperCase() })
                  })}
                  className="card p-4 hover:border-primary-500"
                >
                  <p className="capitalize text-sm">{k}</p>
                  <strong className="block text-3xl my-2">{s.counts[k]}</strong>
                  <small>{s.previous[k]} previous period</small>
                </Link>
              ))}
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="rounded-2xl p-5 bg-primary-900 text-white [&_a]:!text-white">
                <h3 className="text-lg font-semibold">Awaiting spa confirmation</h3>
                <Link
                  className="text-4xl font-bold underline"
                  to={link('bookings', { period, pending: 'true' })}
                >
                  {s.counts.pending}
                </Link>
                <p className="mt-2 text-sm">Appointments in this period awaiting your reply</p>
                <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm">
                  <Link
                    className="underline"
                    to={link('bookings', { period: 'TODAY', upcoming: 'true' })}
                  >
                    {s.arrivals} still to arrive today
                  </Link>
                  {s.conflicts > 0 &&
                    (owner ? (
                      <Link className="underline" to={link('availability')}>
                        {s.conflicts} scheduling {s.conflicts === 1 ? 'clash' : 'clashes'} today
                      </Link>
                    ) : (
                      <span>
                        {s.conflicts} scheduling {s.conflicts === 1 ? 'clash' : 'clashes'} today
                      </span>
                    ))}
                </div>
              </div>
              <div className="card p-5">
                {s.money && (
                  <>
                    <h3 className="font-semibold">Collections & refunds</h3>
                    {s.money.length ? (
                      s.money.map((m) => (
                        <div key={m.currency + m.channel} className="mb-2">
                          <p className="text-sm text-slate-600">
                            {m.channel === 'DESK'
                              ? 'Taken at your desk'
                              : 'Paid online · held by Nirvana until payout'}
                          </p>
                          <strong className="text-xl">
                            {money(m.collected - m.refunds, m.currency)} net
                          </strong>
                          <p className="text-sm">
                            {money(m.collected, m.currency)} collected ·{' '}
                            {money(m.refunds, m.currency)} refunded
                            {m.tips > 0 && ' · ' + money(m.tips, m.currency) + ' in tips'}
                          </p>
                        </div>
                      ))
                    ) : (
                      <p>No recorded money movements for these dates.</p>
                    )}
                    {s.payout?.map((p) => (
                      <p key={p.currency} className="text-sm">
                        Expected payout from Nirvana:{' '}
                        <strong>{money(p.payout, p.currency)}</strong> (
                        {money(p.online, p.currency)} online less {p.commissionPct}% commission,{' '}
                        {money(p.commission, p.currency)}). An estimate: the payout statement is
                        final.
                      </p>
                    ))}
                  </>
                )}
                <Link
                  className="block underline"
                  to={link('payments', { period, payment: 'DUE_AT_SPA' })}
                >
                  {s.due.length
                    ? s.due.map((d) => money(d.outstanding, d.currency)).join(' · ')
                    : 'No balance'}{' '}
                  due at spa
                </Link>
                <p className="text-sm mt-2">
                  {s.activeSubscriptions} active spa package subscriptions now
                </p>
              </div>
            </div>
          </>
        ))}
      {section !== 'analytics' && (
        <div className="card -order-2 p-4 md:p-6 space-y-4">
          <div className="flex flex-wrap justify-between items-center gap-2">
            <h3 className="text-xl font-semibold">
              {agenda
                ? 'Today’s agenda'
                : section === 'customers'
                  ? buyers
                    ? 'Package purchases'
                    : 'Completed visits by customer'
                  : section === 'payments'
                    ? ledger
                      ? 'Transactions by collection or refund date'
                      : 'Payments by appointment date'
                    : 'Booking list'}
            </h3>
            {agenda && r && (
              <Link className={button} to={link('bookings', { period: 'TODAY' })}>
                All appointments · {r.dates.from}
              </Link>
            )}
          </div>
          {!agenda && (
            <div className="flex items-end flex-wrap gap-2">
              {section === 'bookings' && (
                <>
                  <label className="grid text-sm">
                    Layout
                    <select
                      className={input}
                      value={param('calendar')}
                      onChange={(e) =>
                        selectCalendar(
                          e.target.value,
                          r?.dates.from || new Date().toISOString().slice(0, 10)
                        )
                      }
                    >
                      <option value="">List</option>
                      <option value="DAY">Day calendar</option>
                      <option value="WEEK">Week calendar</option>
                    </select>
                  </label>
                  {calendar && (
                    <label className="grid text-sm">
                      Calendar starting
                      <input
                        className={input}
                        type="date"
                        value={param('from')}
                        onChange={(e) => selectCalendar(param('calendar'), e.target.value)}
                      />
                    </label>
                  )}
                </>
              )}
              {section === 'payments' && (
                <>
                  <label className="grid text-sm">
                    Payment view
                    <select
                      className={input}
                      value={ledger ? 'TRANSACTIONS' : 'APPOINTMENTS'}
                      onChange={(e) =>
                        change({
                          view: e.target.value,
                          search: '',
                          status: '',
                          payment: '',
                          pending: ''
                        })
                      }
                    >
                      <option value="APPOINTMENTS">Appointment balances</option>
                      {owner && <option value="TRANSACTIONS">Collections & refunds</option>}
                    </select>
                  </label>
                  {ledger && (
                    <button
                      className={button}
                      disabled={download.isPending || !valid}
                      onClick={() => download.mutate()}
                    >
                      Export filtered CSV
                    </button>
                  )}
                  {download.isError && <p role="alert">{errorMessage(download.error)}</p>}
                </>
              )}
              {section === 'customers' && (
                <label className="grid text-sm">
                  Directory
                  <select
                    className={input}
                    value={view}
                    onChange={(e) => change({ view: e.target.value })}
                  >
                    <option value="VISITORS">Service customers</option>
                    <option value="BUYERS">Package buyers</option>
                  </select>
                </label>
              )}
              <form
                className="flex items-end gap-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  change({ search: term });
                }}
              >
                <label className="grid text-sm">
                  Search
                  <input
                    className={input}
                    placeholder={
                      section === 'customers' ? 'Customer name' : 'Name, reference or service'
                    }
                    value={term}
                    onChange={(e) => setTerm(e.target.value)}
                  />
                </label>
                <button className={button}>Search</button>
              </form>
              {section === 'bookings' &&
                catalogFilters.data &&
                (['serviceId', 'therapistId'] as const).map((k) => (
                  <label key={k} className="grid text-sm">
                    {k === 'serviceId' ? 'Service' : 'Therapist'}
                    <select
                      className={input}
                      value={param(k)}
                      onChange={(e) => change({ [k]: e.target.value })}
                    >
                      <option value="">All</option>
                      {catalogFilters.data[k].map((v) => (
                        <option key={v.id} value={v.id}>
                          {v.name}
                        </option>
                      ))}
                    </select>
                  </label>
                ))}
              {section !== 'customers' && !ledger && (
                <>
                  <label className="grid text-sm">
                    Appointment status
                    <select
                      className={input}
                      value={param('status')}
                      onChange={(e) => change({ status: e.target.value })}
                    >
                      <option value="">All statuses</option>
                      {[
                        'PENDING_PAYMENT',
                        'CONFIRMED',
                        'ARRIVED',
                        'COMPLETED',
                        'CANCELLED',
                        'NO_SHOW'
                      ].map((k) => (
                        <option value={k} key={k}>
                          {label(k)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="grid text-sm">
                    Payment status
                    <select
                      className={input}
                      value={param('payment')}
                      onChange={(e) => change({ payment: e.target.value })}
                    >
                      <option value="">All payments</option>
                      {[
                        'DUE_AT_SPA',
                        'PAID_AT_SPA',
                        'PAID',
                        'PACKAGE',
                        'GIFT_CARD',
                        'WALLET',
                        'PENDING',
                        'INIT',
                        'FAILED',
                        'REFUNDED',
                        'PARTIALLY_REFUNDED',
                        'UNPAID'
                      ].map((k) => (
                        <option key={k} value={k}>
                          {label(k)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      checked={param('pending') === 'true'}
                      onChange={(e) => change({ pending: e.target.checked ? 'true' : '' })}
                    />{' '}
                    Awaiting confirmation
                  </label>
                </>
              )}
            </div>
          )}
          {rows.isError ? (
            <Notice error={rows.error} retry={() => void rows.refetch()} />
          ) : !r ? (
            (valid || agenda) && <p>Loading records…</p>
          ) : calendar ? (
            <div className="grid gap-3">
              {Array.from({ length: calendarDays }, (_, i) => {
                const day = new Date(r.dates.from + 'T12:00:00Z');
                day.setUTCDate(day.getUTCDate() + i);
                const date = day.toISOString().slice(0, 10);
                const matches = r.items.filter(
                  (b) =>
                    new Intl.DateTimeFormat('en-CA', {
                      timeZone: r.timezone,
                      year: 'numeric',
                      month: '2-digit',
                      day: '2-digit'
                    }).format(new Date(b.startUtc)) === date
                );
                return (
                  <section className="rounded-xl border p-3" key={date}>
                    <h4 className="font-semibold text-primary-800">
                      {day.toLocaleDateString('en-IN', {
                        weekday: 'long',
                        month: 'short',
                        day: 'numeric',
                        timeZone: 'UTC'
                      })}
                    </h4>
                    {matches.map((b) => (
                      <Appointment key={b.id} row={b} zone={r.timezone} spa={id} owner={owner} />
                    ))}
                    {!matches.length && <p className="text-sm">No appointments on this page.</p>}
                  </section>
                );
              })}
            </div>
          ) : r.items.length === 0 ? (
            <p className="rounded-xl bg-stone-50 p-5">
              No matching {section === 'customers' ? 'customers' : 'appointments'} for these dates.
            </p>
          ) : (
            r.items.map((row) =>
              ledger ? (
                <article key={row.source + row.reference + row.eventUtc} className="border-t py-3">
                  <strong>{money(row.collected - row.refunded, row.currency)}</strong> ·{' '}
                  {row.source} ·{' '}
                  {row.channel === 'DESK' ? 'held by the spa' : 'held by Nirvana until payout'}
                  <p>
                    {when(row.eventUtc, r.timezone)} · {row.reference} · {row.method}
                    {!!row.tip && ' · ' + money(row.tip, row.currency) + ' tip'}
                  </p>
                  {row.receipt && <small>Receipt {row.receipt}</small>}
                </article>
              ) : section === 'customers' ? (
                <article key={row.id} className="border-t py-3">
                  <strong>{row.customer || 'Guest'}</strong>
                  <CustomerHistory
                    spa={id}
                    user={buyers ? row.userId! : row.id}
                    zone={r.timezone}
                  />
                  {buyers ? (
                    <p>
                      {row.packageLevel} · {row.remaining} of {row.sessions} sessions remaining ·{' '}
                      {money(row.price * 100, row.currency)} · Purchased{' '}
                      {when(row.purchasedUtc, r.timezone)}
                      {row.expiresUtc && ' · Expires ' + when(row.expiresUtc, r.timezone)}
                    </p>
                  ) : (
                    <p>
                      {row.visits} completed visits · Last visit {when(row.lastUtc, r.timezone)}
                    </p>
                  )}
                </article>
              ) : (
                <Appointment key={row.id} row={row} zone={r.timezone} spa={id} owner={owner} />
              )
            )
          )}
          {!agenda && r && (
            <div className="flex gap-4 items-center">
              <button
                className={button}
                disabled={!page}
                onClick={() => change({ page: String(page - 1) })}
              >
                Previous
              </button>
              Page {page + 1}
              <button
                className={button}
                disabled={!r.more}
                onClick={() => change({ page: String(page + 1) })}
              >
                Next
              </button>
            </div>
          )}
        </div>
      )}
      {summaryView && s && valid && (
        <div className="grid gap-4 lg:grid-cols-[2fr_1fr]">
          <section className="card p-5">
            <h3 className="font-semibold text-lg">Booking volume</h3>
            <p className="text-sm mb-4">Appointments by scheduled date</p>
            <p className="text-xs mb-2">
              Dark green · completed　 Light green · confirmed　 Amber · cancelled　 Grey · other
            </p>
            <div aria-hidden="true" className="flex items-end gap-1 h-28 border-b">
              {s.trend.map((b) => (
                <div
                  key={b.day}
                  title={b.day + ' · ' + b.bookings + ' appointments'}
                  className="flex flex-col justify-end flex-1 overflow-hidden bg-stone-300 rounded-t"
                  style={{
                    height:
                      (b.bookings / Math.max(1, ...s.trend.map((x) => x.bookings))) * 100 + '%'
                  }}
                >
                  <span
                    className="bg-amber-400"
                    style={{ height: (b.cancelled / Math.max(1, b.bookings)) * 100 + '%' }}
                  />
                  <span
                    className="bg-primary-400"
                    style={{ height: (b.confirmed / Math.max(1, b.bookings)) * 100 + '%' }}
                  />
                  <span
                    className="bg-primary-800"
                    style={{ height: (b.completed / Math.max(1, b.bookings)) * 100 + '%' }}
                  />
                </div>
              ))}
            </div>
            <div className="flex justify-between text-xs mt-1">
              <span>{s.dates.from}</span>
              <span>{s.dates.to}</span>
            </div>
            <details className="mt-3">
              <summary>View chart data</summary>
              <div className="table-scroll" tabIndex={0} role="region" aria-label="Chart data">
              <table className="w-full text-left">
                <thead>
                  <tr>
                    <th>Period starting</th>
                    <th>Bookings</th>
                    <th>Completed</th>
                    <th>Confirmed</th>
                    <th>Cancelled</th>
                  </tr>
                </thead>
                <tbody>
                  {s.trend.map((b) => (
                    <tr key={b.day}>
                      <td>{b.day}</td>
                      <td>{b.bookings}</td>
                      <td>{b.completed}</td>
                      <td>{b.confirmed}</td>
                      <td>{b.cancelled}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              </div>
            </details>
          </section>
          <section className="card p-5">
            <h3 className="font-semibold text-lg">Top services</h3>
            <p className="text-sm">Completed visits and appointment value</p>
            {s.services.length ? (
              s.services.map((v, i) => (
                <p className="flex justify-between py-2 border-b" key={i}>
                  <span>{v.name}</span>
                  <strong>
                    {v.visits} · {money(v.valueCents, v.currency)}
                  </strong>
                </p>
              ))
            ) : (
              <p className="mt-4">No completed visits in this period.</p>
            )}
          </section>
        </div>
      )}
      {section === 'analytics' && s && (
        <section className="card p-5">
          <h3 className="mb-2 text-lg font-semibold">Package sales & redemptions</h3>
          {s.packagesTrend.some((x) => x.sales || x.redemptions) ? (
            <div className="table-scroll" tabIndex={0} role="region" aria-label="Chart data">
            <table className="w-full text-left text-sm">
              <thead>
                <tr>
                  <th>Period starting</th>
                  <th>Sold</th>
                  <th>Sessions used</th>
                </tr>
              </thead>
              <tbody>
                {s.packagesTrend.map((v) => (
                  <tr key={v.day}>
                    <td>{v.day}</td>
                    <td>{v.sales}</td>
                    <td>{v.redemptions}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          ) : (
            <p>No package sales or redemptions for these dates.</p>
          )}
        </section>
      )}
      {section === 'analytics' && valid && (
        <section className="card p-5 space-y-4">
          <h3 className="font-semibold text-lg">Working capacity</h3>
          {capacity.isError ? (
            <Notice error={capacity.error} retry={() => void capacity.refetch()} />
          ) : !capacity.data ? (
            <p>Loading capacity…</p>
          ) : (
            <>
              {['Therapists', 'Rooms'].map((k) => {
                const u = capacity.data.utilization[k];
                return (
                  <div key={k}>
                    <p>
                      {k} ·{' '}
                      {u?.availableSeconds
                        ? Math.round((100 * u.occupiedSeconds) / u.availableSeconds) + '% booked'
                        : 'No scheduled capacity'}
                    </p>
                    {!!u?.availableSeconds && (
                      <>
                        <progress
                          aria-label={k + ' utilization'}
                          value={u.occupiedSeconds}
                          max={u.availableSeconds}
                          className="w-full accent-green-800"
                        />
                        <small>
                          {Math.round(u.occupiedSeconds / 60)} of{' '}
                          {Math.round(u.availableSeconds / 60)} scheduled minutes
                        </small>
                      </>
                    )}
                  </div>
                );
              })}
              <p className="text-sm">
                Therapists use rostered work less breaks, leave and spa closures. Rooms use offered
                slot intervals less closures. Overlapping times count once. Unassigned bookings are
                excluded.
              </p>
              <details>
                <summary>Booking demand by weekday and hour</summary>
                <div className="overflow-x-auto">
                  <table className="text-xs">
                    <thead>
                      <tr>
                        <th>Day / hour</th>
                        {Array.from({ length: 24 }, (_, i) => (
                          <th key={i} className="p-1">
                            {i}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {capacity.data.heat.map((day, i) => (
                        <tr key={i}>
                          <th>{['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][i]}</th>
                          {day.map((v, h) => (
                            <td
                              key={h}
                              className={v ? 'bg-primary-100 p-1 text-center' : 'p-1 text-center'}
                            >
                              {v}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </details>
            </>
          )}
        </section>
      )}
      {section === 'analytics' && s?.cashTrend && (
        <section className="card p-5">
          <h3 className="font-semibold text-lg">Collections and refunds over time</h3>
          {s.cashTrend.length ? (
            s.cashTrend.map((v) => (
              <div key={v.day + v.currency} className="my-2">
                <span>
                  {v.day} · {money(v.collected, v.currency)} collected ·{' '}
                  {money(v.refunds, v.currency)} refunded
                </span>
                <div className="flex gap-1">
                  <progress
                    aria-label="Collections"
                    value={v.collected}
                    max={Math.max(
                      1,
                      ...(s.cashTrend ?? [])
                        .filter((x) => x.currency === v.currency)
                        .map((x) => Math.max(x.collected, x.refunds))
                    )}
                    className="accent-green-700"
                  />
                  <progress
                    aria-label="Refunds"
                    value={v.refunds}
                    max={Math.max(
                      1,
                      ...(s.cashTrend ?? [])
                        .filter((x) => x.currency === v.currency)
                        .map((x) => Math.max(x.collected, x.refunds))
                    )}
                    className="accent-amber-700"
                  />
                </div>
              </div>
            ))
          ) : (
            <p>No recorded transactions.</p>
          )}
        </section>
      )}
      {agenda && (
        <div className="flex flex-wrap gap-2">
          {[
            ['Manage services', 'services'],
            ['Manage packages', 'packages'],
            ['Staff availability', 'availability'],
            ['Edit spa profile', 'settings']
          ].map(([title, path]) => (
            <Link className={button} key={path} to={link(path)}>
              {title}
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

function Appointment({
  row: b,
  zone,
  spa,
  owner
}: {
  row: Row;
  zone: string;
  spa: string;
  owner: boolean;
}) {
  const [showHistory, setShowHistory] = useState(false);
  const history = useQuery({
    queryKey: ['desk', spa, 'history', b.id],
    enabled: showHistory,
    queryFn: async () =>
      (
        await client.get<
          { action: string; entity: string; actor?: number; atUtc: string; reason?: string }[]
        >('/api/owner/spas/' + spa + '/desk/bookings/' + b.id + '/history')
      ).data
  });
  const [mode, setMode] = useState('');
  const [reason, setReason] = useState('');
  const [day, setDay] = useState('');
  const [slot, setSlot] = useState('');
  const [method, setMethod] = useState('CASH');
  // A desk payment: the amount (a part payment is fine), a tip, and a key so a retry lands once.
  const [amount, setAmount] = useState('');
  const [tip, setTip] = useState('');
  const [key, setKey] = useState('');
  const rupees = (v: string) => Math.round(Number(v || 0) * 100);
  const amountOk = rupees(amount) > 0 && rupees(amount) <= b.payable && rupees(tip) >= 0;
  const qc = useQueryClient();
  const slots = useQuery({
    queryKey: ['desk-slots', spa, b.serviceId, b.guests, day],
    enabled: mode === 'move' && !!day,
    queryFn: () => getSlots(spa, String(b.serviceId), day, b.guests)
  });
  const chosen =
    slots.data?.filter(
      (s) =>
        s.available &&
        Date.parse(s.startTime) !== Date.parse(b.startUtc) &&
        (b.preference === 'NAMED'
          ? s.therapists.some((t) => t.available && t.id === String(b.therapistId))
          : b.preference === 'LABEL'
            ? s.therapistTypes.some((t) => t.type === b.therapistType && t.availableCount > 0)
            : true)
    ) || [];
  const action = useMutation({
    mutationFn: async () => {
      const prefix = '/api/owner/spas/' + spa;
      if (mode === 'confirm' || mode === 'decline')
        await client.post(prefix + '/desk/bookings/' + b.id + '/reply', {
          accept: mode === 'confirm',
          reason
        });
      else if (mode === 'move')
        await client.post(prefix + '/desk/bookings/' + b.id + '/move', {
          slotId: Number(slot),
          reason
        });
      else if (mode === 'collect')
        await client.post(prefix + '/desk/bookings/' + b.id + '/collect', {
          method,
          reference: reason,
          amountCents: rupees(amount),
          tipCents: rupees(tip),
          requestKey: key
        });
      else if (mode === 'return')
        await client.post(prefix + '/desk/bookings/' + b.id + '/return');
      else if (mode === 'void')
        await client.post(prefix + '/desk/bookings/' + b.id + '/void', { reason });
      else
        await client.post(
          prefix + '/bookings/' + b.id + '/' + mode,
          mode === 'cancel' ? { reason } : undefined
        );
    },
    onSuccess: async () => {
      setMode('');
      setReason('');
      await qc.invalidateQueries({ queryKey: ['desk', spa] });
      await qc.invalidateQueries({ queryKey: ['desk-slots', spa] });
    }
  });
  const shape = { status: b.status, startTs: b.startUtc, endTs: b.endUtc };
  const actions = bookingActions(shape);
  if (['PENDING', 'NEEDS_CONTACT', 'NO_MOBILE'].includes(b.confirmation ?? '') && b.status === 'CONFIRMED')
    actions.unshift(
      { path: 'confirm', label: 'Confirm appointment' },
      { path: 'decline', label: 'Decline appointment' }
    );
  if (b.status === 'CONFIRMED' && Date.parse(b.startUtc) > Date.now())
    actions.push({ path: 'move', label: 'Reschedule' });
  if (canSpaCancel(shape)) actions.push({ path: 'cancel', label: 'Cancel booking' });
  const tookMoney = !!b.deskPaid && !b.deskReturned;
  if (owner && tookMoney && b.status === 'CANCELLED')
    actions.push({ path: 'return', label: 'Record money returned' });
  if (b.payment === 'DUE_AT_SPA')
    actions.push({ path: 'collect', label: b.deskPaid ? 'Record next payment' : 'Record payment' });
  // A payment recorded by mistake can be voided by the owner on the day it was recorded.
  if (
    owner &&
    tookMoney &&
    b.deskCollectedUtc &&
    localDay(b.deskCollectedUtc, zone) === localDay(new Date().toISOString(), zone)
  )
    actions.push({ path: 'void', label: 'Void last desk payment' });
  return (
    <article className="border-t py-4 space-y-2">
      <div className="flex flex-wrap justify-between gap-2">
        <div>
          <strong>{b.service}</strong>{b.deskSource && <span> · {b.deskSource==='PHONE'?'Phone':'Walk-in'}</span>} · {when(b.startUtc, zone)}
          <p className="text-sm text-slate-600">
            {b.customer || 'Guest'} · {b.therapist || 'Any therapist'}
            {b.room && ' · ' + b.room}
          </p>
        </div>
        <div className="text-sm">
          <p className="font-semibold capitalize">
            <span
              className={
                'inline-block rounded-full px-2 py-1 mr-1 ' +
                (b.status === 'CANCELLED'
                  ? 'bg-rose-50 text-rose-800'
                  : b.status === 'PENDING_PAYMENT'
                    ? 'bg-amber-50 text-amber-900'
                    : 'bg-primary-50 text-primary-900')
              }
            >
              {label(b.status)}
            </span>
            <span
              className={
                'inline-block rounded-full px-2 py-1 ' +
                (b.payment === 'DUE_AT_SPA'
                  ? 'bg-amber-50 text-amber-900'
                  : 'bg-stone-100 text-stone-800')
              }
            >
              {label(b.payment)}
            </span>
          </p>
          <p>
            {b.reference}
            {!!b.deskPaid && ' · ' + money(b.deskPaid, b.currency) + ' paid at desk'}
            {!!b.deskTips && ' + ' + money(b.deskTips, b.currency) + ' tip'}
            {b.payment === 'DUE_AT_SPA' && ' · ' + money(b.payable, b.currency) + ' due'}
          </p>
          {!!b.addOns?.length && (
            <ul aria-label="Add-ons" className="text-sm">
              {b.addOns.map((a, i) => (
                <li key={i}>
                  + {a.name}
                  {a.quantity > 1 && ' × ' + a.quantity}
                  {a.extraMinutes > 0 && ' (' + a.extraMinutes + ' min)'} · {money(a.amountCents, a.currency)}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {actions.map((a) => (
          <button
            className={button}
            disabled={action.isPending}
            key={a.path}
            onClick={() => {
              action.reset();
              setMode(a.path);
              setReason('');
              setSlot('');
              if (a.path === 'collect') {
                setAmount((b.payable / 100).toFixed(2));
                setTip('');
                setKey(newKey());
              }
            }}
          >
            {a.label}
          </button>
        ))}
      </div>
      <DeskGroupBadge spa={spa} booking={b.id} />
      <HealthFormPanel spa={spa} booking={b.id} />
      <details onToggle={(e) => setShowHistory(e.currentTarget.open)}>
        <summary className="text-sm text-primary-700">Appointment history</summary>
        {history.isError ? (
          <Notice error={history.error} retry={() => void history.refetch()} />
        ) : history.data?.length ? (
          history.data.map((v, i) => (
            <p key={i} className="text-sm">
              {when(v.atUtc, zone)} · {label(v.entity)} {label(v.action)} ·{' '}
              {v.actor ? 'Staff ' + v.actor : 'System'} {v.reason && ' · ' + v.reason}
            </p>
          ))
        ) : (
          <p className="text-sm">
            {history.isLoading ? 'Loading history…' : 'No audited changes recorded.'}
          </p>
        )}
      </details>
      {action.isSuccess && !mode && (
        <p role="status" className="text-sm text-primary-800">
          Appointment updated.
        </p>
      )}
      {mode && (
        <form
          className="bg-stone-50 rounded-xl p-4 space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            action.mutate();
          }}
        >
          <h4 className="font-semibold">
            {actions.find((a) => a.path === mode)?.label} · {b.reference}
          </h4>
          {mode === 'move' && (
            <div className="flex flex-wrap gap-2">
              <label className="grid">
                New date
                <input
                  type="date"
                  className={input}
                  required
                  value={day}
                  onChange={(e) => {
                    setDay(e.target.value);
                    setSlot('');
                  }}
                />
              </label>
              <label className="grid">
                Available time
                <select
                  className={input}
                  value={slot}
                  required
                  onChange={(e) => setSlot(e.target.value)}
                >
                  <option value="">Select time</option>
                  {chosen.map((s) => (
                    <option key={s.id} value={s.id}>
                      {when(s.startTime, zone)}
                    </option>
                  ))}
                </select>
              </label>
              {slots.isError && <p role="alert">{errorMessage(slots.error)}</p>}
              {slots.isSuccess && !chosen.length && <p>No matching times on this date.</p>}
            </div>
          )}
          {mode === 'collect' && (
            <div className="flex flex-wrap gap-2">
              <label className="grid">
                Method
                <select
                  className={input}
                  value={method}
                  onChange={(e) => setMethod(e.target.value)}
                >
                  {['CASH', 'UPI', 'CARD'].map((v) => (
                    <option key={v}>{v}</option>
                  ))}
                </select>
              </label>
              <label className="grid">
                Amount received (balance {money(b.payable, b.currency)})
                <input
                  className={input}
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  max={(b.payable / 100).toFixed(2)}
                  required
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
              </label>
              <label className="grid">
                Tip (optional)
                <input
                  className={input}
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="0.01"
                  value={tip}
                  onChange={(e) => setTip(e.target.value)}
                />
              </label>
              {rupees(amount) > 0 && rupees(amount) < b.payable && (
                <p className="w-full text-sm">
                  Part payment: {money(b.payable - rupees(amount), b.currency)} will still be due.
                </p>
              )}
            </div>
          )}
          {mode === 'void' && (
            <p>
              This removes the last desk payment from the records, so its balance is due again. Use
              it only for a payment recorded by mistake. The appointment history keeps what was
              removed and why.
            </p>
          )}
          {mode === 'return' && (
            <p>
              Confirm only after returning the money to the customer. This records the return in the
              ledger.
            </p>
          )}
          {(mode === 'cancel' || mode === 'decline') && (
            <p>
              The appointment will be cancelled. The customer receives the applicable online refund
              or package session back. Any money collected at the desk must be reconciled with the
              customer.
            </p>
          )}
          {['cancel', 'decline', 'collect', 'move', 'void'].includes(mode) && (
            <label className="grid">
              {mode === 'collect' ? 'Receipt reference (optional)' : 'Reason'}
              <input
                className={input}
                value={reason}
                required={mode === 'cancel' || mode === 'decline' || mode === 'void'}
                maxLength={mode === 'collect' ? 80 : 300}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
          )}
          {action.isError && <p role="alert">{errorMessage(action.error)}</p>}
          <div className="flex gap-2">
            <button
              className={button}
              disabled={
                action.isPending ||
                (mode === 'move' && !slot) ||
                (mode === 'collect' && !amountOk) ||
                ((mode === 'cancel' || mode === 'decline' || mode === 'void') && !reason.trim())
              }
            >
              Confirm{' '}
              {mode === 'collect'
                ? 'collection'
                : actions.find((a) => a.path === mode)?.label.toLowerCase()}
            </button>
            <button
              type="button"
              className={button}
              disabled={action.isPending}
              onClick={() => setMode('')}
            >
              Back
            </button>
          </div>
        </form>
      )}
    </article>
  );
}

function CustomerHistory({ spa, user, zone }: { spa: string; user: number; zone: string }) {
  const [open, setOpen] = useState(false);
  const history = useQuery({
    queryKey: ['desk', spa, 'customer', user],
    enabled: open,
    queryFn: async () =>
      (
        await client.get<{
          visits: { reference: string; service: string; startUtc: string; status: string }[];
          packages: { level: string; remaining: number; status: string }[];
          redemptions: { reference: string; usedUtc: string }[];
        }>('/api/owner/spas/' + spa + '/desk/customers/' + user)
      ).data
  });
  return (
    <details onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary className="text-sm text-primary-700">Customer history at this spa</summary>
      {history.isError ? (
        <Notice error={history.error} retry={() => void history.refetch()} />
      ) : history.data ? (
        <div className="rounded-xl bg-stone-50 p-3 text-sm">
          <h4 className="font-semibold">Recent and upcoming appointments</h4>
          {history.data.visits.map((v) => (
            <p key={v.reference}>
              {when(v.startUtc, zone)} · {v.service} · {label(v.status)}
            </p>
          ))}
          {!history.data.visits.length && <p>No visits recorded.</p>}
          <h4 className="font-semibold mt-3">Spa packages</h4>
          {history.data.packages.map((v, i) => (
            <p key={i}>
              {v.level} · {label(v.status)} · {v.remaining} sessions remaining
            </p>
          ))}
          {!history.data.packages.length && <p>No purchases recorded.</p>}
          <h4 className="font-semibold mt-3">Recent redemptions</h4>
          {history.data.redemptions.map((v, i) => (
            <p key={i}>
              {when(v.usedUtc, zone)} · {v.reference || 'Manual redemption'}
            </p>
          ))}
          {!history.data.redemptions.length && <p>No redemptions recorded.</p>}
        </div>
      ) : (
        open && <p>Loading history…</p>
      )}
    </details>
  );
}
