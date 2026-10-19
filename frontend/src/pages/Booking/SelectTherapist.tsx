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
          onClick={() => setTherapist()}
        >
          No preference
        </Button>
        {slot?.therapistSelectionEnabled &&
          slot.therapists
            .filter((t) => t.available)
            .map((t) => (
              <Button
                key={t.id}
                variant={state.therapistId === t.id ? 'primary' : 'secondary'}
                onClick={() => setTherapist(t.id)}
              >
                {t.name} · {t.type}
              </Button>
            ))}
        {slot?.therapistTypeSelectionEnabled &&
          slot.therapistTypes
            .filter((t) => t.availableCount > 0)
            .map((t) => (
              <Button
                key={t.type}
                variant={state.therapistType === t.type ? 'primary' : 'secondary'}
                onClick={() => setTherapist(undefined, t.type)}
              >
                {t.type} therapist
              </Button>
            ))}
      </div>
      <div className="flex gap-3">
        <Button variant="secondary" onClick={onBack}>
          Back
        </Button>
        <Button onClick={onNext}>Review appointment</Button>
      </div>
    </div>
  );
}
