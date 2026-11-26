import { FormEvent, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import {
  GIFT_CARD_STATUS,
  GiftCard,
  giftCardBalance,
  money,
  myGiftCards,
  payForGiftCard,
  resumeGiftCard,
  startGiftCard
} from '../../api/rewardsApi';
import Button from '../../components/common/Button';

const AMOUNTS = [500, 1000, 2000, 5000];
const MIN_RUPEES = 500;
const MAX_RUPEES = 10000;
const date = (iso?: string) =>
  iso ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';

/** Checks what a buyer typed before it goes to the server. */
export function giftCardProblem(rupees: number, email: string): string | undefined {
  if (!Number.isInteger(rupees) || rupees < MIN_RUPEES || rupees > MAX_RUPEES)
    return `Choose a whole amount between ₹${MIN_RUPEES.toLocaleString('en-IN')} and ₹${MAX_RUPEES.toLocaleString('en-IN')}.`;
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) return "Enter the recipient's email address.";
  return undefined;
}

export default function GiftCards() {
  const [params] = useSearchParams();
  const spaId = Number(params.get('spa')) || undefined;
  const spaName = params.get('name') || undefined;
  return (
    <div className="mx-auto my-10 max-w-2xl space-y-6 px-4">
      <h1 className="text-3xl font-bold">Gift cards</h1>
      <Buy spaId={spaId} spaName={spaName} />
      <Mine />
      <CheckBalance />
    </div>
  );
}

