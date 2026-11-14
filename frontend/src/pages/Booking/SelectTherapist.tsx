import { therapistLabel } from './therapistLabel';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
export default function SelectTherapist({
  onNext,
  onBack
}: {
  onNext: () => void;
  onBack: () => void;
}) {
  const { state, setTherapist } = useBookingFlow();
  const slot = state.selectedSlot;
  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-semibold">Choose your therapist</h2>
      <p>Availability is specific to your selected service and time.</p>
      <div className="grid gap-3 md:grid-cols-2">
        <Button
          variant={!state.therapistId && !state.therapistType ? 'primary' : 'secondary'}
          aria-pressed={!state.therapistId && !state.therapistType}
          onClick={() => setTherapist()}
        >
          Any
        </Button>
        {slot?.therapistTypeSelectionEnabled &&
          slot.therapistTypes
            .filter((t) => t.availableCount > 0)
            .map((t) => (
              <Button
                key={t.type}
                variant={state.therapistType === t.type ? 'primary' : 'secondary'}
                aria-pressed={state.therapistType === t.type}
                onClick={() => setTherapist(undefined, t.type)}
              >
                {therapistLabel(t.type)}
              </Button>
            ))}
      </div>
      {slot?.therapistSelectionEnabled && slot.therapists.some(t => t.available) && (
        <details><summary className="cursor-pointer">Choose a specific therapist</summary>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
        {slot?.therapistSelectionEnabled &&
          slot.therapists
            .filter((t) => t.available)
            .map((t) => (
              <Button
                key={t.id}
                variant={state.therapistId === t.id ? 'primary' : 'secondary'}
                onClick={() => setTherapist(t.id)}
              >
                {t.name}{t.type && ' · ' + therapistLabel(t.type)}
              </Button>
            ))}
          </div>
        </details>
      )}
      <div className="flex flex-wrap gap-3">
        <Button variant="secondary" onClick={onBack}>
          Back
        </Button>
        <Button onClick={onNext}>Review appointment</Button>
      </div>
    </div>
  );
}
