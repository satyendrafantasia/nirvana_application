import { uniqueAvailableTimes } from './availableTimes';
import { track } from '../../telemetry';
import { useRef } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { getSlots, getTherapistDirectory, isSlotConflict } from '../../api/bookingApi';
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
  const { state, setSlot, setGuests, setHold, setTherapist, setAvailabilityDate } = useBookingFlow();
  const preferred = state.therapistType ? 'type:' + state.therapistType : state.therapistId || '';
  const date = state.availabilityDate ?? new Date().toLocaleDateString('en-CA');
  const reserving = useRef(false);
  const chosen = state.addOns ?? [];
  const addOnIds = chosen.map((a) => a.id);
  const extraMinutes = chosen.reduce((sum, a) => sum + a.extraMinutes, 0);

  const {
    data: slots,
    isLoading,
    isError,
    refetch
  } = useQuery({
    queryKey: ['slots', state.spaId, state.service?.id, date, state.guests, addOnIds],
    queryFn: () => getSlots(state.spaId || '', state.service?.id || '', date, state.guests, addOnIds),
    refetchInterval: 15000,
    refetchOnWindowFocus: true,
    staleTime: 0,
    enabled: Boolean(state.spaId && state.service && date)
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
          addOnIds,
          therapistId: preferred && !preferred.startsWith('type:') ? Number(preferred) : undefined,
          therapistType: preferred.startsWith('type:') ? preferred.slice(5) : undefined,
          holdMinutes: 10
        }
      );
      setHold(data.holdToken, data.expiresAt, data.therapistName);
      onNext();
    },
    onError: (error) => {
      if (isSlotConflict(error)) setSlot(undefined);
      void refetch();
    },
    onSettled: () => { reserving.current = false; }
  });
  const directory = useQuery({
    queryKey: ['therapist-directory', state.spaId, state.service?.id],
    queryFn: () => getTherapistDirectory(state.spaId!, state.service!.id),
    enabled: Boolean(state.spaId && state.service),
    refetchInterval: 15000
  });
  const options = directory.data?.named || [];
  const types = directory.data?.labels || [];
  const directoryName = options.find(t => String(t.id) === preferred)?.name;
  const selectedName = directoryName || state.preferredTherapistName;
  const times = uniqueAvailableTimes(slots || [], preferred);
  const activeHold = Boolean(
    state.holdToken && state.holdExpiresAt && new Date(state.holdExpiresAt) > new Date()
  );
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
      <div className="flex flex-wrap gap-3 items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">Select a time</h2>
          <p className="text-sm text-slate-600">
            Pick a date and slot for your visit. Times shown in{' '}
            {Intl.DateTimeFormat().resolvedOptions().timeZone}.
          </p>
          {extraMinutes > 0 && state.service && (
            <p className="text-sm text-slate-600">
              Showing times with room for your {state.service.durationMinutes + extraMinutes}-minute appointment, add-ons included.
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" disabled={hold.isPending} onClick={onBack} size="sm">
            Back
          </Button>
          <Button
            pressFeedback
            onClick={() => {
              if (reserving.current) return;
              reserving.current = true;
              hold.mutate();
            }}
            disabled={
              !state.slot ||
              hold.isPending ||
              (!activeHold && !times.some((s) => s.id === state.selectedSlot?.id))
            }
            size="sm"
          >
            {hold.isPending ? 'Reserving…' : 'Continue'}
          </Button>
        </div>
      </div>

      <p className="text-sm text-slate-600" role="status">
        {state.selectedSlot && <>
          {new Date(state.selectedSlot.startTime).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}
          {' · '}{selectedName || state.therapistType || (preferred ? 'Selected therapist' : 'Any available therapist')}.{' '}
        </>}
        Continuing reserves your selected time for 10 minutes.
      </p>
      <span className="sr-only" role="status">{hold.isPending ? 'Reserving your time…' : ''}</span>
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
        {directory.isError && <p role="alert">{errorMessage(directory.error)}</p>}
          <label>
            Therapist
            <select
              aria-label="Therapist"
              className="block rounded-xl border p-2"
              disabled={hold.isPending}
              value={preferred}
              onChange={(e) => {
                const choice = e.target.value;
                const named = options.find(t => String(t.id) === choice);
                setTherapist(named && String(named.id), choice.startsWith('type:') ? choice.slice(5) : undefined, named?.name);
                setSlot(undefined);
              }}
            >
              <option value="">Any available therapist</option>
              {preferred && !directoryName && !types.some(label => 'type:' + label === preferred) &&
                <option value={preferred}>{selectedName || state.therapistType || 'Previously selected therapist'}</option>}
              {types.map((type) => (
                <option key={type} value={'type:' + type}>
                  {type}
                </option>
              ))}
              {options.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                  {t.type ? ' · ' + t.type : ''}
                </option>
              ))}
            </select>
          </label>
        <Input
          type="date"
          disabled={hold.isPending}
          label="Date"
          value={date}
          min={new Date().toLocaleDateString('en-CA')}
          onChange={(e) => setAvailabilityDate(e.target.value)}
        />
      </div>

      <Input
        type="number"
        disabled={hold.isPending}
        label="Guests"
        min={state.service?.minPersons || 1}
        max={state.service?.maxPersons || 1}
        value={state.guests}
        onChange={(e) =>
          setGuests(
            Math.min(
              state.service?.maxPersons || 1,
              Math.max(state.service?.minPersons || 1, Math.floor(Number(e.target.value)))
            )
          )
        }
      />
      <div>
        {isError && (
          <p role="alert">
            Availability could not be loaded.{' '}
            <button className="underline" onClick={() => void refetch()}>
              Retry
            </button>
          </p>
        )}
        {isLoading && (
          <p role="status" className="text-sm text-slate-500">
            Loading slots...
          </p>
        )}
        {times.length > 0 ? (
          <SlotGrid
            slots={times}
            selectedSlot={state.selectedSlot?.id}
            onSelect={(slot) => {
              if (hold.isPending) return;
              setSlot(slot);
              track('select_appointment_slot');
            }}
          />
        ) : (
          !isError &&
          !isLoading && (
            <p role="status" className="text-sm text-slate-500">
              {preferred ? `${selectedName || (preferred.startsWith('type:') ? preferred.slice(5) : 'Selected therapist')} has no available times on this date. Choose another date or therapist.` : 'No slots available for the selected date.'}
            </p>
          )
        )}
      </div>
      {slots
        ?.filter((s) => !preferred && !s.available)
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
