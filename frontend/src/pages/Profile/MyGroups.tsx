import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Button from '../../components/common/Button';
import { showToast } from '../../components/common/Toast';
import { errorMessage } from '../../api/client';
import { cancelGroup, findMember, getGroupQuote, getMyGroups } from '../../api/groupApi';
import { GroupMembers } from '../Booking/GroupConfirmation';

const money = (cents: number, currency: string) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(cents / 100);

export const useMyGroups = () => useQuery({ queryKey: ['my-booking-groups'], queryFn: getMyGroups });

/** On a booking card: which group it belongs to and whose appointment it is. */
export function GroupBadge({ bookingId }: { bookingId: string }) {
  const groups = useMyGroups();
  const found = findMember(groups.data, bookingId);
  if (!found) return null;
  return (
    <p className="text-sm font-medium text-primary-700">
      {found.group.size === 2 ? 'Couple booking' : `Group of ${found.group.size}`} · guest {found.member.position}:{' '}
      {found.member.booker ? 'you' : found.member.guestName}
    </p>
  );
}

/** Upcoming group bookings shown together, with cancelling the whole group in one step. */
export default function MyGroups() {
  const qc = useQueryClient();
  const groups = useMyGroups();
  const [cancelling, setCancelling] = useState<number>();
  const quote = useQuery({
    queryKey: ['group-quote', cancelling],
    enabled: cancelling != null,
    queryFn: () => getGroupQuote(cancelling!)
  });
  const cancel = useMutation({
    mutationFn: () => cancelGroup(cancelling!, quote.data!.refundCents),
    onSuccess: () => {
      showToast('Group booking cancelled.', 'success');
      setCancelling(undefined);
      void qc.invalidateQueries({ queryKey: ['my-booking-groups'] });
      void qc.invalidateQueries({ queryKey: ['my-bookings'] });
    },
    onError: () => void quote.refetch()
  });
  const upcoming = (groups.data || []).filter(
    (g) => new Date(g.startTs) > new Date() && g.members.some((m) => m.status === 'CONFIRMED' || m.status === 'PENDING_PAYMENT')
  );
  if (upcoming.length === 0) return null;
  return (
    <section className="space-y-3" aria-label="Group bookings">
      <h2 className="text-xl font-semibold">Group bookings</h2>
      {upcoming.map((g) => (
        <article key={g.groupId} className="card space-y-3 p-5">
          <p>
            {g.spaName} · {new Date(g.startTs).toLocaleString()} · {g.size} guests
          </p>
          <GroupMembers group={g} />
          <p className="text-sm text-slate-600">
            To cancel one guest, use Cancel on that guest&rsquo;s appointment below. Their share is refunded under the
            spa&rsquo;s policy; the rest of the group stays booked.
          </p>
          {cancelling === g.groupId ? (
            <div className="space-y-2 rounded-xl border p-3">
              {quote.isLoading && <p role="status">Checking the cancellation terms…</p>}
              {quote.isError && <p role="alert">{errorMessage(quote.error)}</p>}
              {quote.data && (
                <p>
                  Cancel {quote.data.members.length} appointment{quote.data.members.length === 1 ? '' : 's'} · refund{' '}
                  {money(quote.data.refundCents, quote.data.currency)}
                  {quote.data.feeCents > 0 ? ` · fee ${money(quote.data.feeCents, quote.data.currency)}` : ''}
                </p>
              )}
              {cancel.isError && <p role="alert">{errorMessage(cancel.error)}</p>}
              <div className="flex gap-2">
                <Button disabled={!quote.data || cancel.isPending} onClick={() => cancel.mutate()}>
                  Cancel the whole group
                </Button>
                <Button variant="secondary" onClick={() => setCancelling(undefined)}>
                  Keep it
                </Button>
              </div>
            </div>
          ) : (
            <Button variant="secondary" onClick={() => setCancelling(g.groupId)}>
              Cancel the whole group
            </Button>
          )}
        </article>
      ))}
    </section>
  );
}
