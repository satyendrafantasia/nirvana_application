import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { GIFT_CARD_STATUS, adminGiftCards, issueGiftCard, money, voidGiftCard } from '../../api/rewardsApi';
import Button from '../../components/common/Button';

/** Nirvana staff: find, issue and void gift cards. */
export default function GiftCardsAdmin() {
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [search, setSearch] = useState('');
  const cards = useQuery({ queryKey: ['admin-gift-cards', search], queryFn: () => adminGiftCards(search) });
  const [form, setForm] = useState({ rupees: 500, recipientEmail: '', recipientName: '', spaId: '', note: '' });
  const issue = useMutation({
    mutationFn: () =>
      issueGiftCard({
        amountCents: Math.round(form.rupees * 100),
        recipientEmail: form.recipientEmail.trim(),
        recipientName: form.recipientName.trim() || undefined,
        spaId: form.spaId ? Number(form.spaId) : undefined,
        note: form.note.trim()
      }),
    onSuccess: () => {
      setForm({ rupees: 500, recipientEmail: '', recipientName: '', spaId: '', note: '' });
      void qc.invalidateQueries({ queryKey: ['admin-gift-cards'] });
    }
  });
  const cancel = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => voidGiftCard(id, reason),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin-gift-cards'] })
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    issue.mutate();
  };
  return (
    <div className="mx-auto my-10 max-w-4xl space-y-6 px-4">
      <h1 className="text-3xl font-bold">Gift cards</h1>
      <form onSubmit={submit} className="card grid gap-3 p-6 sm:grid-cols-2" aria-labelledby="issue-heading">
        <h2 id="issue-heading" className="text-xl font-semibold sm:col-span-2">
          Issue a gift card
        </h2>
        <label>
          Amount (₹)
          <input type="number" min={1} className="block w-full rounded-xl border p-2" value={form.rupees}
            onChange={(e) => setForm({ ...form, rupees: Number(e.target.value) })} />
        </label>
        <label>
          Spa ID (blank for any spa)
          <input className="block w-full rounded-xl border p-2" value={form.spaId} inputMode="numeric"
            onChange={(e) => setForm({ ...form, spaId: e.target.value.replace(/\D/g, '') })} />
        </label>
        <label>
          Recipient email
          <input type="email" required className="block w-full rounded-xl border p-2" value={form.recipientEmail}
            onChange={(e) => setForm({ ...form, recipientEmail: e.target.value })} />
        </label>
        <label>
          Recipient name
          <input className="block w-full rounded-xl border p-2" value={form.recipientName}
            onChange={(e) => setForm({ ...form, recipientName: e.target.value })} />
        </label>
        <label className="sm:col-span-2">
          Reason (kept in the audit log)
          <input required maxLength={255} className="block w-full rounded-xl border p-2" value={form.note}
            onChange={(e) => setForm({ ...form, note: e.target.value })} />
        </label>
        {issue.isError && (
          <p role="alert" className="text-rose-700 sm:col-span-2">
            {errorMessage(issue.error)}
          </p>
        )}
        {issue.isSuccess && (
          <p role="status" className="text-emerald-700 sm:col-span-2">
            Issued {issue.data.code} and emailed it to {issue.data.recipientEmail}.
          </p>
        )}
        <div className="sm:col-span-2">
          <Button type="submit" disabled={issue.isPending}>
            {issue.isPending ? 'Issuing…' : 'Issue gift card'}
          </Button>
        </div>
      </form>

      <section className="card space-y-3 p-6" aria-labelledby="cards-heading">
        <h2 id="cards-heading" className="text-xl font-semibold">
          Find gift cards
        </h2>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setSearch(q.trim());
          }}
        >
          <input className="flex-1 rounded-xl border p-2" placeholder="Code or recipient email" value={q} onChange={(e) => setQ(e.target.value)} />
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </form>
        {cards.isError && (
          <p role="alert" className="text-rose-700">
            {errorMessage(cards.error)}
          </p>
        )}
        {cancel.isError && (
          <p role="alert" className="text-rose-700">
            {errorMessage(cancel.error)}
          </p>
        )}
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-slate-500">
              <th className="font-medium">Code</th>
              <th className="font-medium">Recipient</th>
              <th className="font-medium">Where</th>
              <th className="font-medium">Status</th>
              <th className="text-right font-medium">Balance</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {cards.data?.map((c) => (
              <tr key={c.id} className="border-t border-slate-100">
                <td className="py-2 font-mono">{c.code}</td>
                <td className="py-2">{c.recipientEmail}</td>
                <td className="py-2">{c.spaName ?? 'Any spa'}</td>
                <td className="py-2">
                  {GIFT_CARD_STATUS[c.status]} {c.source === 'ADMIN' ? '(issued)' : ''}
                </td>
                <td className="py-2 text-right">
                  {money(c.balanceCents, c.currency)} / {money(c.initialCents, c.currency)}
                </td>
                <td className="py-2 text-right">
                  {['ACTIVE', 'EXHAUSTED', 'EXPIRED'].includes(c.status) && (
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={cancel.isPending}
                      onClick={() => {
                        const reason = window.prompt(`Void ${c.code}? Its balance will be removed. Reason:`);
                        if (reason?.trim()) cancel.mutate({ id: c.id, reason: reason.trim() });
                      }}
                    >
                      Void
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
