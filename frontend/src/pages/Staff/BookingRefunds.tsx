import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';

type Refund = {
  id: number;
  reference: string;
  amountCents: number;
  currency: string;
  reason: string;
  status: 'REQUESTED' | 'FAILED' | 'PROCESSING' | 'REVIEW_REQUIRED';
  attempt: number;
  gatewayRefundId?: string;
  gatewayPaymentId: string;
};
type Worklist = { items: Refund[]; hasNext: boolean };

export default function BookingRefunds() {
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const [references, setReferences] = useState<Record<number, string>>({});
  const verify = useMutation({
    mutationFn: async (id: number) =>
      client.post(`/api/admin/booking-refunds/${id}/verify`, { refundId: references[id]?.trim() }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['booking-refunds'] })
  });
  const list = useQuery({
    queryKey: ['booking-refunds', page],
    queryFn: async () =>
      (await client.get<Worklist>('/api/admin/booking-refunds', { params: { page } })).data,
    refetchInterval: 30000
  });
  const recover = useMutation({
    mutationFn: async (refund: Refund) =>
      client.post(
        `/api/admin/booking-refunds/${refund.id}/${refund.attempt === 0 ? 'recover' : 'resume'}`
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['booking-refunds'] })
  });
  const decision = useMutation({
    mutationFn: async ({ refund, approve }: { refund: Refund; approve: boolean }) =>
      client.post(
        `/api/admin/booking-refunds/${refund.id}/decision`,
        {
          approve,
          issueVoucher: false,
          reason: approve ? 'Approved by finance after review' : 'Declined by finance after review'
        },
        { params: { status: refund.status, attempt: refund.attempt } }
      ),
    onSettled: () => qc.invalidateQueries({ queryKey: ['booking-refunds'] })
  });
  return (
    <section aria-label="Booking refunds" className="space-y-3 rounded-2xl border bg-white p-5">
      <h2 className="text-xl font-semibold">Booking refunds</h2>
      {list.isLoading && <p>Loading refunds…</p>}
      {list.isError && <p role="alert">{errorMessage(list.error)}</p>}
      {list.data?.items.map((refund) => (
        <article
          key={refund.id}
          className="flex flex-wrap items-center justify-between gap-3 border-t pt-3"
        >
          <div>
            <p className="font-semibold">
              {refund.reference} ·{' '}
              {new Intl.NumberFormat('en-IN', {
                style: 'currency',
                currency: refund.currency
              }).format(refund.amountCents / 100)}
            </p>
            <p className="text-sm">{refund.reason}</p>
            <p className="text-sm text-slate-600">
              {refund.status} · Attempt {refund.attempt}
              {refund.gatewayRefundId && ` · ${refund.gatewayRefundId}`}
            </p>
          </div>
          {refund.status === 'REVIEW_REQUIRED' && refund.attempt === 0 && (
            <form
              className="flex flex-wrap items-end gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                verify.mutate(refund.id);
              }}
            >
              <label className="text-sm">
                Razorpay refund ID for {refund.gatewayPaymentId}
                <input
                  className="block rounded-lg border p-2"
                  required
                  pattern="rfnd_[A-Za-z0-9]+"
                  placeholder="rfnd_..."
                  value={references[refund.id] || ''}
                  onChange={(event) =>
                    setReferences({ ...references, [refund.id]: event.target.value })
                  }
                />
              </label>
              <button className="btn-secondary" disabled={verify.isPending}>
                Verify refund
              </button>
              <button
                type="button"
                className="btn-secondary"
                disabled={recover.isPending}
                onClick={() => recover.mutate(refund)}
              >
                Recover if never refunded
              </button>
            </form>
          )}
          {refund.status === 'REVIEW_REQUIRED' && refund.attempt > 0 && (
            <button
              className="btn-secondary"
              disabled={recover.isPending}
              onClick={() => recover.mutate(refund)}
            >
              Resume gateway reconciliation
            </button>
          )}
          {(refund.status === 'REQUESTED' || refund.status === 'FAILED') && (
            <div className="flex gap-3">
              <button
                type="button"
                className="btn-primary"
                disabled={decision.isPending}
                onClick={() => decision.mutate({ refund, approve: true })}
              >
                {refund.status === 'FAILED' ? 'Retry refund' : 'Approve refund'}
              </button>
              {refund.status === 'REQUESTED' && (
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={decision.isPending}
                  onClick={() => decision.mutate({ refund, approve: false })}
                >
                  Decline
                </button>
              )}
            </div>
          )}
        </article>
      ))}
      {list.data?.items.length === 0 && <p>No open refunds on this page.</p>}
      {recover.isError && <p role="alert">{errorMessage(recover.error)}</p>}
      {verify.isError && <p role="alert">{errorMessage(verify.error)}</p>}
      {decision.isError && <p role="alert">{errorMessage(decision.error)}</p>}
      <div className="flex gap-3">
        {page > 0 && <button onClick={() => setPage(page - 1)}>Previous refunds</button>}
        {list.data?.hasNext && <button onClick={() => setPage(page + 1)}>More refunds</button>}
      </div>
    </section>
  );
}
