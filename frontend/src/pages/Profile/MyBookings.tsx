import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cancelBooking, getUserBookings, getSlots, rescheduleBooking } from '../../api/bookingApi';
import { resumePayment } from '../../api/paymentApi';
import { errorMessage } from '../../api/client';
import { BookingSummary } from '../../types/booking';
import Button from '../../components/common/Button';
import SlotGrid from '../../components/booking/SlotGrid';
export default function MyBookings() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<BookingSummary>();
  const [date, setDate] = useState('');
  const [target, setTarget] = useState('');
  const [cancel, setCancel] = useState<BookingSummary>();
  const qc = useQueryClient();
  const bookings = useQuery({
    queryKey: ['my-bookings', page],
    queryFn: () => getUserBookings(page)
  });
  const slots = useQuery({
    queryKey: ['reschedule', editing?.id, date],
    enabled: !!editing && !!date,
    queryFn: () => getSlots(editing!.spaId!, editing!.service.id, date)
  });
  const action = useMutation({
    mutationFn: async ({ kind, b }: { kind: string; b: BookingSummary }) => {
      if (kind === 'cancel') await cancelBooking(b.id);
      else if (kind === 'move') await rescheduleBooking(b.id, target);
      else await resumePayment(b.id);
    },
    onSuccess: () => {
      setEditing(undefined);
      setCancel(undefined);
      void qc.invalidateQueries({ queryKey: ['my-bookings'] });
    }
  });
  return (
    <div className="mx-auto max-w-5xl p-6 space-y-5">
      <h1 className="text-3xl font-bold">My appointments</h1>
      {bookings.isLoading && <p>Loading appointments…</p>}
      {(bookings.isError || action.isError) && (
        <p role="alert" className="text-rose-700">
          {errorMessage(bookings.error || action.error)}
        </p>
      )}
      {bookings.data?.items.map((b) => (
        <article className="card p-5 space-y-3" key={b.id}>
          <p>
            {b.spaName} · {b.reference}
          </p>
          <h2 className="text-xl font-semibold">{b.service.name}</h2>
          <p>
            {new Date(b.slot).toLocaleString()} · {b.service.durationMinutes} minutes
          </p>
          <p>
            {b.status.replaceAll('_', ' ')} ·{' '}
            {b.paymentMode === 'OFFLINE' ? 'Pay at spa' : 'Online payment'}
          </p>
          {b.refundStatus && b.refundStatus !== 'NONE' && <p>Refund: {b.refundStatus.toLowerCase()}</p>}
          <div className="flex gap-2 flex-wrap">
            {b.status === 'PENDING_PAYMENT' && b.paymentMode === 'ONLINE' && (
              <Button disabled={action.isPending} onClick={() => action.mutate({ kind: 'pay', b })}>
                Complete payment
              </Button>
            )}
            {b.status === 'CONFIRMED' && (
              <Button
                variant="secondary"
                onClick={() => {
                  setEditing(b);
                  setTarget('');
                  setDate('');
                }}
              >
                Reschedule
              </Button>
            )}
            {b.canCancel && (
              <Button variant="ghost" onClick={() => setCancel(b)}>
                Cancel appointment
              </Button>
            )}
          </div>
          {cancel?.id === b.id && (
            <div>
              <p>Cancel this appointment? The spa’s cancellation and refund policy applies.</p>
              <Button
                disabled={action.isPending}
                onClick={() => action.mutate({ kind: 'cancel', b })}
              >
                Confirm cancellation
              </Button>
              <Button variant="ghost" onClick={() => setCancel(undefined)}>
                Keep appointment
              </Button>
            </div>
          )}
          {editing?.id === b.id && (
            <div className="space-y-3">
              <label>
                New date
                <input
                  aria-label="New appointment date"
                  type="date"
                  min={new Date().toLocaleDateString('en-CA')}
                  value={date}
                  onChange={(e) => {
                    setDate(e.target.value);
                    setTarget('');
                  }}
                />
              </label>
              {slots.isLoading && <p>Loading times…</p>}
              {slots.isError && <p role="alert">{errorMessage(slots.error)}</p>}
              {slots.data && (
                <SlotGrid
                  slots={slots.data}
                  selectedSlot={target}
                  onSelect={(s) => setTarget(s.id)}
                />
              )}
              <Button
                disabled={!target || action.isPending}
                onClick={() => action.mutate({ kind: 'move', b })}
              >
                Confirm new time
              </Button>
              <Button variant="ghost" onClick={() => setEditing(undefined)}>
                Close
              </Button>
            </div>
          )}
        </article>
      ))}
      {bookings.data?.items.length === 0 && (
        <p>No appointments yet. Browse spas to find your next treatment.</p>
      )}
      <div className="flex gap-3">
        <Button disabled={page === 0} onClick={() => setPage(page - 1)}>
          Previous
        </Button>
        <span>Page {page + 1}</span>
        <Button
          disabled={!bookings.data || page + 1 >= bookings.data.totalPages}
          onClick={() => setPage(page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
