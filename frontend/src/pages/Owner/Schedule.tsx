import { showToast } from '../../components/common/Toast';
import { FormEvent, useState } from 'react';
import { Temporal } from '@js-temporal/polyfill';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { RoomTools, WeeklyRoster, Room } from './RoomTools';
import Button from '../../components/common/Button';
import { ScheduleGroupBadges } from './GroupLinks';

type Interval = { kind: 'WORK' | 'BREAK' | 'TIME_OFF'; start: string; end: string };
type Therapist = {
  therapistId: number;
  name: string;
  working: boolean;
  enabled: boolean;
  inherited: boolean;
  intervals: Interval[];
};
type Slot = {
  id: number;
  serviceId: number;
  service: string;
  start: string;
  end: string;
  capacity: number;
  booked: number;
  held: number;
  remaining: number;
  closed: boolean;
  status: string;
  bookingIds: number[];
  roomId: number | null;
  appointments: string[];
  therapistId: number | null;
};
type Day = { date: string; timezone: string; slots: Slot[]; therapists: Therapist[] };
type Service = { id: number; name: string; active: boolean; durationMin: number };
type Lookup = { therapists: { therapistId: number; name: string }[] };
const labels = { WORK: 'Shift', BREAK: 'Break', TIME_OFF: 'Time off' };
const input = 'block mt-1 w-full rounded-lg border bg-white p-2';
const title = 'text-xl font-semibold';
const panel = 'card p-4 md:p-6 space-y-4';
function wall(ts: string, zone: string) {
  return Temporal.Instant.from(ts)
    .toZonedDateTimeISO(zone)
    .toPlainDateTime()
    .toString({ smallestUnit: 'minute' });
}
export function instant(value: string, zone: string, original?: string) {
  if (original && wall(original, zone) === value) return original;
  try {
    return Temporal.PlainDateTime.from(value)
      .toZonedDateTime(zone, { disambiguation: 'reject' })
      .toInstant()
      .toString();
  } catch {
    throw new Error(
      'Choose a valid local time. This time may fall within a daylight-saving clock change.'
    );
  }
}
function clock(ts: string, day: Day) {
  const local = wall(ts, day.timezone);
  return local.startsWith(day.date) ? local.slice(11) : local.replace('T', ' ');
}
function message(e: unknown) {
  return e instanceof Error && !('response' in e) ? e.message : errorMessage(e);
}
export default function Schedule({ spaId }: { spaId: string }) {
  const [date, setDate] = useState('');
  const base = '/api/owner/spas/' + spaId + '/schedule';
  const schedule = useQuery({
    refetchInterval: 30000,
    refetchOnWindowFocus: true,
    queryKey: ['owner-schedule', spaId, date],
    queryFn: async () =>
      (await client.post<Day>(base + '/prepare', null, { params: date ? { date } : {} })).data
  });
  return (
    <section className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">Daily schedule</h2>
          {schedule.data && <p>Local time · {schedule.data.timezone}</p>}
        </div>
        <div className="flex items-end gap-2">
          <label>
            Schedule date
            <input
              type="date"
              className={input}
              value={date || schedule.data?.date || ''}
              onChange={(e) => setDate(e.target.value)}
            />
          </label>
          <Button
            variant="secondary"
            onClick={() => {
              setDate('');
              if (!date) void schedule.refetch();
            }}
          >
            Today
          </Button>
        </div>
      </header>
      {schedule.isPending ? (
        <p role="status">Loading schedule…</p>
      ) : schedule.isError ? (
        <p role="alert">
          {errorMessage(schedule.error)} <button onClick={() => schedule.refetch()}>Retry</button>
        </p>
      ) : (
        <Daily key={spaId + schedule.data.date} day={schedule.data} base={base} spaId={spaId} />
      )}
    </section>
  );
}
function Daily({ day, base, spaId }: { day: Day; base: string; spaId: string }) {
  const qc = useQueryClient();
  const [slotEdit, setSlot] = useState<number | 'new'>();
  const [staffEdit, setStaff] = useState<number>();
  const [roomFilter, setRoomFilter] = useState('all');
  const rooms = useQuery({
    queryKey: ['rooms', spaId],
    queryFn: async () => (await client.get<Room[]>(base + '/rooms')).data
  });
  const [lookupId, setLookup] = useState<number>();
  const [notice, setNotice] = useState('');
  const services = useQuery({
    queryKey: ['owner-catalog', spaId, 'services'],
    queryFn: async () =>
      (await client.get<Service[]>('/api/owner/spas/' + spaId + '/services')).data
  });
  type Edit = {
    path: string;
    method: 'patch' | 'post' | 'put' | 'delete';
    body: unknown;
    success: string;
  };
  const save = useMutation({
    mutationFn: async (e: Edit) =>
      client.request({ url: base + e.path, method: e.method, data: e.body }),
    onMutate: () => setNotice(''),
    onSuccess: (_, e) => {
      setNotice(e.success);
      showToast(e.success, 'success');
      setSlot(undefined);
      setStaff(undefined);
    },
    onSettled: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['owner-schedule', spaId] }),
        qc.invalidateQueries({ queryKey: ['slot-team', spaId] }),
        qc.invalidateQueries({ queryKey: ['slots'] })
      ]);
    }
  });
  const selected = day.slots.find((s) => s.id === lookupId);
  const team = useQuery({
    queryKey: ['slot-team', spaId, day.date, lookupId],
    enabled: !!selected,
    queryFn: async () =>
      (
        await client.get<Lookup[]>(base + '/available-therapists', {
          params: { serviceId: selected!.serviceId, slotId: lookupId }
        })
      ).data
  });
  function startEdit() {
    setNotice('');
    save.reset();
    setSlot(undefined);
    setStaff(undefined);
  }
  const working = day.therapists.filter((t) => t.working).length;
  return (
    <fieldset disabled={save.isPending} className="min-w-0 space-y-5">
      {save.isError && (
        <p role="alert" className="rounded-xl bg-red-50 p-3 text-red-800">
          {message(save.error)}
        </p>
      )}
      {notice && (
        <p role="status" className="rounded-xl bg-primary-50 p-3">
          {notice}
        </p>
      )}
      {rooms.isError && <p role="alert">{errorMessage(rooms.error)}</p>}
      <RoomTools
        base={base}
        date={day.date}
        rooms={rooms.data || []}
        services={services.data || []}
        refresh={async () => {
          await Promise.all([
            rooms.refetch(),
            qc.invalidateQueries({ queryKey: ['owner-schedule', spaId] }),
            qc.invalidateQueries({ queryKey: ['slots'] })
          ]);
        }}
      />
      <section className={panel}>
        <header className="flex flex-wrap items-center justify-between gap-3">
          <h3 className={title}>
            Appointment slots <small>({day.slots.length})</small>
          </h3>
          <Button
            onClick={() => {
              startEdit();
              setSlot('new');
            }}
          >
            Add slot
          </Button>
        </header>
        {slotEdit === 'new' &&
          (services.isError ? (
            <p role="alert">{errorMessage(services.error)}</p>
          ) : services.isPending ? (
            <p>Loading services…</p>
          ) : (
            <SlotForm
              rooms={rooms.data || []}
              base={base}
              day={day}
              services={services.data}
              cancel={() => setSlot(undefined)}
              submit={(body) =>
                save.mutate({ method: 'post', path: '/slots', body, success: 'Slot created.' })
              }
            />
          ))}
        {!day.slots.length && (
          <p>
            No appointments scheduled. Generate slots for one or more rooms, or add a single slot.
          </p>
        )}
        <label className="block">
          Room
          <select
            className={input}
            value={roomFilter}
            onChange={(e) => setRoomFilter(e.target.value)}
          >
            <option value="all">All rooms</option>
            {rooms.data?.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
            <option value="legacy">Unassigned legacy slots</option>
          </select>
        </label>
        <div className="hidden md:block max-w-full overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr>
                <th>Local time</th>
                {(rooms.data || [])
                  .filter((r) => roomFilter === 'all' || String(r.id) === roomFilter)
                  .map((r) => (
                    <th className="p-2" key={r.id}>
                      {r.name}
                    </th>
                  ))}
              </tr>
            </thead>
            <tbody>
              {[
                ...new Set(
                  day.slots
                    .filter(
                      (s) => s.roomId && (roomFilter === 'all' || String(s.roomId) === roomFilter)
                    )
                    .map((s) => s.start)
                )
              ]
                .sort()
                .map((at) => (
                  <tr className="border-t" key={at}>
                    <th>{clock(at, day)}</th>
                    {(rooms.data || [])
                      .filter((r) => roomFilter === 'all' || String(r.id) === roomFilter)
                      .map((r) => (
                        <td className="p-2" key={r.id}>
                          {day.slots
                            .filter((s) => s.roomId === r.id && s.start === at)
                            .map((s) => (
                              <a
                                key={s.id}
                                href={'#slot-' + s.id}
                                className="block rounded-lg p-2 bg-primary-50"
                              >
                                {s.closed
                                  ? 'Blocked'
                                  : s.booked
                                    ? 'Booked'
                                    : s.held
                                      ? 'Held'
                                      : 'Open'}{' '}
                                · {s.service} {s.appointments.join(', ')}
                              </a>
                            ))}
                        </td>
                      ))}
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
        <div className="divide-y">
          {day.slots
            .filter((s) => roomFilter === 'all' || String(s.roomId ?? 'legacy') === roomFilter)
            .map((s) => (
              <article id={'slot-' + s.id} key={s.id} className="py-4 space-y-2">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <strong>
                      {clock(s.start, day)} – {clock(s.end, day)}
                    </strong>
                    <p>
                      {s.service} ·{' '}
                      {rooms.data?.find((r) => r.id === s.roomId)?.name || 'Unassigned legacy room'}
                    </p>
                    <ScheduleGroupBadges spa={spaId} bookingIds={s.bookingIds} />
                  </div>
                  <span className="rounded-full bg-primary-50 px-3 py-1 text-sm">
                    {s.closed
                      ? 'Closed'
                      : s.booked
                        ? 'Booked'
                        : s.held
                          ? 'Held'
                          : s.status === 'HELD'
                            ? 'Held'
                            : s.status === 'BOOKED' || !s.remaining
                              ? 'Full'
                              : 'Open'}
                  </span>
                </div>
                <p className="text-sm">
                  Capacity {s.capacity} · Booked {s.booked} · Held {s.held} · Remaining{' '}
                  {s.remaining}
                </p>
                <p className="text-sm">
                  Therapist ·{' '}
                  {day.therapists.find((t) => t.therapistId === s.therapistId)?.name ||
                    'Any eligible therapist'}
                </p>
                {!!s.appointments.length && (
                  <p className="text-sm">Bookings {s.appointments.join(', ')}</p>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => {
                      startEdit();
                      setSlot(s.id);
                    }}
                  >
                    Edit slot
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => {
                      startEdit();
                      save.mutate({
                        method: 'patch',
                        path: '/slots/' + s.id,
                        body: { closed: !s.closed },
                        success: s.closed ? 'Slot reopened.' : 'Slot closed to new bookings.'
                      });
                    }}
                  >
                    {s.closed ? 'Reopen slot' : 'Block slot'}
                  </Button>
                  {!s.booked && !s.held && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        if (window.confirm('Delete this slot?'))
                          save.mutate({
                            method: 'delete',
                            path: '/slots/' + s.id,
                            body: null,
                            success: 'Slot deleted.'
                          });
                      }}
                    >
                      Delete slot
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-expanded={lookupId === s.id}
                    onClick={() => setLookup(lookupId === s.id ? undefined : s.id)}
                  >
                    Available therapists
                  </Button>
                </div>
                {lookupId === s.id && (
                  <p role="status">
                    {team.isPending
                      ? 'Checking availability…'
                      : team.isError
                        ? errorMessage(team.error)
                        : !team.data?.length
                          ? 'This slot is unavailable for booking.'
                          : team.data[0].therapists.map((t) => t.name).join(', ') ||
                            'No therapists available for the full appointment.'}
                  </p>
                )}
                {slotEdit === s.id && (
                  <SlotForm
                    rooms={rooms.data || []}
                    base={base}
                    day={day}
                    slot={s}
                    services={[]}
                    cancel={() => setSlot(undefined)}
                    submit={(body) =>
                      save.mutate({
                        method: 'patch',
                        path: '/slots/' + s.id,
                        body,
                        success: 'Slot updated.'
                      })
                    }
                  />
                )}
              </article>
            ))}
        </div>
      </section>
      <section className={panel}>
        <header>
          <h3 className={title}>Therapist roster</h3>
          <p>
            {working} working · {day.therapists.length} on team
          </p>
        </header>
        {!day.therapists.length && <p>Add your team in the Therapists tab to schedule shifts.</p>}
        <div className="divide-y">
          {day.therapists.map((t) => (
            <article key={t.therapistId} className="py-4 space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <strong>{t.name}</strong>
                  <p className="text-sm">
                    {!t.enabled
                      ? 'Unavailable in therapist settings'
                      : t.working
                        ? 'Working'
                        : 'Off for the day'}
                    {t.inherited && t.enabled ? ' · Spa hours' : ''}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    startEdit();
                    setStaff(t.therapistId);
                  }}
                >
                  Edit availability
                </Button>
              </div>
              <ul className="flex flex-wrap gap-2">
                {t.intervals.map((i, k) => (
                  <li key={k} className="rounded-lg bg-primary-50 px-2 py-1 text-sm">
                    {labels[i.kind]} · {clock(i.start, day)} – {clock(i.end, day)}
                  </li>
                ))}
              </ul>
              <WeeklyRoster
                base={base}
                id={t.therapistId}
                refresh={() => qc.invalidateQueries({ queryKey: ['owner-schedule', spaId] })}
              />
              {staffEdit === t.therapistId && (
                <RosterForm
                  day={day}
                  therapist={t}
                  cancel={() => setStaff(undefined)}
                  submit={(intervals) =>
                    save.mutate({
                      method: 'put',
                      path: '/therapists/' + t.therapistId + '/days/' + day.date,
                      body: { intervals },
                      success: 'Availability saved for ' + t.name + '.'
                    })
                  }
                />
              )}
            </article>
          ))}
        </div>
      </section>
    </fieldset>
  );
}
type SlotInput = {
  roomId?: number;
  therapistId?: number;
  serviceId?: number;
  start: string;
  end: string;
  capacity: number;
};
function SlotForm({
  rooms,
  base,
  day,
  slot,
  services,
  cancel,
  submit
}: {
  base: string;
  day: Day;
  rooms: Room[];
  slot?: Slot;
  services: Service[];
  cancel: () => void;
  submit: (v: SlotInput) => void;
}) {
  const [error, setError] = useState('');
  const activeServices = services.filter((s) => s.active);
  const [serviceId, setServiceId] = useState(activeServices[0]?.id);
  const now = Temporal.Now.zonedDateTimeISO(day.timezone).toPlainDateTime();
  const suggested = Temporal.PlainDateTime.from(day.date + 'T10:00');
  const initialStart = slot
    ? wall(slot.start, day.timezone)
    : (Temporal.PlainDateTime.compare(suggested, now) > 0
        ? suggested
        : now.add({ minutes: 1 })
      ).toString({ smallestUnit: 'minute' });
  const [startValue, setStartValue] = useState(initialStart);
  const duration = activeServices.find((s) => s.id === serviceId)?.durationMin || 60;
  const endFor = (value: string, minutes: number) =>
    value
      ? Temporal.PlainDateTime.from(value).add({ minutes }).toString({ smallestUnit: 'minute' })
      : '';
  const [endValue, setEndValue] = useState(() =>
    slot ? wall(slot.end, day.timezone) : endFor(initialStart, duration)
  );
  const [chosenTherapist, setChosenTherapist] = useState(String(slot?.therapistId ?? 0));
  const candidates = useQuery({
    queryKey: ['slot-therapists', base, slot?.id, slot?.start, slot?.end],
    enabled: !!slot,
    queryFn: async () =>
      (
        await client.get<{ id: number; name: string }[]>(
          base + '/slots/' + slot!.id + '/therapists'
        )
      ).data
  });
  function handle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    try {
      const start = instant(String(data.get('start')), day.timezone, slot?.start),
        end = instant(String(data.get('end')), day.timezone, slot?.end);
      if (Date.parse(end) <= Date.parse(start)) throw new Error('End must be after start.');
      setError('');
      submit({
        start,
        end,
        roomId: Number(data.get('room')),
        capacity: Number(data.get('capacity')),
        ...(slot ? { therapistId: Number(data.get('therapist')) } : {}),
        ...(!slot ? { serviceId: Number(data.get('service')) } : {})
      });
    } catch (err) {
      setError(message(err));
    }
  }
  return (
    <form onSubmit={handle} className="rounded-xl border p-4 space-y-3">
      <strong>{slot ? 'Edit slot' : 'New slot'}</strong>
      <div className="grid gap-3 sm:grid-cols-2">
        <label>
          Room
          <select name="room" required className={input} defaultValue={slot?.roomId ?? ''}>
            <option value="">Select room</option>
            {rooms
              .filter((r) => r.active || slot?.roomId === r.id)
              .map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
          </select>
        </label>
        {!slot && (
          <label>
            Service
            <select
              name="service"
              required
              className={input}
              value={serviceId ?? ''}
              onChange={(e) => {
                const id = Number(e.target.value);
                setServiceId(id);
                setEndValue(
                  endFor(startValue, activeServices.find((s) => s.id === id)?.durationMin || 60)
                );
              }}
            >
              <option value="">Select service</option>
              {services
                .filter((s) => s.active)
                .map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
            </select>
          </label>
        )}
        {slot && (
          <label>
            Slot therapist
            <select
              name="therapist"
              className={input}
              value={chosenTherapist}
              onChange={(e) => setChosenTherapist(e.target.value)}
            >
              <option value={0}>Any eligible therapist</option>
              {slot.therapistId && !candidates.data?.some((t) => t.id === slot.therapistId) && (
                <option value={slot.therapistId}>
                  {day.therapists.find((t) => t.therapistId === slot.therapistId)?.name}
                </option>
              )}
              {candidates.data?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
            {candidates.isPending
              ? 'Loading eligible therapists…'
              : candidates.isError
                ? errorMessage(candidates.error)
                : !candidates.data?.length
                  ? 'No eligible therapists for this time.'
                  : ''}
          </label>
        )}
        {(['start', 'end'] as const).map((k) => (
          <label key={k}>
            {k === 'start' ? 'Start' : 'End'}
            <input
              required
              name={k}
              type="datetime-local"
              className={input}
              value={k === 'start' ? startValue : endValue}
              onChange={(e) => {
                const value = e.target.value;
                if (k === 'end') setEndValue(value);
                else {
                  setStartValue(value);
                  if (!slot) setEndValue(endFor(value, duration));
                }
              }}
            />
          </label>
        ))}
        <label>
          Capacity
          <input
            required
            className={input}
            name="capacity"
            type="number"
            min="1"
            max="32767"
            defaultValue={slot?.capacity || 1}
          />
        </label>
      </div>
      {error && <p role="alert">{error}</p>}
      <div className="flex gap-2">
        <Button type="submit">Save slot</Button>
        <Button type="button" variant="secondary" onClick={cancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
function RosterForm({
  day,
  therapist,
  cancel,
  submit
}: {
  day: Day;
  therapist: Therapist;
  cancel: () => void;
  submit: (v: Interval[]) => void;
}) {
  const [rows, setRows] = useState(() =>
    therapist.intervals.map((i) => ({
      kind: i.kind,
      start: wall(i.start, day.timezone),
      end: wall(i.end, day.timezone),
      original: i as Interval | undefined
    }))
  );
  const [error, setError] = useState('');
  function handle(e: FormEvent) {
    e.preventDefault();
    try {
      const intervals = rows.map((r) => ({
        kind: r.kind,
        start: instant(r.start, day.timezone, r.original?.start),
        end: instant(r.end, day.timezone, r.original?.end)
      }));
      if (intervals.some((i) => Date.parse(i.end) <= Date.parse(i.start)))
        throw new Error('Each interval must end after it starts.');
      setError('');
      submit(intervals);
    } catch (err) {
      setError(message(err));
    }
  }
  return (
    <form onSubmit={handle} className="rounded-xl border p-4 space-y-3">
      <strong>Availability for {therapist.name}</strong>
      {!rows.length && <p>Off for the day</p>}
      {rows.map((r, n) => (
        <div className="grid items-end gap-2 md:grid-cols-[auto_1fr_1fr_auto]" key={n}>
          <strong>{labels[r.kind]}</strong>
          {(['start', 'end'] as const).map((k) => (
            <label key={k}>
              {k === 'start' ? 'From' : 'Until'}
              <input
                required
                className={input}
                type="datetime-local"
                value={r[k]}
                onChange={(e) =>
                  setRows(rows.map((v, j) => (j === n ? { ...v, [k]: e.target.value } : v)))
                }
              />
            </label>
          ))}
          <Button
            type="button"
            variant="ghost"
            onClick={() => setRows(rows.filter((_, j) => j !== n))}
          >
            Remove
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap gap-2">
        {(Object.keys(labels) as Interval['kind'][]).map((kind) => (
          <Button
            type="button"
            key={kind}
            variant="secondary"
            onClick={() =>
              setRows([
                ...rows,
                {
                  kind,
                  start: day.date + (kind === 'WORK' ? 'T09:00' : 'T13:00'),
                  end: day.date + (kind === 'WORK' ? 'T18:00' : 'T14:00'),
                  original: undefined
                }
              ])
            }
          >
            Add {labels[kind].toLowerCase()}
          </Button>
        ))}
        <Button type="button" variant="ghost" onClick={() => setRows([])}>
          Mark day off
        </Button>
      </div>
      {error && <p role="alert">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit">Save availability</Button>
        <Button type="button" variant="secondary" onClick={cancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
