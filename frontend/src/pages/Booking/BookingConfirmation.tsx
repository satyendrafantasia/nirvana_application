import Button from '../../components/common/Button';
import { BookingSummary } from '../../types/booking';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { useState } from 'react';
import HealthForm from '../../components/health/HealthForm';
import BookedAddOnLines from '../../components/booking/BookedAddOnLines';

interface BookingConfirmationProps {
  summary: BookingSummary | null;
  onRestart: () => void;
}

const BookingConfirmation = ({ summary, onRestart }: BookingConfirmationProps) => {
  const { reset } = useBookingFlow();
  // Offered once the booking exists; the guest may skip it and finish it from My bookings before the visit.
  const [healthOpen, setHealthOpen] = useState(false);

  const handleDone = () => {
    reset();
    onRestart();
  };

  return (
    <div className="space-y-6 text-center">
      <div aria-hidden="true" className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary-50 text-3xl">
        {summary?.status === 'CONFIRMED' ? (
          <svg className="booking-confirmed text-primary-700" width="34" height="34" viewBox="0 0 32 32" fill="none">
            <path d="m6 16 7 7L26 9" pathLength="1" stroke="currentColor" strokeWidth="3"
              strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ) : '…'}
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
          <BookedAddOnLines addOns={summary.addOns} />
          <p className="text-sm text-slate-500">Date and time</p>
          <time dateTime={summary.slot}>{new Date(summary.slot).toLocaleString(undefined, { dateStyle: 'long', timeStyle: 'short' })}</time>
          {summary.therapistName && <p>Therapist: {summary.therapistName}</p>}
          {summary.totalCents != null && summary.currency && <p className="mt-3">Booking total · {new Intl.NumberFormat(undefined, { style: 'currency', currency: summary.currency }).format(summary.totalCents / 100)}</p>}
        </div>
      ) : (
        <p className="text-sm text-slate-500">
          Booking details will appear here after confirmation.
        </p>
      )}

      {summary?.id && (summary.status === 'CONFIRMED' || summary.status === 'PENDING_PAYMENT') && (
        <div className="mx-auto max-w-lg rounded-2xl border p-5 text-left">
          {healthOpen ? (
            <HealthForm bookingId={summary.id} onDone={() => setHealthOpen(false)} />
          ) : (
            <div className="space-y-2">
              <p className="font-semibold">Health and consent form</p>
              <p className="text-sm">
                Tell your therapist about allergies, pregnancy or health conditions. It takes two minutes, and you can
                also do it from My bookings any time before your visit.
              </p>
              <Button variant="secondary" onClick={() => setHealthOpen(true)}>
                Fill it in now
              </Button>
            </div>
          )}
        </div>
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
