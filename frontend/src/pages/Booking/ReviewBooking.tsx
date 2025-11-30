import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';

interface ReviewBookingProps {
  onNext: () => void;
  onBack: () => void;
}

const ReviewBooking = ({ onNext, onBack }: ReviewBookingProps) => {
  const { state } = useBookingFlow();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">Review your booking</h2>
          <p className="text-sm text-slate-600">Confirm details before payment.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onBack} size="sm">
            Back
          </Button>
          <Button onClick={onNext} size="sm" disabled={!state.slot || !state.service}>
            Continue
          </Button>
        </div>
      </div>

      <div className="space-y-3">
        <div className="rounded-2xl border border-gray-100 bg-gray-50 p-4">
          <p className="text-sm text-slate-500">Spa</p>
          <p className="text-lg font-semibold text-slate-900">{state.spaName}</p>
        </div>
        {state.service && (
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-4">
            <p className="text-sm text-slate-500">Service</p>
            <p className="text-lg font-semibold text-slate-900">{state.service.name}</p>
            <p className="text-sm text-slate-500">{state.service.durationMinutes} mins · ${state.service.price.toFixed(2)}</p>
          </div>
        )}
        {state.slot && (
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-4">
            <p className="text-sm text-slate-500">Date & time</p>
            <p className="text-lg font-semibold text-slate-900">
              {new Date(state.slot).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
            </p>
          </div>
        )}
        {state.therapistType && (
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-4">
            <p className="text-sm text-slate-500">Therapist</p>
            <p className="text-lg font-semibold text-slate-900">
              {state.therapistType === 'ANY' ? 'Any available therapist' : 'Selected therapist'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default ReviewBooking;
