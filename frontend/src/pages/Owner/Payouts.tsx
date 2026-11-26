import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import {
  BUSINESS_TYPES,
  type Payout,
  type PayoutAccount,
  type Upcoming,
  day,
  kindLabel,
  money,
  ownerPayouts,
  ownerStatement,
  period,
  saveBankDetails,
  statusLabel,
  validAccount,
  validIfsc
} from '../../api/payoutApi';

const input = 'rounded-xl border bg-white px-3 py-2 text-sm';
const button =
  'rounded-xl border border-primary-200 px-3 py-2 text-sm font-semibold text-primary-800 hover:bg-primary-50 disabled:opacity-50';
const tone: Record<Payout['status'], string> = {
  COMPLETED: 'bg-emerald-50 text-emerald-800',
  PENDING: 'bg-amber-50 text-amber-800',
  IN_PROGRESS: 'bg-sky-50 text-sky-800',
  FAILED: 'bg-red-50 text-red-800'
};

export function StatusBadge({ payout }: { payout: Payout }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${tone[payout.status]}`}>
      {statusLabel(payout)}
    </span>
  );
}

/**
 * The owner's payouts: where Nirvana pays the spa, what's waiting for the next payout, and a statement
 * for each payout. Only online money is here; money taken at the desk is already the spa's.
 */
export default function Payouts({ spaId }: { spaId: string }) {
  const [page, setPage] = useState(0);
  const data = useQuery({
    queryKey: ['payouts', spaId, page],
    queryFn: () => ownerPayouts(spaId, page)
  });
  return (
    <div className="space-y-5">
      <section className="card p-5 space-y-2">
        <h2 className="text-xl font-semibold">Payouts</h2>
        <p className="text-sm text-slate-600">
          Guests who pay online pay Nirvana. Each week Nirvana pays you that money, less online
          refunds and the platform commission. Money you take at the front desk is already yours and
          isn&apos;t part of a payout.
        </p>
        {data.isPending && <p>Loading payouts…</p>}
        {data.isError && <p role="alert">{errorMessage(data.error)}</p>}
        {data.data?.upcoming.map((u) => (
          <UpcomingLine key={u.currency} upcoming={u} />
        ))}
        {data.data && !data.data.upcoming.length && (
          <p className="text-sm">Nothing waiting for the next payout.</p>
        )}
      </section>
      {data.data && (
        <BankDetailsForm
          spaId={spaId}
          account={data.data.account}
          automatic={data.data.automatic}
        />
      )}
      {data.data && (
        <Statements
          spaId={spaId}
          items={data.data.items}
          page={page}
          hasMore={data.data.hasMore}
          setPage={setPage}
        />
      )}
    </div>
  );
}

export function UpcomingLine({ upcoming: u }: { upcoming: Upcoming }) {
  return (
    <p className="text-sm">
      Next payout so far: <strong>{money(u.net, u.currency)}</strong> ({money(u.gross, u.currency)}{' '}
      online, less {money(u.refunds, u.currency)} refunds and {u.commissionPct}% commission of{' '}
      {money(u.commission, u.currency)}). It may still change before the weekly run.
    </p>
  );
}

export function BankDetailsForm({
  spaId,
  account,
  automatic
}: {
  spaId: string;
  account: PayoutAccount | null;
  automatic: boolean;
}) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(!account);
  const [form, setForm] = useState({
    accountHolder: account?.accountHolder ?? '',
    ifsc: account?.ifsc ?? '',
    accountNumber: '',
    confirm: '',
    businessType: account?.businessType ?? 'proprietorship'
  });
  const save = useMutation({
    mutationFn: () => saveBankDetails(spaId, form),
    onSuccess: async () => {
      setEditing(false);
      setForm((f) => ({ ...f, accountNumber: '', confirm: '' }));
      await qc.invalidateQueries({ queryKey: ['payouts', spaId] });
    }
  });
  const problem = !form.accountHolder.trim()
    ? 'Enter the account holder’s name'
    : !validIfsc(form.ifsc)
      ? 'Enter a valid 11-character IFSC, like HDFC0001234'
      : !validAccount(form.accountNumber)
        ? 'Enter the account number: 9 to 18 digits'
        : form.accountNumber.replace(/\D/g, '') !== form.confirm.replace(/\D/g, '')
          ? 'The account numbers don’t match'
          : null;
  return (
    <section className="card p-5 space-y-3">
      <h3 className="text-lg font-semibold">Bank account for payouts</h3>
      {account && !editing && (
        <div className="space-y-1 text-sm">
          <p>
            <strong>{account.accountHolder}</strong> · Account {account.accountMasked} · IFSC{' '}
            {account.ifsc}
          </p>
          {automatic && account.routeStatus === 'LINKED' && (
            <p className="text-emerald-700">Ready for automatic payouts.</p>
          )}
          {automatic && account.routeStatus === 'FAILED' && (
            <p role="alert" className="text-red-700">
              We couldn&apos;t set up payouts to this account: {account.routeError}. Check the
              details and save them again, or contact Nirvana support.
            </p>
          )}
          {!automatic && (
            <p className="text-slate-600">Nirvana pays this account by bank transfer.</p>
          )}
          <button className={button} onClick={() => setEditing(true)}>
            Change bank account
          </button>
        </div>
      )}
      {editing && (
        <form
          className="grid gap-3 md:grid-cols-2"
          autoComplete="off"
          onSubmit={(e) => {
            e.preventDefault();
            if (!problem) save.mutate();
          }}
        >
          <p className="md:col-span-2 text-sm text-slate-600">
            We keep only the last four digits of the account number. The full number is sent once to
            our payment partner, Razorpay, to set up payouts.
          </p>
          <label className="grid gap-1 text-sm">
            Account holder&apos;s name (as the bank has it)
            <input
              className={input}
              value={form.accountHolder}
              maxLength={120}
              required
              onChange={(e) => setForm({ ...form, accountHolder: e.target.value })}
            />
          </label>
          <label className="grid gap-1 text-sm">
            IFSC
            <input
              className={input + ' uppercase'}
              value={form.ifsc}
              maxLength={11}
              required
              onChange={(e) => setForm({ ...form, ifsc: e.target.value.toUpperCase() })}
            />
          </label>
          <label className="grid gap-1 text-sm">
            Account number
            <input
              className={input}
              inputMode="numeric"
              value={form.accountNumber}
              maxLength={22}
              required
              onChange={(e) => setForm({ ...form, accountNumber: e.target.value })}
            />
          </label>
          <label className="grid gap-1 text-sm">
            Account number again
            <input
              className={input}
              inputMode="numeric"
              value={form.confirm}
              maxLength={22}
              required
              onPaste={(e) => e.preventDefault()}
              onChange={(e) => setForm({ ...form, confirm: e.target.value })}
            />
          </label>
          <label className="grid gap-1 text-sm">
            Type of business
            <select
              className={input}
              value={form.businessType}
              onChange={(e) => setForm({ ...form, businessType: e.target.value })}
            >
              {BUSINESS_TYPES.map(([value, text]) => (
                <option key={value} value={value}>
                  {text}
                </option>
              ))}
            </select>
          </label>
          <div className="md:col-span-2 flex flex-wrap items-center gap-2">
            <button
              className="rounded-xl bg-primary-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
              disabled={save.isPending || !!problem}
            >
              Save bank account
            </button>
            {account && (
              <button type="button" className={button} onClick={() => setEditing(false)}>
                Cancel
              </button>
            )}
            {problem && (form.accountNumber || form.ifsc) && (
              <span className="text-sm text-slate-600">{problem}</span>
            )}
          </div>
          {save.isError && (
            <p role="alert" className="md:col-span-2">
              {errorMessage(save.error)}
            </p>
          )}
        </form>
      )}
    </section>
  );
}

function Statements({
  spaId,
  items,
  page,
  hasMore,
  setPage
}: {
  spaId: string;
  items: Payout[];
  page: number;
  hasMore: boolean;
  setPage: (n: number) => void;
}) {
  const [open, setOpen] = useState<number | null>(null);
  return (
    <section className="card p-5 space-y-3">
      <h3 className="text-lg font-semibold">Payout statements</h3>
      {!items.length && (
        <p className="text-sm">No payouts yet. The first one comes after the weekly run.</p>
      )}
      {!!items.length && (
        <StatementTable items={items} open={open} setOpen={setOpen} spaId={spaId} />
      )}
      <div className="flex gap-2">
        {page > 0 && (
          <button className={button} onClick={() => setPage(page - 1)}>
            Newer
          </button>
        )}
        {hasMore && (
          <button className={button} onClick={() => setPage(page + 1)}>
            Older
          </button>
        )}
      </div>
    </section>
  );
}

export function StatementTable({
  items,
  open,
  setOpen,
  spaId
}: {
  items: Payout[];
  open: number | null;
  setOpen: (id: number | null) => void;
  spaId: string;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="text-left text-slate-600">
          <tr>
            <th className="py-2 pr-3">Period</th>
            <th className="pr-3 text-right">Online</th>
            <th className="pr-3 text-right">Refunds</th>
            <th className="pr-3 text-right">Commission</th>
            <th className="pr-3 text-right">Payout</th>
            <th className="pr-3">Status</th>
            <th className="pr-3">Transfer</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {items.map((p) => (
            <StatementRow
              key={p.id}
              payout={p}
              open={open === p.id}
              spaId={spaId}
              toggle={() => setOpen(open === p.id ? null : p.id)}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatementRow({
  payout: p,
  open,
  toggle,
  spaId
}: {
  payout: Payout;
  open: boolean;
  toggle: () => void;
  spaId: string;
}) {
  return (
    <>
      <tr className="border-t align-top">
        <td className="py-2 pr-3">
          {period(p)}
          <div className="text-xs text-slate-500">{p.reference}</div>
        </td>
        <td className="pr-3 text-right">{money(p.gross, p.currency)}</td>
        <td className="pr-3 text-right">−{money(p.refunds, p.currency)}</td>
        <td className="pr-3 text-right">
          −{money(p.commission, p.currency)}
          <div className="text-xs text-slate-500">{p.commissionPct}%</div>
        </td>
        <td className="pr-3 text-right font-semibold">{money(p.net, p.currency)}</td>
        <td className="pr-3">
          <StatusBadge payout={p} />
          {p.status !== 'COMPLETED' && p.failureReason && (
            <div className="text-xs text-slate-500">{p.failureReason}</div>
          )}
          {p.paidAt && <div className="text-xs text-slate-500">Paid {day(p.paidAt)}</div>}
        </td>
        <td className="pr-3 font-mono text-xs">{p.transferId || '—'}</td>
        <td>
          {p.periodEnd && (
            <button className="text-sm underline" onClick={toggle} aria-expanded={open}>
              {open ? 'Hide' : 'Details'}
            </button>
          )}
        </td>
      </tr>
      {open && (
        <tr>
          <td colSpan={8} className="bg-slate-50 p-3">
            <StatementItems spaId={spaId} id={p.id} currency={p.currency} />
          </td>
        </tr>
      )}
    </>
  );
}

function StatementItems({ spaId, id, currency }: { spaId: string; id: number; currency: string }) {
  const statement = useQuery({
    queryKey: ['payout', spaId, id],
    queryFn: () => ownerStatement(spaId, id)
  });
  if (statement.isPending) return <p>Loading…</p>;
  if (statement.isError) return <p role="alert">{errorMessage(statement.error)}</p>;
  return <ItemList items={statement.data.items} currency={currency} />;
}

export function ItemList({
  items,
  currency
}: {
  items: {
    kind: Parameters<typeof kindLabel>[0];
    reference?: string;
    amount: number;
    occurredAt: string;
  }[];
  currency: string;
}) {
  if (!items.length) return <p className="text-sm">No payments in this payout.</p>;
  return (
    <ul className="space-y-1 text-sm">
      {items.map((i, n) => (
        <li key={n} className="flex flex-wrap justify-between gap-2">
          <span>
            {day(i.occurredAt)} · {kindLabel(i.kind)} · {i.reference}
          </span>
          <span className={i.amount < 0 ? 'text-red-700' : ''}>{money(i.amount, currency)}</span>
        </li>
      ))}
    </ul>
  );
}
