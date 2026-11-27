import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';

export type SpaBookingView = {
  reference: string;
  spaName: string;
  service?: string;
  when: string;
  guests: number;
  guestName: string;
  payment: string;
  /** PENDING, CONFIRMED, DECLINED, AUTO_CONFIRMED, NO_MOBILE or CLOSED */
  status: string;
  bookingStatus: string;
};

export const DECLINE_REASONS = [
  'Fully booked at that time',
  'Closed that day',
  "We don't offer this treatment",
  'Other'
];

/** What the spa needs to know first, in a sentence. */
export function headline(b: SpaBookingView) {
  if (b.status === 'DECLINED') return 'You declined this booking. The appointment is cancelled.';
  if (b.bookingStatus === 'CANCELLED' || b.status === 'CLOSED') return 'This booking was cancelled. The time is free again.';
  if (b.status === 'CONFIRMED') return 'You confirmed this booking. Thank you!';
  if (['NEEDS_CONTACT', 'AUTO_CONFIRMED'].includes(b.status)) return 'Your reply is overdue. Can you still take this booking?';
  return 'New booking. Can you take it?';
}

const answerable = (b: SpaBookingView) =>
  b.bookingStatus === 'CONFIRMED' || b.bookingStatus === 'PENDING_PAYMENT';

/** The page a spa opens from its booking SMS: no sign-in, just confirm or decline. */
const SpaBookingReply = ({ owner = false }: { owner?: boolean }) => {
  const { token = '', bookingId = '' } = useParams();
  const path = owner ? '/api/owner/push/bookings/' + encodeURIComponent(bookingId)
    : '/api/public/spa-bookings/' + encodeURIComponent(token);
  const qc = useQueryClient();
  const key = owner ? ['owner-booking-reply', bookingId] : ['spa-booking-reply', token];
  const booking = useQuery({
    queryKey: key,
    queryFn: async () =>
      (await client.get<SpaBookingView>(path)).data,
    retry: false
  });
  const [declining, setDeclining] = useState(false);
  const [reason, setReason] = useState(DECLINE_REASONS[0]);
  const [other, setOther] = useState('');
  const answer = useMutation({
    mutationFn: async (action: 'confirm' | 'decline') =>
      (
        await client.post<SpaBookingView>(
          `${path}/${action}`,
          action === 'decline' ? { reason: reason === 'Other' ? other.trim() || 'Other' : reason } : undefined
        )
      ).data,
    onSuccess: (data) => {
      setDeclining(false);
      qc.setQueryData(key, data);
    }
  });

  const b = booking.data;
  return (
    <div className="mx-auto max-w-md px-4 py-8">
      <p className="eyebrow">
        <span /> NIRVANA BOOKING
      </p>
      {booking.isLoading && <p role="status">Loading the booking…</p>}
      {booking.isError && (
        <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-800">
          {errorMessage(booking.error)}
        </p>
      )}
      {b && (
        <div className="space-y-5">
          <h1 className="text-2xl font-semibold text-primary-900">{headline(b)}</h1>
          <dl className="spa-facts rounded-2xl border border-primary-100 bg-white p-5">
            <div>
              <dt>Spa</dt>
              <dd>{b.spaName}</dd>
            </div>
            {b.service && (
              <div>
                <dt>Treatment</dt>
                <dd>{b.service}</dd>
              </div>
            )}
            <div>
              <dt>When</dt>
              <dd className="font-semibold">{b.when}</dd>
            </div>
            <div>
              <dt>Guest</dt>
              <dd>
                {b.guestName}
                {b.guests > 1 ? ` and ${b.guests - 1} more` : ''}
              </dd>
            </div>
            <div>
              <dt>Payment</dt>
              <dd>{b.payment.charAt(0).toUpperCase() + b.payment.slice(1)}</dd>
            </div>
            <div>
              <dt>Booking</dt>
              <dd>{b.reference}</dd>
            </div>
          </dl>

          {answer.isError && (
            <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-800">
              {errorMessage(answer.error)}
            </p>
          )}

          {answerable(b) && b.status !== 'DECLINED' && !declining && (
            <div className="flex flex-col gap-3">
              {b.status !== 'CONFIRMED' && (
                <Button size="lg" disabled={answer.isPending} onClick={() => answer.mutate('confirm')}>
                  {answer.isPending && answer.variables === 'confirm' ? 'Confirming…' : 'Confirm booking'}
                </Button>
              )}
              <Button size="lg" variant="secondary" disabled={answer.isPending} onClick={() => setDeclining(true)}>
                {b.status === 'CONFIRMED' ? "We can't take it after all" : 'Decline'}
              </Button>
              {b.status === 'PENDING' && (
                <p className="text-sm text-slate-600">
                  If you don't reply, the booking stays and the guest will come at this time.
                </p>
              )}
            </div>
          )}

          {declining && (
            <form
              className="space-y-3 rounded-2xl border border-red-100 bg-white p-5"
              onSubmit={(e) => {
                e.preventDefault();
                answer.mutate('decline');
              }}
            >
              <fieldset className="space-y-2">
                <legend className="font-semibold">Why can't you take it?</legend>
                {DECLINE_REASONS.map((r) => (
                  <label key={r} className="flex items-center gap-2">
                    <input type="radio" name="reason" value={r} checked={reason === r} onChange={() => setReason(r)} />
                    {r}
                  </label>
                ))}
              </fieldset>
              {reason === 'Other' && (
                <label className="block text-sm">
                  Reason
                  <input
                    className="mt-1 w-full rounded-lg border border-slate-300 p-2"
                    maxLength={300}
                    value={other}
                    onChange={(e) => setOther(e.target.value)}
                  />
                </label>
              )}
              <p className="text-sm text-slate-600">
                The booking will be cancelled and the guest told. Anything they paid is refunded in full.
              </p>
              <div className="flex flex-wrap gap-3">
                <Button type="submit" disabled={answer.isPending}>
                  {answer.isPending ? 'Declining…' : 'Decline booking'}
                </Button>
                <Button type="button" variant="ghost" onClick={() => setDeclining(false)}>
                  Back
                </Button>
              </div>
            </form>
          )}
        </div>
      )}
    </div>
  );
};

export default SpaBookingReply;
