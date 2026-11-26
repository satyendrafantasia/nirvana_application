import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { isAdmin } from '../../auth/roles';
import { useAuth } from '../../hooks/useAuth';
import {
  type Payout,
  adminPayouts,
  adminStatement,
  markPayoutPaid,
  money,
  period,
  retryPayout,
  runSettlement,
  setLinkedAccount
} from '../../api/payoutApi';
import { ItemList, StatusBadge } from '../Owner/Payouts';

const input = 'rounded-xl border bg-white px-3 py-2 text-sm';
const button =
  'rounded-xl border border-primary-200 px-3 py-2 text-sm font-semibold text-primary-800 hover:bg-primary-50 disabled:opacity-50';
const FILTERS: [string, string][] = [
  ['FAILED', 'Failed'],
  ['PENDING', 'Waiting'],
  ['IN_PROGRESS', 'In progress'],
  ['COMPLETED', 'Paid'],
  ['', 'All']
];

/** Platform admins: every spa's payouts, the weekly settlement run on demand, and failed transfers to retry. */
export default function AdminPayouts() {
  const { user } = useAuth();
  const [status, setStatus] = useState('FAILED');
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const list = useQuery({
    queryKey: ['admin-payouts', status, page],
    queryFn: () => adminPayouts(status, page),
    enabled: isAdmin(user?.roles)
  });
  const run = useMutation({
    mutationFn: runSettlement,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-payouts'] })
  });
  if (!isAdmin(user?.roles))
    return (
      <div className="mx-auto max-w-xl space-y-3 p-8">
        <h1 className="text-2xl font-bold">Admin access required</h1>
        <Link to="/">Go home</Link>
      </div>
    );
  return (
    <div className="mx-auto max-w-6xl space-y-5 p-4 md:p-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Spa payouts</h1>
          <p className="text-slate-600">
            Online money owed to spas, settled weekly.{' '}
            {list.data &&
              (list.data.automatic
                ? 'Transfers go through Razorpay Route.'
                : 'Automatic transfers are off: pay each spa by bank transfer, then record the reference here.')}
          </p>
        </div>
        <button className={button} disabled={run.isPending} onClick={() => run.mutate()}>
          {run.isPending ? 'Running…' : 'Run settlement now'}
        </button>
      </div>
      {run.data && (
        <p className="card p-3 text-sm" role="status">
          Settled up to {new Date(run.data.cutoff + 'Z').toLocaleDateString('en-IN')}:{' '}
          {run.data.created} new payouts, {run.data.carriedForward} spas carried forward (refunds
          larger than takings), {run.data.sent} transfers tried, {run.data.failed} failed.
        </p>
      )}
      {run.isError && <p role="alert">{errorMessage(run.error)}</p>}
      <nav aria-label="Payout status" className="flex flex-wrap gap-2">
        {FILTERS.map(([value, text]) => (
          <button
            key={value}
            aria-pressed={status === value}
            className={`${button} ${status === value ? 'bg-primary-700 !text-white' : ''}`}
            onClick={() => {
              setStatus(value);
              setPage(0);
            }}
          >
            {text}
          </button>
        ))}
      </nav>
      {list.isPending && <p>Loading…</p>}
      {list.isError && <p role="alert">{errorMessage(list.error)}</p>}
      {list.data && !list.data.items.length && <p className="card p-4">No payouts here.</p>}
      {list.data?.items.map((p) => (
        <AdminPayoutCard key={p.id} payout={p} />
      ))}
      <div className="flex gap-2">
        {page > 0 && (
          <button className={button} onClick={() => setPage(page - 1)}>
            Newer
          </button>
        )}
        {list.data?.hasMore && (
          <button className={button} onClick={() => setPage(page + 1)}>
            Older
          </button>
        )}
      </div>
    </div>
  );
}

