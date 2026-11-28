import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { recoveryStats } from '../../api/checkoutRecoveryApi';
import { money } from '../../api/rewardsApi';

const pct = (x: number) => `${Math.round(x * 100)}%`;

/** Reminders for unfinished checkouts: sent, clicked and the bookings they brought back. All spas when {@code spa} is omitted. */
export default function CheckoutRecoveryStats({ spa }: { spa?: number | string }) {
  const [range, setRange] = useState<{ from?: string; to?: string }>({});
  const stats = useQuery({
    queryKey: ['checkout-recovery-stats', spa ?? 'all', range],
    queryFn: () => recoveryStats(spa, range.from, range.to)
  });
  const s = stats.data;
  return (
    <section className="card space-y-3 p-6" aria-labelledby="recovery-heading">
      <h2 id="recovery-heading" className="text-xl font-semibold">Checkout reminders</h2>
      <p className="text-sm text-slate-600">
        Guests who chose a time but didn't finish paying get one reminder. Recovered means they booked {spa === undefined ? 'at the same spa' : 'here'} within 7 days.
      </p>
      <div className="flex flex-wrap gap-3 text-sm">
        <label>
          From
          <input type="date" className="block rounded-lg border p-1" value={range.from ?? s?.from?.slice(0, 10) ?? ''}
            onChange={(e) => setRange((x) => ({ ...x, from: e.target.value || undefined }))} />
        </label>
        <label>
          To
          <input type="date" className="block rounded-lg border p-1" value={range.to ?? ''}
            onChange={(e) => setRange((x) => ({ ...x, to: e.target.value || undefined }))} />
        </label>
      </div>
      {stats.isPending && <p role="status">Loading reminders…</p>}
      {stats.isError && <p role="alert" className="text-rose-700">{errorMessage(stats.error)}</p>}
      {s && (
        <dl className="grid gap-3 sm:grid-cols-4">
          <div className="rounded-xl bg-slate-50 p-3">
            <dt className="text-sm text-slate-600">Reminders sent</dt>
            <dd className="text-2xl font-semibold">{s.sent}</dd>
            {s.failed > 0 && <dd className="text-xs text-slate-500">{s.failed} could not be delivered</dd>}
          </div>
          <div className="rounded-xl bg-slate-50 p-3">
            <dt className="text-sm text-slate-600">Clicked</dt>
            <dd className="text-2xl font-semibold">{s.clicked}</dd>
            <dd className="text-xs text-slate-500">{pct(s.clickRate)} of sent</dd>
          </div>
          <div className="rounded-xl bg-slate-50 p-3">
            <dt className="text-sm text-slate-600">Bookings recovered</dt>
            <dd className="text-2xl font-semibold">{s.converted}</dd>
            <dd className="text-xs text-slate-500">{pct(s.conversionRate)} of sent</dd>
          </div>
          <div className="rounded-xl bg-slate-50 p-3">
            <dt className="text-sm text-slate-600">Recovered revenue</dt>
            <dd className="text-2xl font-semibold">{money(s.recoveredCents)}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}
