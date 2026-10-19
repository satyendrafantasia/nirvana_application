import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { getSlots } from '../../api/bookingApi';
import client, { errorMessage } from '../../api/client';
import { Link } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import SlotGrid from '../../components/booking/SlotGrid';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import { useBookingFlow } from '../../hooks/useBookingFlow';

interface SelectSlotProps {
  onNext: () => void;
  onBack: () => void;
}

const SelectSlot = ({ onNext, onBack }: SelectSlotProps) => {
  const { state, setSlot, setGuests, setHold } = useBookingFlow();
  const [date, setDate] = useState<string>(() => new Date().toISOString().slice(0, 10));

  const {
    data: slots,
    isLoading,
    isError
  } = useQuery({
    queryKey: ['slots', state.spaId, state.service?.id, date, state.guests],
    queryFn: () => getSlots(state.spaId || '', state.service?.id || '', date, state.guests),
    enabled: Boolean(state.spaId)
  });

  const { user } = useAuth();
  const hold = useMutation({
    mutationFn: async () => {
      if (state.holdToken && state.holdExpiresAt && new Date(state.holdExpiresAt) > new Date()) {
        onNext();
        return;
      }
      const { data } = await client.post(
        `/api/spas/${state.spaId}/services/${state.service?.id}/slots/${state.selectedSlot?.id}/hold`,
        {
          spaId: state.spaId,
          serviceId: state.service?.id,
          slotId: state.selectedSlot?.id,
          guests: state.guests,
          holdMinutes: 10
        }
      );
      setHold(data.holdToken, data.expiresAt);
      onNext();
    }
  });
  const wait = useMutation({
    mutationFn: async (slotId: string) =>
      client.post('/api/waitlist', {
        spaId: state.spaId,
        serviceId: state.service?.id,
        slotId,
        guests: state.guests,
        contactEmail: user?.email
      })
  });
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">Select a time</h2>
          <p className="text-sm text-slate-600">
            Pick a date and slot for your visit. Times shown in{' '}
            {Intl.DateTimeFormat().resolvedOptions().timeZone}.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onBack} size="sm">
            Back
          </Button>
          <Button onClick={() => hold.mutate()} disabled={!state.slot || hold.isPending} size="sm">
            Continue
          </Button>
        </div>
      </div>

      <p className="text-sm text-slate-600">
        Continuing reserves your selected time for 10 minutes.
      </p>
      {(hold.isError || wait.isError) && (
        <p role="alert" className="text-rose-700">
          {errorMessage(hold.error || wait.error)}
        </p>
      )}
      {wait.isSuccess && <p role="status">You have joined the waitlist.</p>}
      <Link className="text-primary-700 underline" to="/features/availability">
        View a full week of availability
      </Link>
      <div className="grid gap-4 md:grid-cols-3">
        <Input
          type="date"
          label="Date"
          value={date}
          min={new Date().toLocaleDateString('en-CA')}
          onChange={(e) => {
            setDate(e.target.value);
            setSlot(undefined);
          }}
        />
      </div>

      <Input
        type="number"
        label="Guests"
        min={state.service?.minPersons || 1}
        max={state.service?.maxPersons || 1}
        value={state.guests}
        onChange={(e) => setGuests(Math.max(1, Number(e.target.value)))}
      />
      <div>
        {isError && (
          <p role="alert">Unable to load availability. Please try another date or retry.</p>
        )}
        {isLoading && <p className="text-sm text-slate-500">Loading slots...</p>}
        {slots && slots.length > 0 ? (
          <SlotGrid slots={slots} selectedSlot={state.selectedSlot?.id} onSelect={setSlot} />
        ) : (
          <p className="text-sm text-slate-500">No slots available for the selected date.</p>
        )}
      </div>
      {slots
        ?.filter((s) => !s.available)
        .map((s) => (
          <div key={s.id} className="flex gap-3 items-center">
            <span>{new Date(s.startTime).toLocaleString()} — unavailable</span>
            <Button variant="ghost" disabled={wait.isPending} onClick={() => wait.mutate(s.id)}>
              Join waitlist
            </Button>
          </div>
        ))}
    </div>
  );
};

export default SelectSlot;
