import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import { useAuth } from '../../hooks/useAuth';
import { isAdmin } from '../../auth/roles';

export type Claim = {
  id: number;
  spaId: number;
  spaName: string;
  city?: string;
  state?: string;
  postalCode?: string;
  addressLine?: string;
  placeId: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  consentedAt: string;
  termsVersion: string;
  ownerEmail: string;
  ownerName?: string;
  phone?: string;
  spaEmail?: string;
  websiteUrl?: string;
  reviewNote?: string;
  reviewedAt?: string;
  kycStatus?: string;
  phoneVerifiedAt?: string;
};

const statuses = [
  ['PENDING', 'Waiting for review'],
  ['APPROVED', 'Approved'],
  ['REJECTED', 'Rejected']
] as const;

export const googleListingUrl = (claim: Pick<Claim, 'spaName' | 'placeId'>) =>
  `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(claim.spaName)}&query_place_id=${encodeURIComponent(claim.placeId)}`;

const when = (value?: string) => (value ? new Date(value).toLocaleString() : '—');

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="break-words">{children || '—'}</dd>
    </div>
  );
}

/** One claim: what the owner submitted, links to compare with Google, and the decision. */
export function ClaimCard({ claim }: { claim: Claim }) {
  const qc = useQueryClient();
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [confirming, setConfirming] = useState(false);
  const decide = useMutation({
    mutationFn: async (decision: 'approve' | 'reject') =>
      client.post(
        `/api/admin/listing-claims/${claim.id}/${decision}`,
        decision === 'reject' ? { reason: reason.trim() } : undefined
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['listing-claims'] })
  });
  const address = [claim.addressLine, claim.city, claim.state, claim.postalCode]
    .filter(Boolean)
    .join(', ');
  return (
    <article className="card space-y-4 p-5" aria-labelledby={`claim-${claim.id}`}>
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id={`claim-${claim.id}`} className="text-lg font-semibold">
            {claim.spaName}
          </h2>
          <p className="text-sm text-slate-600">
            Spa #{claim.spaId} · claimed {when(claim.consentedAt)}
          </p>
          {claim.phoneVerifiedAt ? (
            <span className="mt-2 inline-block rounded-full bg-green-100 px-3 py-1 text-xs font-semibold text-green-900">
              ✓ Listing phone verified by text
            </span>
          ) : (
            <span className="mt-2 inline-block rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-900">
              Phone not verified
            </span>
          )}
        </div>
        <a
          className="rounded-xl border px-3 py-2 text-sm underline"
          href={googleListingUrl(claim)}
          target="_blank"
          rel="noreferrer"
        >
          Open the Google listing
        </a>
      </header>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <Fact label="Owner">
          {claim.ownerName} {claim.ownerName && '·'} {claim.ownerEmail}
        </Fact>
        <Fact label="Phone submitted">{claim.phone}</Fact>
        <Fact label="Address submitted">{address}</Fact>
        <Fact label="Spa email · website">
          {[claim.spaEmail, claim.websiteUrl].filter(Boolean).join(' · ')}
        </Fact>
        <Fact label="Agreed to">Terms {claim.termsVersion}</Fact>
        <Fact label="Google Place ID">
          <code className="text-xs">{claim.placeId}</code>
        </Fact>
        {claim.status !== 'PENDING' && (
          <>
            <Fact label="Reviewed">{when(claim.reviewedAt)}</Fact>
            {claim.reviewNote && <Fact label="Reason">{claim.reviewNote}</Fact>}
          </>
        )}
      </dl>
      {claim.status === 'PENDING' && (
        <div className="space-y-3 border-t pt-4">
          <p className="text-sm text-slate-600">
            {claim.phoneVerifiedAt
              ? `The claimant entered a code texted to the phone on the Google listing (${when(claim.phoneVerifiedAt)}). Check the details match the listing before approving.`
              : `Before approving, call the number shown on the Google listing — not the one submitted — and confirm that ${claim.ownerName || 'the owner'} manages this spa.`}
          </p>
          {rejecting ? (
            <form
              className="space-y-2"
              onSubmit={(e) => {
                e.preventDefault();
                decide.mutate('reject');
              }}
            >
              <label className="flex flex-col gap-1 text-sm font-medium">
                Reason, shown to the owner *
                <textarea
                  className="min-h-20 rounded-xl border px-3 py-2 font-normal"
                  required
                  maxLength={1000}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <p className="text-xs text-slate-600">
                Rejecting unpublishes the spa and frees the listing so someone else can claim it.
              </p>
              <div className="flex flex-wrap gap-2">
                <Button type="submit" disabled={decide.isPending || !reason.trim()}>
                  Reject claim
                </Button>
                <Button type="button" variant="secondary" onClick={() => setRejecting(false)}>
                  Cancel
                </Button>
              </div>
            </form>
          ) : confirming ? (
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm">Publish this spa and mark it verified?</span>
              <Button disabled={decide.isPending} onClick={() => decide.mutate('approve')}>
                Yes, approve
              </Button>
              <Button variant="secondary" onClick={() => setConfirming(false)}>
                Cancel
              </Button>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => setConfirming(true)}>Approve</Button>
              <Button variant="secondary" onClick={() => setRejecting(true)}>
                Reject
              </Button>
            </div>
          )}
          {decide.isError && (
            <p role="alert" className="text-rose-700">
              {errorMessage(decide.error)}
            </p>
          )}
        </div>
      )}
    </article>
  );
}

export default function ListingClaims() {
  const { user } = useAuth();
  const allowed = isAdmin(user?.roles);
  const [status, setStatus] = useState<Claim['status']>('PENDING');
  const claims = useQuery({
    queryKey: ['listing-claims', status],
    queryFn: async () =>
      (await client.get<Claim[]>('/api/admin/listing-claims', { params: { status } })).data,
    enabled: allowed
  });
  if (!allowed)
    return (
      <div className="mx-auto max-w-xl space-y-3 p-8">
        <h1 className="text-2xl font-bold">Admin access required</h1>
        <Link to="/">Go home</Link>
      </div>
    );
  return (
    <div className="mx-auto max-w-4xl space-y-5 p-4 md:p-8">
      <div>
        <h1 className="text-3xl font-bold">Listing claims</h1>
        <p className="text-slate-600">
          Spa owners who claimed their Google Maps listing. Approving publishes the spa.
        </p>
      </div>
      <nav aria-label="Claim status" className="flex flex-wrap gap-2">
        {statuses.map(([value, label]) => (
          <button
            key={value}
            type="button"
            aria-pressed={status === value}
            onClick={() => setStatus(value)}
            className={`rounded-xl border px-4 py-2 ${status === value ? 'bg-primary-700 text-white' : 'bg-white'}`}
          >
            {label}
          </button>
        ))}
      </nav>
      {claims.isPending && <p role="status">Loading claims…</p>}
      {claims.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(claims.error)}
        </p>
      )}
      {claims.data?.length === 0 && (
        <p className="card p-6 text-slate-600">
          {status === 'PENDING' ? 'No claims are waiting for review.' : 'No claims here yet.'}
        </p>
      )}
      <div className="space-y-4">
        {claims.data?.map((c) => <ClaimCard key={c.id} claim={c} />)}
      </div>
    </div>
  );
}