function Buy({ spaId, spaName }: { spaId?: number; spaName?: string }) {
  const qc = useQueryClient();
  const [amount, setAmount] = useState(1000);
  const [forSpa, setForSpa] = useState(!!spaId);
  const [recipientName, setRecipientName] = useState('');
  const [recipientEmail, setRecipientEmail] = useState('');
  const [message, setMessage] = useState('');
  const [problem, setProblem] = useState<string>();
  const buy = useMutation({
    mutationFn: async () =>
      payForGiftCard(
        await startGiftCard({
          amountCents: amount * 100,
          spaId: forSpa ? spaId : undefined,
          recipientName: recipientName.trim() || undefined,
          recipientEmail: recipientEmail.trim(),
          message: message.trim() || undefined
        })
      ),
    onSettled: () => void qc.invalidateQueries({ queryKey: ['my-gift-cards'] }),
    onSuccess: () => {
      setRecipientName('');
      setRecipientEmail('');
      setMessage('');
    }
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const p = giftCardProblem(amount, recipientEmail);
    setProblem(p);
    if (!p) buy.mutate();
  };
  return (
    <form onSubmit={submit} className="card space-y-4 p-6" aria-labelledby="buy-heading">
      <h2 id="buy-heading" className="text-xl font-semibold">
        Send a gift card
      </h2>
      <p className="text-sm text-slate-600">
        The recipient gets a code by email to use at checkout, over as many bookings as they like until the balance runs
        out. Gift cards are valid for one year.
      </p>
      <fieldset className="space-y-2" disabled={buy.isPending}>
        <legend className="font-medium">Amount</legend>
        <div className="flex flex-wrap gap-2">
          {AMOUNTS.map((a) => (
            <button
              key={a}
              type="button"
              aria-pressed={amount === a}
              onClick={() => setAmount(a)}
              className={`rounded-xl border-2 px-4 py-2 ${amount === a ? 'border-primary-700 bg-primary-50' : 'border-slate-200 bg-white'}`}
            >
              ₹{a.toLocaleString('en-IN')}
            </button>
          ))}
          <label className="text-sm">
            Other amount (₹)
            <input
              type="number"
              min={MIN_RUPEES}
              max={MAX_RUPEES}
              step={1}
              className="block w-32 rounded-xl border p-2"
              value={amount}
              onChange={(e) => setAmount(Number(e.target.value))}
            />
          </label>
        </div>
      </fieldset>
      {spaId && (
        <fieldset className="space-y-1" disabled={buy.isPending}>
          <legend className="font-medium">Where it can be used</legend>
          <label className="block">
            <input type="radio" checked={forSpa} onChange={() => setForSpa(true)} /> Only at {spaName || 'this spa'}
          </label>
          <label className="block">
            <input type="radio" checked={!forSpa} onChange={() => setForSpa(false)} /> At any spa on Nirvana
          </label>
        </fieldset>
      )}
      <fieldset className="space-y-3" disabled={buy.isPending}>
        <label className="block">
          Recipient's name (optional)
          <input className="block w-full rounded-xl border p-3" maxLength={100} value={recipientName} onChange={(e) => setRecipientName(e.target.value)} />
        </label>
        <label className="block">
          Recipient's email
          <input
            type="email"
            required
            className="block w-full rounded-xl border p-3"
            maxLength={255}
            value={recipientEmail}
            onChange={(e) => setRecipientEmail(e.target.value)}
          />
        </label>
        <label className="block">
          Message (optional)
          <textarea className="block w-full rounded-xl border p-3" maxLength={300} value={message} onChange={(e) => setMessage(e.target.value)} />
        </label>
      </fieldset>
      {(problem || buy.isError) && (
        <p role="alert" className="text-rose-700">
          {problem || errorMessage(buy.error)}
        </p>
      )}
      {buy.isSuccess && (
        <p role="status" className="text-emerald-700">
          Paid. The gift card code {buy.data.code} has been emailed to {buy.data.recipientEmail}.
        </p>
      )}
      <Button type="submit" disabled={buy.isPending}>
        {buy.isPending ? 'Processing…' : `Pay ₹${(Number(amount) || 0).toLocaleString('en-IN')}`}
      </Button>
    </form>
  );
}

function Mine() {
  const qc = useQueryClient();
  const cards = useQuery({ queryKey: ['my-gift-cards'], queryFn: myGiftCards });
  const resume = useMutation({
    mutationFn: (card: GiftCard) => resumeGiftCard(card.id),
    onSettled: () => void qc.invalidateQueries({ queryKey: ['my-gift-cards'] })
  });
  if (!cards.data?.length) return null;
  return (
    <section className="card space-y-3 p-6" aria-labelledby="mine-heading">
      <h2 id="mine-heading" className="text-xl font-semibold">
        Gift cards you bought
      </h2>
      <ul className="divide-y divide-slate-100">
        {cards.data.map((c) => (
          <li key={c.id} className="py-3 text-sm">
            <p className="font-medium">
              {money(c.initialCents, c.currency)} for {c.recipientName || c.recipientEmail} · {GIFT_CARD_STATUS[c.status]}
            </p>
            {c.status === 'PAYMENT_PENDING' ? (
              <Button size="sm" variant="secondary" disabled={resume.isPending} onClick={() => resume.mutate(c)}>
                Check payment
              </Button>
            ) : (
              <p className="text-slate-600">
                Code <span className="font-mono">{c.code}</span> · balance {money(c.balanceCents, c.currency)}
                {c.spaName ? ` · ${c.spaName} only` : ' · any spa'}
                {c.expiresAt ? ` · valid until ${date(c.expiresAt)}` : ''}
              </p>
            )}
          </li>
        ))}
      </ul>
      {resume.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(resume.error)}
        </p>
      )}
    </section>
  );
}

function CheckBalance() {
  const [code, setCode] = useState('');
  const check = useMutation({ mutationFn: () => giftCardBalance(code) });
  return (
    <form
      className="card space-y-3 p-6"
      aria-labelledby="balance-heading"
      onSubmit={(e) => {
        e.preventDefault();
        if (code.trim()) check.mutate();
      }}
    >
      <h2 id="balance-heading" className="text-xl font-semibold">
        Check a gift card balance
      </h2>
      <div className="flex flex-wrap items-end gap-2">
        <label className="text-sm">
          Gift card code
          <input className="block rounded-xl border p-2 uppercase" maxLength={32} value={code} onChange={(e) => setCode(e.target.value)} />
        </label>
        <Button type="submit" variant="secondary" disabled={check.isPending}>
          Check
        </Button>
      </div>
      {check.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(check.error)}
        </p>
      )}
      {check.data && (
        <p role="status">
          {money(check.data.balanceCents, check.data.currency)} left ·{' '}
          {GIFT_CARD_STATUS[check.data.status as GiftCard['status']] ?? check.data.status}
          {check.data.spaName ? ` · ${check.data.spaName} only` : ' · any spa'}
          {check.data.expiresAt ? ` · valid until ${date(check.data.expiresAt)}` : ''}
        </p>
      )}
    </form>
  );
}
