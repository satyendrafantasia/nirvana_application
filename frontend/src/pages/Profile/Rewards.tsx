import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import {
  claimReferral,
  getLoyalty,
  getReferral,
  ledgerLabel,
  pointsRules,
  signedPoints
} from '../../api/rewardsApi';
import Button from '../../components/common/Button';

const date = (iso?: string) =>
  iso ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';

/** Points balance and every change to it, newest first. */
export function LoyaltyPoints() {
  const loyalty = useQuery({ queryKey: ['loyalty'], queryFn: getLoyalty });
  const [all, setAll] = useState(false);
  return (
    <section className="card space-y-3 p-6" aria-labelledby="points-heading">
      <h2 id="points-heading" className="text-xl font-semibold">
        Loyalty points
      </h2>
      {loyalty.isPending && <p role="status">Loading your points…</p>}
      {loyalty.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(loyalty.error)}
        </p>
      )}
      {loyalty.data && (
        <>
          <p className="text-3xl font-bold">{loyalty.data.balance.toLocaleString('en-IN')}</p>
          {loyalty.data.expiringSoonPoints > 0 && (
            <p className="rounded-lg bg-amber-50 p-2 text-sm text-amber-900">
              {loyalty.data.expiringSoonPoints.toLocaleString('en-IN')} point
              {loyalty.data.expiringSoonPoints === 1 ? '' : 's'} expire on {date(loyalty.data.expiringSoonAt)}. Use them on
              your next booking.
            </p>
          )}
          <p className="text-sm text-slate-600">{pointsRules(loyalty.data)}</p>
          {loyalty.data.history.length === 0 ? (
            <p className="text-sm text-slate-600">No points activity yet.</p>
          ) : (
            <>
              <table className="w-full text-sm">
                <caption className="sr-only">Points history</caption>
                <thead>
                  <tr className="text-left text-slate-500">
                    <th className="py-1 font-medium">Date</th>
                    <th className="py-1 font-medium">What happened</th>
                    <th className="py-1 text-right font-medium">Points</th>
                    <th className="py-1 text-right font-medium">Balance</th>
                  </tr>
                </thead>
                <tbody>
                  {(all ? loyalty.data.history : loyalty.data.history.slice(0, 8)).map((e) => (
                    <tr key={e.id} className="border-t border-slate-100 align-top">
                      <td className="py-2 whitespace-nowrap">{date(e.createdAt)}</td>
                      <td className="py-2">
                        {ledgerLabel(e.type)}
                        {e.bookingReference && <span className="text-slate-500"> · {e.bookingReference}</span>}
                        {e.remaining > 0 && e.expiresAt && (
                          <span className="block text-xs text-slate-500">
                            {e.remaining.toLocaleString('en-IN')} left, expires {date(e.expiresAt)}
                          </span>
                        )}
                      </td>
                      <td className={`py-2 text-right ${e.points < 0 ? 'text-rose-700' : 'text-emerald-700'}`}>
                        {signedPoints(e.points)}
                      </td>
                      <td className="py-2 text-right">{e.balanceAfter.toLocaleString('en-IN')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {loyalty.data.history.length > 8 && (
                <button type="button" className="text-sm underline" onClick={() => setAll(!all)}>
                  {all ? 'Show fewer' : `Show all ${loyalty.data.history.length}`}
                </button>
              )}
            </>
          )}
        </>
      )}
    </section>
  );
}

/** The customer's own referral link, and a place to add the code of whoever referred them. */
export function ReferFriends() {
  const qc = useQueryClient();
  const referral = useQuery({ queryKey: ['referral'], queryFn: getReferral });
  const [code, setCode] = useState('');
  const [copied, setCopied] = useState(false);
  const claim = useMutation({
    mutationFn: () => claimReferral(code),
    onSuccess: (data) => {
      qc.setQueryData(['referral'], data);
      setCode('');
    }
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (code.trim()) claim.mutate();
  };
  const r = referral.data;
  return (
    <section className="card space-y-3 p-6" aria-labelledby="referral-heading">
      <h2 id="referral-heading" className="text-xl font-semibold">
        Refer a friend
      </h2>
      {referral.isPending && <p role="status">Loading…</p>}
      {referral.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(referral.error)}
        </p>
      )}
      {r && (
        <>
          <p className="text-sm text-slate-600">{r.reward}</p>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-lg bg-primary-50 px-3 py-2 font-mono text-lg tracking-wider">{r.code}</span>
            <Button
              variant="secondary"
              onClick={() =>
                void navigator.clipboard?.writeText(r.link).then(() => {
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                })
              }
            >
              {copied ? 'Link copied' : 'Copy invite link'}
            </Button>
          </div>
          <p className="break-all text-xs text-slate-500">{r.link}</p>
          <p className="text-sm">
            {r.invited === 0
              ? 'Nobody has joined with your code yet.'
              : `${r.invited} joined with your code, ${r.rewarded} rewarded so far.`}{' '}
            Rewards are given for up to {r.monthlyCap} friends a month, once per phone number, after the friend's first
            paid appointment is completed.
          </p>
          {r.referredBy ? (
            <p className="text-sm">
              You joined with code <strong>{r.referredBy}</strong>:{' '}
              {r.myStatus === 'REWARDED'
                ? 'your welcome reward has been added.'
                : r.myStatus === 'REJECTED'
                  ? `no reward (${r.myStatusReason?.toLowerCase() ?? 'not eligible'}).`
                  : r.myStatusReason
                    ? `${r.myStatusReason.toLowerCase()}.`
                    : 'your reward arrives after your first paid appointment.'}
            </p>
          ) : (
            r.canClaim && (
              <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
                <label className="text-sm">
                  Were you referred? Enter their code
                  <input
                    className="block rounded-xl border p-2 uppercase"
                    value={code}
                    maxLength={16}
                    onChange={(e) => setCode(e.target.value)}
                  />
                </label>
                <Button type="submit" disabled={claim.isPending || !code.trim()}>
                  {claim.isPending ? 'Adding…' : 'Add code'}
                </Button>
              </form>
            )
          )}
          {claim.isError && (
            <p role="alert" className="text-rose-700">
              {errorMessage(claim.error)}
            </p>
          )}
        </>
      )}
    </section>
  );
}
