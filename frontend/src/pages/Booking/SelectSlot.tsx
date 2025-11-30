import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getSlots } from '../../api/bookingApi';
import SlotGrid from '../../components/booking/SlotGrid';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import { useBookingFlow } from '../../hooks/useBookingFlow';

interface SelectSlotProps {
  onNext: () => void;
  onBack: () => void;
}

const SelectSlot = ({ onNext, onBack }: SelectSlotProps) => {
  const { state, setSlot } = useBookingFlow();
  const [date, setDate] = useState<string>(() => new Date().toISOString().slice(0, 10));

  const { data: slots, isLoading } = useQuery({
    queryKey: ['slots', state.spaId, date],
    queryFn: () => getSlots(state.spaId || '', date),
    enabled: Boolean(state.spaId)
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">Select a time</h2>
          <p className="text-sm text-slate-600">Pick a date and slot for your visit.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onBack} size="sm">
            Back
          </Button>
          <Button onClick={onNext} disabled={!state.slot} size="sm">
            Continue
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Input
          type="date"
          label="Date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />
      </div>

      <div>
        {isLoading && <p className="text-sm text-slate-500">Loading slots...</p>}
        {slots && slots.length > 0 ? (
          <SlotGrid slots={slots} selectedSlot={state.slot} onSelect={setSlot} />
        ) : (
          <p className="text-sm text-slate-500">No slots available for the selected date.</p>
        )}
      </div>
    </div>
  );
};

export default SelectSlot;
