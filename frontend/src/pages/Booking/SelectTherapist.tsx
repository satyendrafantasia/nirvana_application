import { useQuery } from '@tanstack/react-query';
import { getTherapists } from '../../api/bookingApi';
import TherapistCard from '../../components/booking/TherapistCard';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';

interface SelectTherapistProps {
  onNext: () => void;
  onBack: () => void;
}

const SelectTherapist = ({ onNext, onBack }: SelectTherapistProps) => {
  const { state, setTherapist } = useBookingFlow();

  const { data: therapists } = useQuery({
    queryKey: ['therapists', state.spaId],
    queryFn: () => getTherapists(state.spaId || ''),
    enabled: Boolean(state.spaId)
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">Choose therapist</h2>
          <p className="text-sm text-slate-600">Select your preferred therapist or let the spa assign.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onBack} size="sm">
            Back
          </Button>
          <Button onClick={onNext} size="sm">
            Continue
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {therapists?.map((therapist) => (
          <TherapistCard
            key={therapist.id}
            therapist={therapist}
            selected={state.therapistId === therapist.id}
            onSelect={(id) => setTherapist(id, 'SPECIFIC')}
          />
        ))}
        <button
          onClick={() => setTherapist(undefined, 'ANY')}
          className={`rounded-2xl border p-4 text-left shadow-sm transition hover:border-primary-200 ${
            state.therapistType === 'ANY' ? 'border-primary-500 bg-primary-50' : 'border-gray-200'
          }`}
        >
          <p className="text-base font-semibold text-slate-800">No preference</p>
          <p className="text-sm text-slate-500">Let the spa assign the best available therapist.</p>
        </button>
      </div>
    </div>
  );
};

export default SelectTherapist;
