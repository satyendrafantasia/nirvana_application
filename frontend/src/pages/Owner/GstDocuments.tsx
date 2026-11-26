import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { commissionInvoice, GstDocumentView, openGstDocument, rupees, spaGstDocuments } from '../../api/gstApi';

/** "2026-11" for the month containing the date, India time. */
const monthOf = (d: Date) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit' }).format(d);

const lastDay = (month: string) => {
  const [y, m] = month.split('-').map(Number);
  return `${month}-${String(new Date(Date.UTC(y, m, 0)).getUTCDate()).padStart(2, '0')}`;
};

/**
 * The spa's GST documents for a month: the invoices and credit notes it issued to guests, and
 * Nirvana's commission invoice to the spa. Each opens as a printable page.
 */
export default function GstDocuments({ spa }: { spa: string }) {
  const current = monthOf(new Date());
  const [month, setMonth] = useState(current);
  const qc = useQueryClient();
  const docs = useQuery({
    queryKey: ['gst-documents', spa, month],
    queryFn: () => spaGstDocuments(spa, `${month}-01`, lastDay(month))
  });
  const open = useMutation({ mutationFn: (d: GstDocumentView) => openGstDocument(d) });
  const commission = useMutation({
    mutationFn: async () => {
      const doc = await commissionInvoice(spa, month);
      if (!doc) throw new Error(`No commission is due for ${month}: there were no online collections.`);
      await openGstDocument(doc);
    },
    onSettled: () => void qc.invalidateQueries({ queryKey: ['gst-documents', spa] })
  });
  const guestDocs = docs.data?.filter((d) => d.docType !== 'COMMISSION_INVOICE') || [];
  const total = (type: 'add' | 'credit', key: 'taxableCents' | 'cgstCents' | 'sgstCents' | 'igstCents' | 'totalCents') =>
    guestDocs
      .filter((d) => (type === 'credit') === (d.docType === 'CREDIT_NOTE'))
      .reduce((sum, d) => sum + d[key], 0);
  const error = docs.error || open.error || commission.error;
  return (
    <section className="rounded-2xl bg-white border border-slate-100 p-6 space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">GST invoices</h2>
          <p className="text-sm text-slate-600">
            Your spa issues each guest's invoice (a bill of supply if you have no GSTIN on file), and a credit note
            for each refund. Nirvana invoices you monthly for its commission.
          </p>
        </div>
        <label className="text-sm">
          Month{' '}
          <input
            type="month"
            className="border rounded px-2 py-1"
            value={month}
            max={current}
            onChange={(e) => e.target.value && setMonth(e.target.value)}
          />
        </label>
      </div>
      {error && (
        <p role="alert" className="text-rose-700">
          {errorMessage(error)}
        </p>
      )}
      {month < current && (
        <button
          type="button"
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
          disabled={commission.isPending}
          onClick={() => commission.mutate()}
        >
          {commission.isPending ? 'Preparing…' : `Nirvana's commission invoice for ${month}`}
        </button>
      )}
      {docs.isLoading && <p>Loading invoices…</p>}
      {docs.data && guestDocs.length === 0 && <p className="text-slate-600">No invoices issued this month.</p>}
      {guestDocs.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-500">
                <th className="py-1 pr-3">Date</th>
                <th className="pr-3">Document</th>
                <th className="pr-3">Guest</th>
                <th className="pr-3">For</th>
                <th className="pr-3 text-right">Taxable</th>
                <th className="pr-3 text-right">GST</th>
                <th className="pr-3 text-right">Total</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {guestDocs.map((d) => {
                const sign = d.docType === 'CREDIT_NOTE' ? -1 : 1;
                return (
                  <tr key={d.id} className="border-t border-slate-100">
                    <td className="py-1.5 pr-3 whitespace-nowrap">{d.issueDate}</td>
                    <td className="pr-3 whitespace-nowrap">
                      {d.title} {d.documentNumber}
                    </td>
                    <td className="pr-3">{d.recipientName}</td>
                    <td className="pr-3">{d.description}</td>
                    <td className="pr-3 text-right">{rupees(sign * d.taxableCents, d.currency)}</td>
                    <td className="pr-3 text-right">{rupees(sign * (d.cgstCents + d.sgstCents + d.igstCents), d.currency)}</td>
                    <td className="pr-3 text-right">{rupees(sign * d.totalCents, d.currency)}</td>
                    <td>
                      <button type="button" className="underline" onClick={() => open.mutate(d)}>
                        Open
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-slate-300 font-semibold">
                <td className="py-1.5" colSpan={4}>
                  Net for the month (invoices less credit notes)
                </td>
                <td className="pr-3 text-right">{rupees(total('add', 'taxableCents') - total('credit', 'taxableCents'))}</td>
                <td className="pr-3 text-right">
                  {rupees(
                    ['cgstCents', 'sgstCents', 'igstCents'].reduce(
                      (s, k) => s + total('add', k as 'cgstCents') - total('credit', k as 'cgstCents'),
                      0
                    )
                  )}
                </td>
                <td className="pr-3 text-right">{rupees(total('add', 'totalCents') - total('credit', 'totalCents'))}</td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      {docs.data
        ?.filter((d) => d.docType === 'COMMISSION_INVOICE')
        .map((d) => (
          <p key={d.id} className="text-sm">
            Commission invoice {d.documentNumber} ({d.periodStart} to {d.periodEnd}): {rupees(d.totalCents)} including GST.{' '}
            <button type="button" className="underline" onClick={() => open.mutate(d)}>
              Open
            </button>
          </p>
        ))}
    </section>
  );
}
