import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import client, { errorMessage } from '../../api/client';
import { useAuth } from '../../hooks/useAuth';
import { isAdmin } from '../../auth/roles';

type State = 'OPEN' | 'RESOLVED' | 'CLOSED';
type CaseRow = { id: number; subject: string; status: State; assignee?: string; bookingReference?: string };
type Message = { id: number; senderType: string; content: string };
type Thread = { id: number; state: State; assigneeId?: number; messages: Message[] };
type Receipt = { caseId: number; accessKey: string };
type Action = 'create' | 'reply' | 'assign' | State;
const input = 'w-full rounded-xl border p-3';
const states: State[] = ['OPEN', 'RESOLVED', 'CLOSED'];

export default function Support({ staff = false }: { staff?: boolean }) {
  const { user } = useAuth();
  const authorized = !staff || isAdmin(user?.roles);
  const guest = !staff && !user;
  const qc = useQueryClient();
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [reply, setReply] = useState('');
  const [keyInput, setKeyInput] = useState('');
  const [key, setKey] = useState('');
  const [receipt, setReceipt] = useState<Receipt>();
  const [selected, setSelected] = useState<number>();
  const [state, setState] = useState<State>('OPEN');
  const [page, setPage] = useState(0);
  const scope = ['support', user?.id ?? 'guest', staff];
  const guestHeaders = { headers: { 'X-Support-Key': key } };
  const queue = useQuery({
    queryKey: [...scope, 'queue', state, page], enabled: authorized && !guest,
    refetchInterval: 30000,
    queryFn: async (): Promise<CaseRow[]> => {
      if (staff) return (await client.get('/api/admin/support/cases', { params: { state, page } })).data;
      const { data } = await client.get('/api/messages/threads', { params: { size: 40, page } });
      return data.threads.map((t: { threadId: number; subject: string; status: State }) =>
        ({ id: t.threadId, subject: t.subject, status: t.status }));
    }
  });
  const thread = useQuery({
    queryKey: [...scope, 'case', guest ? key : selected], enabled: authorized && (guest ? !!key : !!selected),
    retry: false, refetchInterval: 30000,
    queryFn: async (): Promise<Thread> => {
      if (guest) return (await client.get('/api/public/support/case', guestHeaders)).data;
      if (staff) return (await client.get('/api/admin/support/cases/' + selected)).data;
      const { data } = await client.get<Thread>('/api/me/support/' + selected);
      const through = data.messages[data.messages.length - 1]?.id;
      if (through) {
        await client.post('/api/me/support/' + selected + '/read', null, { params: { through } });
        void qc.invalidateQueries({ queryKey: ['support-unread', user?.id] });
      }
      return data;
    }
  });
  const change = useMutation({
    mutationFn: async (action: Action) => {
      if (action === 'create') {
        if (guest) {
          const { data } = await client.post<Receipt>('/api/public/support/cases', { subject, message: body });
          setReceipt(data); setKey(data.accessKey);
        } else {
          const { data } = await client.post('/api/messages/threads', { subject, initialMessage: body });
          setSelected(data.threadId);
        }
        setPage(0); setSubject(''); setBody('');
      } else if (action === 'reply') {
        if (guest) await client.post('/api/public/support/case/replies', { message: reply }, guestHeaders);
        else if (staff) await client.post('/api/admin/support/cases/' + selected + '/replies', { message: reply });
        else await client.post('/api/messages/threads/' + selected + '/messages', { content: reply });
        setReply('');
      } else if (action === 'assign') await client.post('/api/admin/support/cases/' + selected + '/assign', null, {
        params: { takeOverFrom: thread.data?.assigneeId ?? undefined }
      });
      else await client.put('/api/admin/support/cases/' + selected + '/state', null, { params: { state: action } });
    },
    onSettled: () => qc.invalidateQueries({ queryKey: scope })
  });
  const submit = (e: FormEvent, action: Action) => { e.preventDefault(); if (!change.isPending) change.mutate(action); };
  const failure = change.error || thread.error || queue.error;
  const otherAgent = staff && thread.data?.assigneeId != null && String(thread.data.assigneeId) !== String(user?.id);
  if (!authorized) return <p role="alert">A platform administrator account is required to open the support inbox.</p>;
  return (
    <div className="mx-auto max-w-5xl space-y-6 p-5">
      <h1 className="text-3xl font-semibold">{staff ? 'Support inbox' : 'Booking support'}</h1>
      {guest && <p><Link className="underline" to="/auth/login?redirect=%2Fsupport">Sign in</Link> to see your account conversations, or open a guest case below.</p>}
      {failure && <p role="alert" className="card p-3">{errorMessage(failure)}</p>}
      <div className="grid items-start gap-6 md:grid-cols-2">
        <section className="space-y-4">
          {!staff && <form className="card space-y-3 p-5" onSubmit={e => submit(e, 'create')}>
            <h2>Open a case</h2><label className="block">Subject<input className={input} required maxLength={200} value={subject} onChange={e => setSubject(e.target.value)} /></label>
            <label className="block">How can we help?<textarea className={input} required maxLength={4000} value={body} onChange={e => setBody(e.target.value)} /></label>
            <button className="btn-primary" disabled={change.isPending}>Send to support</button>
          </form>}
          {guest ? <form className="card space-y-3 p-5" onSubmit={e => { e.preventDefault(); setKey(keyInput.trim()); setReply(''); }}>
            <label className="block">Track a guest case with your private access key<input className={input} required autoComplete="off" value={keyInput} onChange={e => setKeyInput(e.target.value)} /></label>
            <button className="btn-secondary">Open conversation</button>
          </form> : <div className="card space-y-3 p-5">
            <h2>{staff ? 'Cases' : 'Your cases'}</h2>
            {staff && <label>Status<select className={input} value={state} onChange={e => { setState(e.target.value as State); setPage(0); }}>{states.map(v => <option key={v}>{v}</option>)}</select></label>}
            {queue.isLoading && <p>Loading cases…</p>}
            {queue.data?.length === 0 && <p>No cases on this page.</p>}
            {queue.data?.map(t => <button className="block w-full border-b p-2 text-left" key={t.id} onClick={() => { setSelected(t.id); setReply(''); change.reset(); }}>
              <strong>{t.subject}</strong><br />#{t.id} · {t.status}{t.assignee && ' · ' + t.assignee}{t.bookingReference && ' · ' + t.bookingReference}
            </button>)}
            <div className="flex gap-4"><button disabled={!page} onClick={() => setPage(page - 1)}>Previous</button><button disabled={queue.data?.length !== 40} onClick={() => setPage(page + 1)}>Next</button></div>
          </div>}
        </section>
        <section className="card space-y-4 p-5" aria-live="polite">
          {guest && receipt && <div><strong>Save your private key for case #{receipt.caseId}</strong><p className="break-all select-all font-mono">{receipt.accessKey}</p><p>Anyone with this key can read and reply to this case.</p></div>}
          {thread.data ? <>
            <h2>Case #{thread.data.id} · {thread.data.state}</h2>
            <ol className="space-y-3">{thread.data.messages.map(m => <li className="rounded-xl bg-slate-50 p-3" key={m.id}><strong>{m.senderType === 'USER' ? 'Customer' : 'Support'}</strong><p className="whitespace-pre-wrap break-words">{m.content}</p></li>)}</ol>
            {thread.data.state === 'OPEN' && !otherAgent && <form className="space-y-2" onSubmit={e => submit(e, 'reply')}><label>Reply<textarea className={input} required maxLength={4000} value={reply} onChange={e => setReply(e.target.value)} /></label><button disabled={change.isPending} className="btn-primary">Send reply</button></form>}
            {staff && <div className="flex flex-wrap gap-3">{(otherAgent || !thread.data.assigneeId) && <button className="btn-secondary" disabled={change.isPending} onClick={() => change.mutate('assign')}>{otherAgent ? 'Take over this case' : 'Assign to me'}</button>}
              {!otherAgent && states.filter(v => v !== thread.data.state).map(v => <button className="btn-secondary" key={v} disabled={change.isPending} onClick={() => change.mutate(v)}>{v === 'OPEN' ? 'Reopen' : v === 'RESOLVED' ? 'Resolve' : 'Close'}</button>)}</div>}
          </> : <p>{thread.isFetching ? 'Loading conversation…' : 'Open a case to see its conversation.'}</p>}
        </section>
      </div>
    </div>
  );
}
