import { useState, FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { usePolicy } from './AppointmentOperations';

type Line = {
  reference: string;
  receipt?: string;
  source: string;
  method: string;
  collected: number;
  refunded: number;
  tip: number;
};
type Details = {
  lines?: Line[];
  reference?: string;
  transferDate?: string;
  reason?: string;
  gross?: number;
  commission?: number;
  transferred?: number;
  difference?: number;
  refunds?: number;
  adjustment?: number;
  tips?: number;
  differences?: Record<string, number>;
  counted?: Record<string, number>;
  expected?: number | Record<string, number>;
};
type Report = {
  id: number;
  date: string;
  currency: string;
  state: string;
  revision: number;
  details: string | Details;
};
const label: Record<string, string> = {
  cash: 'Cash counted',
  card: 'Card total',
  upi: 'UPI total',
  transferred: 'Transferred amount',
  adjustment: 'Adjustment'
};
export default function FinancialReports({
  spa,
  owner,
  finance
}: {
  spa: string;
  owner: boolean;
  finance: boolean;
}) {
  const [kind, setKind] = useState<'SHIFT' | 'PAYOUT'>('SHIFT');
  const policy = usePolicy(spa);
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: policy.data?.timezone || 'Asia/Kolkata',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date());
  const [day, setDay] = useState(''),
    [currency, setCurrency] = useState('INR'),
    [reason, setReason] = useState('');
  const date = day || today;
  const path = '/api/owner/spas/' + spa + '/finance';
  const qc = useQueryClient();
  const rows = useQuery({
    queryKey: [path, kind],
    enabled: owner,
    queryFn: async () => (await client.get<Report[]>(path + '/' + kind)).data
  });
  const missing = useQuery({
    queryKey: [path, 'unmatched', date, currency],
    enabled: owner && kind === 'PAYOUT',
    queryFn: async () =>
      (await client.get<string[]>(path + '/unmatched', { params: { through: date, currency } }))
        .data
  });
  const change = useMutation({
    mutationFn: ({ suffix, data }: { suffix: string; data: unknown }) =>
      client.post(path + '/' + suffix, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [path] })
  });
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    const fields: Record<string, number> = {};
    for (const key of Object.keys(label)) fields[key] = Math.round(Number(f.get(key) || 0) * 100);
    change.mutate({
      suffix: kind,
      data: {
        date,
        currency,
        reason,
        ...fields,
        reference: f.get('reference'),
        transferDate: f.get('transferDate'),
        periodStart: f.get('periodStart')
      }
    });
  };
  const money = (n: number, c = currency) =>
    new Intl.NumberFormat(undefined, { style: 'currency', currency: c }).format(n / 100);
  const latest = new Set<string>();
  return (
    <section className="operations-form card p-5 space-y-4">
      <h2>Financial reports</h2>
      {owner && (
        <label>
          Report{' '}
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value as typeof kind);
              change.reset();
            }}
          >
            <option value="SHIFT">Daily shift close</option>
            <option value="PAYOUT">Payout statements</option>
          </select>
        </label>
      )}
      <form key={kind} onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <label>
          Business date
          <input
            type="date"
            required
            value={date}
            max={today}
            onChange={(e) => setDay(e.target.value)}
          />
        </label>
        <label>
          Currency
          <input
            required
            maxLength={3}
            pattern="[A-Z]{3}"
            value={currency}
            onChange={(e) => setCurrency(e.target.value.toUpperCase())}
          />
        </label>
        {(kind === 'SHIFT' || finance) && (
          <>
            {(kind === 'SHIFT' ? ['cash', 'upi', 'card'] : ['transferred', 'adjustment']).map(
              (k) => (
                <label key={k}>
                  {label[k]} ({currency})
                  <input
                    name={k}
                    type="number"
                    step=".01"
                    min={k === 'adjustment' ? undefined : 0}
                    required
                    defaultValue={0}
                  />
                </label>
              )
            )}
            {kind === 'PAYOUT' && (
              <>
                <label>
                  Settlement period start
                  <input name="periodStart" type="date" max={date} required />
                </label>
                <label>
                  Bank transfer reference
                  <input name="reference" required maxLength={100} />
                </label>
                <label>
                  Transfer date
                  <input name="transferDate" required type="date" max={today} />
                </label>
              </>
            )}
            <label>
              Variance or correction reason
              <input maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>
            <button className="btn-primary" disabled={change.isPending}>
              {kind === 'SHIFT' ? 'Submit daily close' : 'Record completed transfer'}
            </button>
          </>
        )}
      </form>
      {kind === 'PAYOUT' && missing.data && (
        <p>
          Unmatched business dates in the 31 days ending {date}: {missing.data.join(', ') || 'None'}
        </p>
      )}
      {change.isSuccess && <p role="status">Report updated.</p>}
      {(change.isError || rows.isError || missing.isError) && (
        <p role="alert">{errorMessage(change.error || rows.error || missing.error)}</p>
      )}
      {rows.data?.map((r) => {
        const d: Details = typeof r.details === 'string' ? JSON.parse(r.details) : r.details;
        const key = r.date + r.currency;
        const current = !latest.has(key);
        latest.add(key);
        return (
          <details key={r.id} className="rounded border p-3">
            <summary>
              {r.date} · {r.currency} · {r.state} · Revision {r.revision}
            </summary>
            {d.reference && (
              <p>
                Bank transfer {d.reference} · {d.transferDate}
              </p>
            )}
            {Object.entries(d)
              .filter(
                ([k]) =>
                  !['lines', 'reason', 'reference', 'transferDate', 'periodStart'].includes(k)
              )
              .map(([k, v]) => (
                <p key={k}>
                  {k}:{' '}
                  {typeof v === 'number'
                    ? money(v, r.currency)
                    : Object.entries(v)
                        .map(([method, value]) => method + ' ' + money(Number(value), r.currency))
                        .join(' · ')}
                </p>
              ))}
            {d.reason && <p>Reason: {d.reason}</p>}
            {d.lines?.map((line, i) => (
              <p key={i}>
                {line.reference} · {line.source} · {line.method} ·{' '}
                {money(line.collected - line.refunded, r.currency)} · Tip{' '}
                {money(line.tip, r.currency)}
              </p>
            ))}
            {current && owner && kind === 'SHIFT' && r.state === 'SUBMITTED' && (
              <button
                className="underline mr-3"
                onClick={() => change.mutate({ suffix: 'shift/' + r.id + '/approve', data: {} })}
              >
                Approve close
              </button>
            )}
            {current && owner && kind === 'SHIFT' && r.state !== 'REOPENED' && (
              <button
                className="underline"
                disabled={!reason.trim()}
                onClick={() =>
                  change.mutate({ suffix: 'shift/' + r.id + '/reopen', data: { reason } })
                }
              >
                Reopen with correction reason
              </button>
            )}
          </details>
        );
      })}
    </section>
  );
}
