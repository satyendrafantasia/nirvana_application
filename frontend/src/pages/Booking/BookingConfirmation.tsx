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
      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary-50 text-3xl">🎉</div>
      <h2 className="text-3xl font-semibold text-slate-900">Booking confirmed</h2>
      <p className="text-slate-600">Your reference is below. A confirmation has been sent to your email.</p>

      {summary ? (
        <div className="mx-auto max-w-lg rounded-2xl bg-gray-50 p-5 text-left">
          <p className="text-sm text-slate-500">Booking ID</p>
          <p className="text-lg font-semibold text-slate-900">{summary.id}</p>
          <p className="text-sm text-slate-500">Spa</p>
          <p className="text-lg font-semibold text-slate-900">{summary.spaName}</p>
          <p className="text-sm text-slate-500">Service</p>
          <p className="text-lg font-semibold text-slate-900">{summary.service.name}</p>
        </div>
      ) : (
        <p className="text-sm text-slate-500">Booking details will appear here after confirmation.</p>
      )}

      <div className="flex justify-center gap-3">
        <Button onClick={handleDone}>Make another booking</Button>
        <Button variant="secondary" as="a" href="/me/bookings">
          View my bookings
        </Button>
      </div>
    </div>
  );
};

export default BookingConfirmation;
