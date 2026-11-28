import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { therapistLabel } from './therapistLabel';
import { addOnTotals, extraTime } from '../../api/addOnApi';
import { money } from '../../api/rewardsApi';

interface ReviewBookingProps {
  onNext: () => void;
  onBack: () => void;
}

const ReviewBooking = ({ onNext, onBack }: ReviewBookingProps) => {
  const { state } = useBookingFlow();
  const namedTherapist = state.selectedSlot?.therapists.find(t => t.id === state.therapistId);
  const therapist = state.heldTherapistName || namedTherapist?.name || state.preferredTherapistName ||
    (state.therapistType ? therapistLabel(state.therapistType) :
      state.therapistId ? 'Selected therapist' : 'Any available therapist');
  const addOns = state.addOns ?? [];
  const extras = addOnTotals(addOns, state.guests);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-3 items-center justify-between">
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
            <p className="text-sm text-slate-500">
              {state.service.durationMinutes} mins ·{' '}
              {new Intl.NumberFormat('en-IN', {
                style: 'currency',
                currency: state.service.currency
              }).format(state.service.price)}{' '}
              per guest · {state.guests} guest(s)
            </p>
          </div>
        )}
        {state.service && addOns.length > 0 && (
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-4">
            <p className="text-sm text-slate-500">Add-ons</p>
            <ul className="space-y-1">
              {addOns.map((a) => (
                <li key={a.id} className="flex justify-between gap-3 text-slate-900">
                  <span>
                    {a.name}
                    {a.extraMinutes > 0 && <span className="ml-2 text-sm text-slate-500">{extraTime(a.extraMinutes)}</span>}
                  </span>
                  <span>
                    {money(a.priceCents * Math.max(1, state.guests), a.currency)}
                    {state.guests > 1 && <span className="text-sm text-slate-500"> ({money(a.priceCents, a.currency)} × {state.guests})</span>}
                  </span>
                </li>
              ))}
            </ul>
            {extras.minutes > 0 && (
              <p className="mt-1 text-sm text-slate-500">Appointment length: {state.service.durationMinutes + extras.minutes} mins</p>
            )}
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
        {state.selectedSlot && (
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-4">
            <p className="text-sm text-slate-500">Therapist</p>
            <p className="text-lg font-semibold text-slate-900">
              {therapist}
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default ReviewBooking;
