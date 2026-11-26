import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';

type Member = { id: number; name: string; email?: string; phone?: string; addedUtc: string };
const input = 'rounded-xl border bg-white px-3 py-2 text-sm';
const button =
  'rounded-xl border border-primary-200 px-3 py-2 text-sm font-semibold text-primary-800 hover:bg-primary-50 disabled:opacity-50';

/**
 * The owner's front-desk team for one spa. Members sign in with their own Nirvana account and can run
 * the day's appointments and take payments, but can't see the money reports, return or void money, or
 * change the spa.
 */
export default function FrontDeskTeam({ spaId }: { spaId: string }) {
  const qc = useQueryClient();
  const [contact, setContact] = useState('');
  const key = ['desk-team', spaId];
  const team = useQuery({
    queryKey: key,
    queryFn: async () => (await client.get<Member[]>('/api/owner/spas/' + spaId + '/staff')).data
  });
  const add = useMutation({
    mutationFn: async () =>
      client.post('/api/owner/spas/' + spaId + '/staff', { contact: contact.trim() }),
    onSuccess: async () => {
      setContact('');
      await qc.invalidateQueries({ queryKey: key });
    }
  });
  const remove = useMutation({
    mutationFn: async (user: number) =>
      client.delete('/api/owner/spas/' + spaId + '/staff/' + user),
    onSuccess: () => qc.invalidateQueries({ queryKey: key })
  });
  return (
    <section className="card p-5 space-y-3">
      <h3 className="text-lg font-semibold">Front desk team</h3>
      <p className="text-sm text-slate-600">
        Give your receptionists their own sign-in. They can check guests in, move appointments and
        record payments, but can&apos;t see your takings, return or void money, or change the spa.
        They need a Nirvana account first, then sign in at the owner sign-in page.
      </p>
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          add.mutate();
        }}
      >
        <label className="grid text-sm">
          Their email or phone number
          <input
            className={input}
            value={contact}
            maxLength={255}
            required
            onChange={(e) => setContact(e.target.value)}
          />
        </label>
        <button className={button} disabled={add.isPending || !contact.trim()}>
          Add to front desk
        </button>
      </form>
      {add.isError && <p role="alert">{errorMessage(add.error)}</p>}
      {team.isError && <p role="alert">{errorMessage(team.error)}</p>}
      {team.data?.length === 0 && <p className="text-sm">No front-desk staff yet.</p>}
      {team.data?.map((m) => (
        <div key={m.id} className="flex flex-wrap items-center justify-between gap-2 border-t pt-2">
          <span>
            <strong>{m.name}</strong>
            {m.name !== m.email && m.email && ' · ' + m.email}
            {m.name !== m.phone && m.phone && ' · ' + m.phone}
          </span>
          <button
            className={button}
            disabled={remove.isPending}
            onClick={() => remove.mutate(m.id)}
          >
            Remove access
          </button>
        </div>
      ))}
      {remove.isError && <p role="alert">{errorMessage(remove.error)}</p>}
    </section>
  );
}
