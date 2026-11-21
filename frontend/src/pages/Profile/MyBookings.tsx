import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cancelBooking, getUserBookings, getSlots, getTherapistDirectory, rescheduleBooking } from '../../api/bookingApi';
import { resumePayment } from '../../api/paymentApi';
import client, { errorMessage } from '../../api/client';
import { BookingSummary } from '../../types/booking';
import Button from '../../components/common/Button';
import SlotGrid from '../../components/booking/SlotGrid';
import { uniqueAvailableTimes } from '../Booking/availableTimes';
export default function MyBookings() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<BookingSummary>();
  const [preference, setPreference] = useState('keep');
  const directory = useQuery({
    queryKey: ['therapist-directory', editing?.spaId, editing?.service.id],
    enabled: !!editing,
    queryFn: () => getTherapistDirectory(editing!.spaId!, editing!.service.id)
  });
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
      if (kind === 'invoice') {
        const response = await client.get(`/api/bookings/${b.id}/invoice/pdf`, { responseType: 'blob' });
        const url = URL.createObjectURL(response.data);
        const link = document.createElement('a');
        link.href = url;
        link.download = `invoice-${b.reference}.pdf`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      } else if (kind === 'cancel') await cancelBooking(b.id);
      else if (kind === 'move') await rescheduleBooking(b.id, target, preference === 'keep' ? undefined : preference);
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
      {bookings.isLoading && <p role="status">Loading appointments…</p>}
      {(bookings.isError || action.isError) && (
        <p role="alert" className="text-rose-700">
          {errorMessage(bookings.error || action.error)}
        </p>
      )}
      {bookings.isError && <Button onClick={() => void bookings.refetch()}>Retry appointments</Button>}
      {bookings.data?.items.length === 0 && <p>No appointments yet. <a href="/spas" className="underline">Explore spas</a></p>}
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
          {b.therapistName && <p>Therapist: {b.therapistName}</p>}
          {b.refundStatus && b.refundStatus !== 'NONE' && <p>Refund: {b.refundStatus.toLowerCase()}</p>}
          <div className="flex gap-2 flex-wrap">
            {b.invoiceAvailable &&
              <Button variant="secondary" disabled={action.isPending} onClick={() => action.mutate({kind: 'invoice', b})}>Download invoice</Button>}
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
                  setPreference('keep');
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
              <label className="block">Therapist
                <select value={preference} onChange={e => {setPreference(e.target.value); setTarget('');}}>
                  <option value="keep">Keep original preference</option>
                  <option value="">Any available therapist</option>
                  {directory.data?.named.map(t => <option key={t.id} value={t.id}>{t.name}{t.type && ' · ' + t.type}</option>)}
                  {directory.data?.labels.map(label => <option key={label} value={'type:' + label}>{label}</option>)}
                </select>
              </label>
              {slots.isLoading && <p>Loading times…</p>}
              {slots.isError && <p role="alert">{errorMessage(slots.error)}</p>}
              {slots.data && (
                <SlotGrid
                  slots={preference === 'keep' ? slots.data : uniqueAvailableTimes(slots.data, preference)}
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
