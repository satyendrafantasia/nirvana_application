import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../api/client';
import { useAuth } from '../hooks/useAuth';
import Button from '../components/common/Button';

const endpoint = '/api/admin/operations/booking-alerts';
type Alert = {
  id: number; spa: string; bookingReference?: string; requestId?: number; email?: string; phone?: string;
  event: string; audience: string; state: string; assignedTo?: number; assignee?: string;
  acknowledged: number; attempts: number; outcome?: string;
};
type Readiness = { smsEnabled: boolean; whatsappEnabled: boolean; pushConfigured: boolean; pushCredentialsAvailable: boolean; pushDevices: number };

function AlertCard({ item, user }: { item: Alert; user: string }) {
  const qc = useQueryClient();
  const [note, setNote] = useState('');
  const action = useMutation({
    mutationFn: (op: string) => client.post(`${endpoint}/${item.id}/${op}`, { note }),
    onSettled: () => qc.invalidateQueries({ queryKey: ['booking-alerts'] })
  });
  const mine = String(item.assignedTo) === user;
  return <article className="card p-5 space-y-3">
    <h3 className="font-semibold">{item.spa} · {item.bookingReference || `Appointment request #${item.requestId}`}</h3>
    <p>{item.event.replaceAll('_', ' ')} · {item.audience.replaceAll('_', ' ')} · {item.state.replaceAll('_', ' ')}</p>
    {item.bookingReference && <p>{item.acknowledged ? 'Spa acknowledged the appointment' : 'No spa acknowledgment recorded'}</p>}
    <p>{item.phone || 'No phone on file'} · {item.email || 'No email on file'}</p>
    <p>{item.assignee ? `Assigned to ${item.assignee}` : 'Unassigned'}</p>
    {item.outcome && <p>Contact outcome · {item.outcome}</p>}
    {item.state !== 'RESOLVED' && <>
      {!item.assignedTo && <Button disabled={action.isPending} onClick={() => action.mutate('assign')}>Assign to me</Button>}
      {mine && <div className="space-y-3">
        {item.event !== 'FOLLOW_UP' && <Button variant="secondary" disabled={action.isPending} onClick={() => action.mutate('retry')}>Retry delivery</Button>}
        <label className="block">Contact outcome
          <textarea className="block border rounded-lg p-2 w-full" value={note} maxLength={500} onChange={e => setNote(e.target.value)} />
        </label>
        <Button disabled={!note.trim() || action.isPending} onClick={() => action.mutate('resolve')}>Resolve alert</Button>
      </div>}
    </>}
    {action.isError && <p role="alert">{errorMessage(action.error)}</p>}
  </article>;
}

export default function BookingAlertQueue() {
  const { user } = useAuth();
  const [resolved, setResolved] = useState(false);
  const [after, setAfter] = useState(0);
  const query = useQuery({ queryKey: ['booking-alerts', user?.id, resolved, after],
    queryFn: async () => (await client.get<Alert[]>(endpoint, { params: { resolved, after } })).data });
  const ready = useQuery({ queryKey: ['alert-readiness', user?.id],
    queryFn: async () => (await client.get<Readiness>(endpoint + '/readiness')).data });
  const rows = query.data || [];
  return <section className="space-y-4">
    <h2 className="text-2xl font-semibold">Booking alerts</h2>
    {ready.data && <p>SMS {ready.data.smsEnabled ? 'enabled' : 'disabled'} · WhatsApp {ready.data.whatsappEnabled ? 'enabled' : 'disabled'} ·
      Push {ready.data.pushConfigured ? (ready.data.pushCredentialsAvailable ? 'configured' : 'sender credentials missing') : 'awaiting setup'} · {ready.data.pushDevices} registered devices</p>}
    <div className="flex flex-wrap gap-2">{[false, true].map(done =>
      <Button key={String(done)} variant={resolved === done ? 'primary' : 'secondary'} onClick={() => { setResolved(done); setAfter(0); }}>
        {done ? 'Resolved' : 'Needs attention'}
      </Button>)}
      <Button variant="secondary" onClick={() => void query.refetch()}>Refresh</Button>
    </div>
    {query.isLoading && <p>Loading alerts…</p>}
    {query.isError && <p role="alert">{errorMessage(query.error)}</p>}
    {query.isSuccess && !rows.length && <p>No alerts in this view.</p>}
    {rows.slice(0,50).map(item => <AlertCard key={item.id} item={item} user={String(user?.id)} />)}
    {after > 0 && <Button variant="secondary" onClick={() => setAfter(0)}>First page</Button>}
    {rows.length > 50 && <Button onClick={() => setAfter(rows[49].id)}>Next page</Button>}
  </section>;
}