export function AdminPayoutCard({ payout: p }: { payout: Payout }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reference, setReference] = useState('');
  const [account, setAccount] = useState('');
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-payouts'] });
  const retry = useMutation({ mutationFn: () => retryPayout(p.id), onSuccess: refresh });
  const paid = useMutation({
    mutationFn: () => markPayoutPaid(p.id, reference.trim()),
    onSuccess: refresh
  });
  const link = useMutation({
    mutationFn: () => setLinkedAccount(p.spaId, account.trim()),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-payout', p.id] })
  });
  const detail = useQuery({
    queryKey: ['admin-payout', p.id],
    queryFn: () => adminStatement(p.id),
    enabled: open
  });
  const unpaid = p.status !== 'COMPLETED';
  return (
    <article className="card space-y-3 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold">
            {p.spaName} <span className="text-sm font-normal text-slate-500">#{p.spaId}</span>
          </h2>
          <p className="text-sm text-slate-600">
            {period(p)} · {p.reference}
          </p>
        </div>
        <div className="text-right">
          <p className="text-lg font-bold">{money(p.net, p.currency)}</p>
          <StatusBadge payout={p} />
        </div>
      </div>
      <p className="text-sm">
        {money(p.gross, p.currency)} online − {money(p.refunds, p.currency)} refunds −{' '}
        {money(p.commission, p.currency)} commission ({p.commissionPct}%)
        {p.transferId && (
          <>
            {' '}
            · Transfer <span className="font-mono">{p.transferId}</span>
          </>
        )}
        {p.bankAccount && <> · To {p.bankAccount}</>}
        {p.attempts > 0 && (
          <>
            {' '}
            · {p.attempts} attempt{p.attempts > 1 ? 's' : ''}
          </>
        )}
      </p>
      {unpaid && p.failureReason && (
        <p className={p.status === 'FAILED' ? 'text-sm text-red-700' : 'text-sm text-slate-600'}>
          {p.failureReason}
        </p>
      )}
      {unpaid && (
        <div className="flex flex-wrap items-end gap-2">
          <button className={button} disabled={retry.isPending} onClick={() => retry.mutate()}>
            Retry transfer
          </button>
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              paid.mutate();
            }}
          >
            <label className="grid text-sm">
              Paid outside Nirvana? Bank or Razorpay reference
              <input
                className={input}
                value={reference}
                maxLength={64}
                onChange={(e) => setReference(e.target.value)}
              />
            </label>
            <button className={button} disabled={paid.isPending || reference.trim().length < 4}>
              Record as paid
            </button>
          </form>
        </div>
      )}
      {retry.isError && <p role="alert">{errorMessage(retry.error)}</p>}
      {paid.isError && <p role="alert">{errorMessage(paid.error)}</p>}
      <button className="text-sm underline" aria-expanded={open} onClick={() => setOpen(!open)}>
        {open ? 'Hide statement' : 'Statement and bank account'}
      </button>
      {open && detail.isPending && <p>Loading…</p>}
      {open && detail.isError && <p role="alert">{errorMessage(detail.error)}</p>}
      {open && detail.data && (
        <div className="space-y-3 rounded-xl bg-slate-50 p-3">
          {detail.data.account ? (
            <p className="text-sm">
              {detail.data.account.accountHolder} · {detail.data.account.accountMasked} ·{' '}
              {detail.data.account.ifsc} · Route:{' '}
              {detail.data.account.routeAccountId || detail.data.account.routeStatus}
              {detail.data.account.routeError && <> ({detail.data.account.routeError})</>}
            </p>
          ) : (
            <p className="text-sm">The spa hasn&apos;t added bank details yet.</p>
          )}
          {detail.data.account && (
            <form
              className="flex flex-wrap items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                link.mutate();
              }}
            >
              <label className="grid text-sm">
                Razorpay linked account made on the dashboard
                <input
                  className={input}
                  placeholder="acc_…"
                  value={account}
                  onChange={(e) => setAccount(e.target.value)}
                />
              </label>
              <button className={button} disabled={link.isPending || !account.trim()}>
                Set linked account
              </button>
            </form>
          )}
          {link.isError && <p role="alert">{errorMessage(link.error)}</p>}
          <ItemList items={detail.data.items} currency={p.currency} />
        </div>
      )}
    </article>
  );
}
