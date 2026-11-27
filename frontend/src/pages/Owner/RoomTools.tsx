import { showToast } from '../../components/common/Toast';
import { FormEvent, useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
export type Room = { id: number; name: string; active: boolean };
const field = 'block w-full rounded-lg border p-2';
type Props = {
  base: string;
  date: string;
  rooms: Room[];
  services: { id: number; name: string; active: boolean }[];
  refresh: () => Promise<unknown>;
};
export function RoomTools({ base, date, rooms, services, refresh }: Props) {
  const [report, setReport] = useState('');
  const action = useMutation({
    mutationFn: async ({ method, path, body }: { method: string; path: string; body: unknown }) => {
      const { data } = await client.request({ url: base + path, method, data: body });
      return path === '/generate'
        ? data.created +
            ' slots created. ' +
            data.skipped.length +
            ' skipped. ' +
            data.skipped
              .map(
                (s: { roomId: number; start: string; end: string }) =>
                  (rooms.find((r) => r.id === s.roomId)?.name || s.roomId) +
                  ' ' +
                  s.start.slice(11, 16) +
                  '–' +
                  s.end.slice(11, 16)
              )
              .join(', ')
        : 'Room saved.';
    },
    onSuccess: async (r) => {
      setReport(r);
      showToast('Schedule updated.', 'success');
      await refresh();
    }
  });
  function room(e: FormEvent<HTMLFormElement>, id?: number) {
    e.preventDefault();
    const v = new FormData(e.currentTarget);
    action.mutate({
      path: '/rooms' + (id ? '/' + id : ''),
      method: id ? 'put' : 'post',
      body: { name: v.get('name'), active: v.has('active') }
    });
  }
  return (
    <fieldset className="card space-y-4 p-5" disabled={action.isPending}>
      <details open={!rooms.length || undefined}>
        <summary className="font-semibold cursor-pointer">Manage rooms</summary>
        {[...rooms, { id: 0, name: '', active: true }].map((r) => (
          <form
            key={r.id}
            onSubmit={(e) => room(e, r.id)}
            className="flex flex-wrap items-end gap-3 py-2"
          >
            <label>
              {r.id ? 'Room name' : 'New room'}
              <input name="name" required maxLength={128} className={field} defaultValue={r.name} />
            </label>
            <label>
              <input type="checkbox" name="active" defaultChecked={r.active} /> Active
            </label>
            <Button>{r.id ? 'Save room' : 'Add room'}</Button>
          </form>
        ))}
      </details>
      <details>
        <summary className="font-semibold cursor-pointer">Generate slots</summary>
        <form
          className="space-y-3 pt-3"
          onSubmit={(e) => {
            e.preventDefault();
            const v = new FormData(e.currentTarget);
            const ids = v.getAll('room').map(Number);
            if (!ids.length) {
              setReport('Select at least one room.');
              return;
            }
            action.mutate({
              method: 'post',
              path: '/generate',
              body: {
                date,
                roomIds: ids,
                serviceId: Number(v.get('service')),
                start: v.get('start'),
                end: v.get('end'),
                minutes: Number(v.get('minutes'))
              }
            });
          }}
        >
          <div className="grid gap-3 sm:grid-cols-4">
            <label>
              Service
              <select className={field} required name="service">
                {services
                  .filter((s) => s.active)
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
              </select>
            </label>
            <label>
              From
              <input required className={field} name="start" type="time" defaultValue="10:00" />
            </label>
            <label>
              Until
              <input required className={field} name="end" type="time" defaultValue="21:00" />
            </label>
            <label>
              Minutes
              <input
                required
                className={field}
                name="minutes"
                type="number"
                min={1}
                max={1440}
                defaultValue={60}
              />
            </label>
          </div>
          <fieldset className="flex flex-wrap gap-3">
            <legend>Rooms</legend>
            {rooms
              .filter((r) => r.active)
              .map((r) => (
                <label key={r.id}>
                  <input name="room" type="checkbox" value={r.id} /> {r.name}
                </label>
              ))}
          </fieldset>
          <Button>Generate slots for {date}</Button>
        </form>
      </details>
      {action.isError ? (
        <p role="alert">{errorMessage(action.error)}</p>
      ) : (
        <p role="status">{report}</p>
      )}
    </fieldset>
  );
}
type Shift = { weekday: number; start: string; end: string };
const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export function WeeklyRoster({
  id,
  base,
  refresh
}: {
  id: number;
  base: string;
  refresh: () => Promise<unknown>;
}) {
  const [open, setOpen] = useState(false);
  const path = base + '/therapists/' + id + '/week';
  const week = useQuery({
    queryKey: ['weekly-roster', path],
    enabled: open,
    queryFn: async () => (await client.get<Shift[]>(path)).data || []
  });
  const [rows, setRows] = useState<Shift[] | null>(null);
  const shifts = rows ?? week.data ?? [];
  const save = useMutation({
    mutationFn: () => client.put(path, shifts),
    onSuccess: async () => {
      showToast('Weekly roster saved.', 'success');
      await week.refetch();
      await refresh();
      setRows(null);
      setOpen(false);
    }
  });
  return (
    <div>
      <Button variant="ghost" onClick={() => setOpen(!open)}>
        Weekly roster
      </Button>
      {open && (
        <form
          className="space-y-2"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <p>
            Weekly shifts repeat until a date override is saved. A saved empty week marks every day
            off.
          </p>
          {shifts.map((r, i) => (
            <div key={i} className="flex flex-wrap gap-2">
              <select
                aria-label="Working day"
                value={r.weekday}
                onChange={(e) =>
                  setRows(
                    shifts.map((v, j) => (i === j ? { ...v, weekday: Number(e.target.value) } : v))
                  )
                }
              >
                {days.map((d, k) => (
                  <option key={d} value={k + 1}>
                    {d}
                  </option>
                ))}
              </select>
              {(['start', 'end'] as const).map((k) => (
                <label key={k}>
                  {k}
                  <input
                    required
                    type="time"
                    value={r[k].slice(0, 5)}
                    onChange={(e) =>
                      setRows(shifts.map((v, j) => (i === j ? { ...v, [k]: e.target.value } : v)))
                    }
                  />
                </label>
              ))}
              <button type="button" onClick={() => setRows(shifts.filter((_, j) => j !== i))}>
                Remove shift
              </button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            onClick={() => setRows([...shifts, { weekday: 1, start: '10:00', end: '18:00' }])}
          >
            Add shift
          </Button>{' '}
          <Button disabled={!week.isSuccess || save.isPending}>Save weekly roster</Button>
          {(week.isError || save.isError) && (
            <p role="alert">{errorMessage(save.error || week.error)}</p>
          )}
        </form>
      )}
    </div>
  );
}
