import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { getSpaServices } from '../../api/spaApi';
import { getSlots } from '../../api/bookingApi';

export type Mode = 'INSTANT' | 'REQUEST' | 'ENQUIRY';
export const modeText = {
  INSTANT: 'Instant booking',
  REQUEST: 'Request to book',
  ENQUIRY: 'Enquiry only'
};
export function usePolicy(spa?: string) {
  return useQuery({
    queryKey: ['booking-policy', spa],
    enabled: !!spa,
    queryFn: async () =>
      (
        await client.get<{ mode: Mode; phone?: string; email?: string; timezone: string }>(
          '/api/spas/' + spa + '/booking-policy'
        )
      ).data
  });
}
export function BookingPolicy({ spa }: { spa: string }) {
  const current = usePolicy(spa);
  const [selection, setSelection] = useState<Mode>();
  const [verified, setVerified] = useState(false);
  const mode = selection ?? current.data?.mode;
  const save = useMutation({
    mutationFn: () => client.put('/api/owner/spas/' + spa + '/booking-policy', { mode, verified }),
    onSuccess: () => current.refetch()
  });
  return (
    <section className="operations-form card p-5 space-y-3">
      <h3>Booking policy</h3>
      <label>
        Customer booking mode{' '}
        <select
          value={mode || ''}
          onChange={(e) => {
            setSelection(e.target.value as Mode);
            setVerified(false);
            save.reset();
          }}
        >
          {Object.entries(modeText).map(([v, n]) => (
            <option key={v} value={v}>
              {n}
            </option>
          ))}
        </select>
      </label>
      {mode !== 'ENQUIRY' && (
        <label className="block">
          <input
            type="checkbox"
            checked={verified}
            onChange={(e) => setVerified(e.target.checked)}
          />{' '}
          I verified the prices, working hours, room and therapist capacity, and a working
          notification channel.
        </label>
      )}
      <button
        className="btn-primary"
        disabled={!mode || save.isPending || (mode !== 'ENQUIRY' && !verified)}
        onClick={() => save.mutate()}
      >
        Save booking policy
      </button>
      {save.isSuccess && <p role="status">Booking policy saved.</p>}
      {(current.isError || save.isError) && (
        <p role="alert">{errorMessage(current.error || save.error)}</p>
      )}
    </section>
  );
}
interface RequestRow {
  id: number;
  spaName: string;
  service: string;
  starts: string;
  deadline: string;
  status: string;
  bookingId?: number;
}
export function Requests({ spa }: { spa?: string }) {
  const path = spa
    ? '/api/owner/spas/' + spa + '/appointment-requests'
    : '/api/me/appointment-requests';
  const rows = useQuery({
    queryKey: [path],
    queryFn: async () => (await client.get<RequestRow[]>(path)).data,
    refetchInterval: 30000
  });
  const qc = useQueryClient();
  const answer = useMutation({
    mutationFn: ({ id, accept }: { id: number; accept: boolean }) =>
      client.post(path + '/' + id, { accept }),
    onSuccess: () => qc.invalidateQueries()
  });
  if (rows.isError) return <p role="alert">{errorMessage(rows.error)}</p>;
  if (!rows.data?.length) return null;
  return (
    <section className="operations-form card p-5 space-y-3">
      <h3>Appointment requests</h3>
      {rows.data.map((r) => (
        <div key={r.id}>
          <strong>
            {r.service} · {r.spaName}
          </strong>{' '}
          · {new Date(r.starts).toLocaleString()} · {r.status}
          {r.status === 'PENDING' && (
            <>
              {' '}
              · Reply by {new Date(r.deadline).toLocaleString()}
              {spa &&
                [true, false].map((accept) => (
                  <button
                    className="ml-2 underline"
                    key={String(accept)}
                    disabled={answer.isPending}
                    onClick={() => answer.mutate({ id: r.id, accept })}
                  >
                    {accept ? 'Accept' : 'Decline'}
                  </button>
                ))}
            </>
          )}
        </div>
      ))}
      {answer.isError && <p role="alert">{errorMessage(answer.error)}</p>}
    </section>
  );
}
export function AppointmentForm({ spa, desk = false }: { spa: string; desk?: boolean }) {
  const [service, setService] = useState(''),
    [date, setDate] = useState(''),
    [slot, setSlot] = useState(''),
    [therapist, setTherapist] = useState('');
  const [channel, setChannel] = useState('WALK_IN'),
    [name, setName] = useState(''),
    [contact, setContact] = useState(''),
    [guest, setGuest] = useState('');
  const [key, setKey] = useState(() => crypto.randomUUID());
  const qc = useQueryClient();
  const policy = usePolicy(spa);
  const services = useQuery({
    queryKey: ['spa-services', spa],
    queryFn: () => getSpaServices(spa)
  });
  const slots = useQuery({
    queryKey: ['ops-slots', spa, service, date],
    enabled: !!date && !!service,
    queryFn: () => getSlots(spa, service, date)
  });
  const rooms = useQuery({
    queryKey: ['desk-rooms', spa, service, date, slots.dataUpdatedAt],
    enabled: desk && !!slots.data?.length,
    queryFn: async () =>
      (
        await client.get<{ id: number; room: string }[]>('/api/owner/spas/' + spa + '/desk-rooms', {
          params: { service, date }
        })
      ).data
  });
  const team = useQuery({
    queryKey: ['desk-slot-team', spa, slot],
    enabled: desk && !!slot,
    queryFn: async () =>
      (
        await client.get<{ id: number; name: string }[]>(
          '/api/owner/spas/' + spa + '/desk-slots/' + slot + '/therapists'
        )
      ).data
  });
  const guests = useQuery({
    queryKey: ['desk-guests', spa, name],
    enabled: desk && name.trim().length >= 2,
    queryFn: async () =>
      (
        await client.get<{ id: number; name: string }[]>(
          '/api/owner/spas/' + spa + '/desk-guests',
          { params: { search: name } }
        )
      ).data
  });
  const booked = useMutation({
    mutationFn: async () =>
      desk
        ? (
            await client.post('/api/owner/spas/' + spa + '/desk-appointments', {
              requestKey: key,
              slotId: Number(slot),
              therapistId: therapist ? Number(therapist) : null,
              channel,
              name,
              contact,
              guestId: guest ? Number(guest) : null
            })
          ).data
        : (
            await client.post('/api/spas/' + spa + '/appointment-requests', {
              slotId: Number(slot)
            })
          ).data.id,
    onSuccess: () => {
      setKey(crypto.randomUUID());
      setSlot('');
      void qc.invalidateQueries();
    }
  });
  return (
    <form
      className="operations-form card p-5 grid gap-3 sm:grid-cols-2"
      onSubmit={(e) => {
        e.preventDefault();
        booked.mutate();
      }}
    >
      <h3 className="sm:col-span-2">{desk ? 'New appointment' : 'Request an appointment'}</h3>
      {!desk && (
        <p className="sm:col-span-2">
          The spa must accept before the time is reserved. Pay at the spa. Track the reply in My
          bookings.
        </p>
      )}
      {desk && (
        <>
          <label>
            Booking source
            <select value={channel} onChange={(e) => setChannel(e.target.value)}>
              <option value="WALK_IN">Walk-in</option>
              <option value="PHONE">Phone</option>
            </select>
          </label>
          <label>
            Guest name or contact
            <input
              required
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                setGuest('');
              }}
            />
          </label>
          {!!guests.data?.length && (
            <label>
              Guest profile
              <select value={guest} onChange={(e) => setGuest(e.target.value)}>
                <option value="">New guest</option>
                {guests.data.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name} (#{g.id})
                  </option>
                ))}
              </select>
            </label>
          )}
          {!guest && (
            <label>
              Contact (optional)
              <input value={contact} onChange={(e) => setContact(e.target.value)} />
            </label>
          )}
        </>
      )}
      <label>
        Service
        <select
          required
          value={service}
          onChange={(e) => {
            setService(e.target.value);
            setSlot('');
            setTherapist('');
          }}
        >
          <option value="">Select service</option>
          {services.data?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Appointment date
        <input
          required
          type="date"
          value={date}
          onChange={(e) => {
            setDate(e.target.value);
            setSlot('');
            setTherapist('');
          }}
        />
      </label>
      <label>
        Available time
        <select
          required
          value={slot}
          onChange={(e) => {
            setSlot(e.target.value);
            setTherapist('');
          }}
        >
          <option value="">Select time</option>
          {slots.data
            ?.filter((s) => s.available)
            .map((s) => (
              <option key={s.id} value={s.id}>
                {new Date(s.startTime).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                  timeZone: policy.data?.timezone || 'Asia/Kolkata',
                  timeZoneName: 'short'
                })}
                {desk &&
                  ' · ' + (rooms.data?.find((r) => String(r.id) === s.id)?.room || 'Slot #' + s.id)}
              </option>
            ))}
        </select>
      </label>
      {desk && (
        <label>
          Therapist
          <select value={therapist} onChange={(e) => setTherapist(e.target.value)}>
            <option value="">Any available</option>
            {team.data?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
      )}
      <button className="btn-primary" disabled={booked.isPending || !slot}>
        {desk ? 'Book appointment' : 'Send request'}
      </button>
      {booked.isSuccess && (
        <p role="status">
          {desk ? 'Booked appointment' : 'Submitted request'} #{booked.data}
        </p>
      )}
      {(booked.isError || slots.isError || services.isError) && (
        <p role="alert">{errorMessage(booked.error || slots.error || services.error)}</p>
      )}
    </form>
  );
}
