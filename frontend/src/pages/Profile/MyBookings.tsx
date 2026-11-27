import CancellationBreakdown from './CancellationBreakdown';
import { Requests } from '../Owner/AppointmentOperations';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cancelBooking, getCancellationQuote, getUserBookings, getSlots, getTherapistDirectory, rescheduleBooking } from '../../api/bookingApi';
import { payAtSpa, resumePayment } from '../../api/paymentApi';
import client, { errorMessage } from '../../api/client';
import { BookingSummary } from '../../types/booking';
import Button from '../../components/common/Button';
import SlotGrid from '../../components/booking/SlotGrid';
import { replacementTimes } from '../Booking/availableTimes';
import HealthForm from '../../components/health/HealthForm';
import InvoiceLinks from './InvoiceLinks';
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
  const [health, setHealth] = useState<string>();
  const quote = useQuery({
    queryKey: ['cancellation-quote', cancel?.id],
    enabled: !!cancel,
    queryFn: () => getCancellationQuote(cancel!.id)
  });
  const wallet = useQuery({
    queryKey: ['my-wallet'],
    queryFn: async () => (await client.get<{currency: string; balance: number}[]>('/api/me/wallet')).data
  });
  const money = (cents: number, currency: string) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(cents / 100);
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
  const eligibleSlots = replacementTimes(slots.data || [], preference, editing?.originalPreference);
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
      } else if (kind === 'cancel') {
        if (!quote.data) throw new Error('Review the cancellation amounts first.');
        await cancelBooking(b.id, quote.data);
      }
      else if (kind === 'move') await rescheduleBooking(b.id, target, preference === 'keep' ? undefined : preference);
      else if (kind === 'offline') await payAtSpa(b.id);
      else await resumePayment(b.id);
    },
    onError: (_error, variables) => {
      if (variables.kind === 'cancel') void quote.refetch();
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['my-wallet'] });
      setEditing(undefined);
      setCancel(undefined);
      void qc.invalidateQueries({ queryKey: ['my-bookings'] });
    }
  });
  return (
    <div className="mx-auto max-w-5xl p-6 space-y-5">
      <h1 className="text-3xl font-bold">My appointments</h1>
      <Requests/>
      {wallet.data?.map(w => <p key={w.currency}>Wallet credit: {money(w.balance, w.currency)}</p>)}
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
          {b.status === 'CONFIRMED' && <p>{b.spaAcknowledged ? 'Confirmed by the spa' : 'Awaiting a reply from the spa'}</p>}
          {b.therapistName && <p>Therapist: {b.therapistName}</p>}
          {b.refundStatus && b.refundStatus !== 'NONE' && <p>Refund: {b.refundStatus.toLowerCase()}</p>}
          <div className="flex gap-2 flex-wrap">
            {(b.invoiceAvailable || (b.paymentMode === 'OFFLINE' && b.status === 'COMPLETED')) && <InvoiceLinks bookingId={b.id} />}
            {b.invoiceAvailable &&
              <Button variant="secondary" disabled={action.isPending} onClick={() => action.mutate({kind: 'invoice', b})}>Download PDF</Button>}
            {b.status === 'PENDING_PAYMENT' && b.paymentMode === 'ONLINE' && (
              <>
              <Button disabled={action.isPending} onClick={() => action.mutate({ kind: 'pay', b })}>
                Retry online payment
              </Button>
              <Button variant="secondary" disabled={action.isPending} onClick={() => action.mutate({kind: 'offline', b})}>
                Confirm pay at spa
              </Button>
              </>
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
            {['PENDING_PAYMENT', 'CONFIRMED', 'ARRIVED'].includes(b.status) && (
              <Button variant="secondary" onClick={() => setHealth(health === b.id ? undefined : b.id)}>
                Health form
              </Button>
            )}
            {b.canCancel && (
              <Button variant="ghost" onClick={() => setCancel(b)}>
                Cancel appointment
              </Button>
            )}
          </div>
          {health === b.id && <HealthForm bookingId={b.id} onDone={() => setHealth(undefined)} />}
          {cancel?.id === b.id && (
            <div>
              <p>Cancel this appointment?</p>
              {quote.isFetching && <p role="status">Checking cancellation terms…</p>}
              {quote.isError && <p role="alert">{errorMessage(quote.error)}</p>}
              {quote.data && <CancellationBreakdown quote={quote.data} />}
              <Button
                disabled={action.isPending || !quote.data || quote.isError || quote.isFetching}
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
              {slots.data && !eligibleSlots.length && <p>No available times for this therapist preference on this date.</p>}
              {slots.data && (
                <SlotGrid
                  slots={eligibleSlots}
                  selectedSlot={target}
                  onSelect={(s) => setTarget(s.id)}
                />
              )}
              <Button
                disabled={!eligibleSlots.some(s => s.id === target) || action.isPending}
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
