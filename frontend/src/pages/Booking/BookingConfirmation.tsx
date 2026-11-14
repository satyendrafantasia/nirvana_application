import Button from '../../components/common/Button';
import { BookingSummary } from '../../types/booking';
import { useBookingFlow } from '../../hooks/useBookingFlow';

interface BookingConfirmationProps {
  summary: BookingSummary | null;
  onRestart: () => void;
}

const BookingConfirmation = ({ summary, onRestart }: BookingConfirmationProps) => {
  const { reset } = useBookingFlow();

  const handleDone = () => {
    reset();
    onRestart();
  };

  return (
    <div className="space-y-6 text-center">
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary-50 text-3xl">
        {summary?.status === 'CONFIRMED' ? '✓' : '…'}
      </div>
      <h2 className="text-3xl font-semibold text-slate-900">{summary?.status === 'CONFIRMED' ? 'Appointment confirmed' : summary?.status === 'PENDING_PAYMENT' ? 'Payment pending' : 'Booking status'}</h2>
      <p className="text-slate-600">Your booking reference and current status are below.</p>

      {summary ? (
        <div className="mx-auto max-w-lg rounded-2xl bg-gray-50 p-5 text-left">
          <p className="text-sm text-slate-500">Booking ID</p>
          <p className="text-lg font-semibold text-slate-900">
            {summary.reference || summary.id} · {summary.status}
          </p>
          <p className="text-sm text-slate-500">Spa</p>
          <p className="text-lg font-semibold text-slate-900">{summary.spaName}</p>
          <p className="text-sm text-slate-500">Service</p>
          <p className="text-lg font-semibold text-slate-900">{summary.service.name}</p>
          <p className="text-sm text-slate-500">Date and time</p>
          <time dateTime={summary.slot}>{new Date(summary.slot).toLocaleString(undefined, { dateStyle: 'long', timeStyle: 'short' })}</time>
          {summary.totalCents != null && summary.currency && <p className="mt-3">Booking total · {new Intl.NumberFormat(undefined, { style: 'currency', currency: summary.currency }).format(summary.totalCents / 100)}</p>}
        </div>
      ) : (
        <p className="text-sm text-slate-500">
          Booking details will appear here after confirmation.
        </p>
      )}

      <div className="flex flex-wrap justify-center gap-3">
        <Button onClick={handleDone}>Book another service</Button>
        <Button variant="secondary" as="a" href="/me/bookings">
          View my bookings
        </Button>
        <Button as="a" href="/" variant="ghost">Return home</Button>
      </div>
    </div>
  );
};

export default BookingConfirmation;
