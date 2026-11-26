import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { money, ownerGiftCards } from '../../api/rewardsApi';

/** Gift cards sold for this spa and gift card balance spent here, for a date range. */
export default function GiftCardReport({ spa }: { spa: number | string }) {
  const [range, setRange] = useState<{ from?: string; to?: string }>({});
  const report = useQuery({
    queryKey: ['owner-gift-cards', spa, range],
    queryFn: () => ownerGiftCards(spa, range.from, range.to)
  });
  const r = report.data;
  return (
    <section className="card space-y-3 p-6" aria-labelledby="gift-report-heading">
      <h2 id="gift-report-heading" className="text-xl font-semibold">
        Gift cards
      </h2>
      <div className="flex flex-wrap gap-3 text-sm">
        <label>
          From
          <input type="date" className="block rounded-lg border p-1" value={range.from ?? r?.from ?? ''}
            onChange={(e) => setRange((x) => ({ ...x, from: e.target.value || undefined }))} />
        </label>
        <label>
          To
          <input type="date" className="block rounded-lg border p-1" value={range.to ?? r?.to ?? ''}
            onChange={(e) => setRange((x) => ({ ...x, to: e.target.value || undefined }))} />
        </label>
      </div>
      {report.isPending && <p role="status">Loading gift cards…</p>}
      {report.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(report.error)}
        </p>
      )}
      {r && (
        <>
          <dl className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl bg-slate-50 p-3">
              <dt className="text-sm text-slate-600">Sold for your spa</dt>
              <dd className="text-lg font-semibold">
                {money(Number(r.sales.soldCents), r.currency)} <span className="text-sm font-normal">({r.sales.cards} cards)</span>
              </dd>
            </div>
            <div className="rounded-xl bg-slate-50 p-3">
              <dt className="text-sm text-slate-600">Spent at your spa</dt>
              <dd className="text-lg font-semibold">
                {money(Number(r.redemptions.redeemedCents), r.currency)}{' '}
                <span className="text-sm font-normal">({r.redemptions.bookings} bookings)</span>
              </dd>
            </div>
            <div className="rounded-xl bg-slate-50 p-3">
              <dt className="text-sm text-slate-600">Unspent on your spa's cards</dt>
              <dd className="text-lg font-semibold">{money(Number(r.sales.outstandingCents), r.currency)}</dd>
            </div>
          </dl>
          <p className="text-xs text-slate-500">
            Gift cards are paid to Nirvana when they are bought. What a guest pays with one at your spa shows here.
          </p>
          {r.recent.length > 0 && (
            <table className="w-full text-sm">
              <caption className="sr-only">Recent gift card use</caption>
              <thead>
                <tr className="text-left text-slate-500">
                  <th className="font-medium">Date</th>
                  <th className="font-medium">Booking</th>
                  <th className="font-medium">Card</th>
                  <th className="text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody>
                {r.recent.map((t, i) => (
                  <tr key={i} className="border-t border-slate-100">
                    <td className="py-1">{new Date(t.createdAt).toLocaleDateString('en-IN')}</td>
                    <td className="py-1">{t.bookingReference ?? '—'}</td>
                    <td className="py-1">
                      {t.card} · {t.scope}
                    </td>
                    <td className="py-1 text-right">
                      {t.type === 'REVERSAL' ? 'Returned ' : ''}
                      {money(Math.abs(Number(t.amountCents)), r.currency)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  );
}
