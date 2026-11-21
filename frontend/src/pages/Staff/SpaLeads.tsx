import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import { useAuth } from '../../hooks/useAuth';
import { isAdmin } from '../../auth/roles';

export const STATUSES = ['NEW', 'CONTACTED', 'INTERESTED', 'ONBOARDED', 'NOT_INTERESTED'] as const;
export type LeadStatus = (typeof STATUSES)[number];
export const statusLabel = (s: string) =>
  ({ NEW: 'New', CONTACTED: 'Contacted', INTERESTED: 'Interested', ONBOARDED: 'Onboarded', NOT_INTERESTED: 'Not interested' })[s] || s;

export type Lead = {
  id: number;
  name: string;
  address?: string;
  city?: string;
  phone?: string;
  email?: string;
  website?: string;
  socialsJson?: string;
  googleRating?: number;
  googleReviewCount?: number;
  mapsUrl?: string;
  status: LeadStatus;
  notes?: string;
  lastContactedAt?: string;
};
type Page = { content: Lead[]; total: number; page: number; counts: Record<string, number> };
type ImportResult = { created: number; updated: number; skipped: number; problems: string[] };

const safeUrl = (url?: string) => (url && /^https?:\/\//i.test(url) ? url : undefined);
const socials = (raw?: string): [string, string][] => {
  try {
    return Object.entries(JSON.parse(raw || '{}') as Record<string, string>).filter(([, u]) => safeUrl(u));
  } catch {
    return [];
  }
};

function ImportLeads() {
  const qc = useQueryClient();
  const [file, setFile] = useState<File>();
  const [city, setCity] = useState('');
  const [source, setSource] = useState('');
  const run = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Choose a JSON file.');
      let rows: unknown;
      try {
        rows = JSON.parse(await file.text());
      } catch {
        throw new Error('That file is not valid JSON.');
      }
      if (!Array.isArray(rows)) throw new Error('The file must contain a list of spas.');
      return (
        await client.post<ImportResult>('/api/admin/spa-leads/import', rows, {
          params: { city: city.trim() || undefined, source: source.trim() || file.name }
        })
      ).data;
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['spa-leads'] })
  });
  return (
    <details className="card p-4">
      <summary className="cursor-pointer font-semibold">Import spas from a file</summary>
      <div className="mt-4 space-y-3">
        <p className="text-sm text-slate-600">
          A JSON list exported from Google Maps (title, address, phone, email, website, socials,
          totalScore, reviewsCount, url, coordinates). Importing the same file again refreshes contact
          details but keeps each spa’s status and notes.
        </p>
        <input type="file" accept="application/json,.json" onChange={(e) => setFile(e.target.files?.[0])} />
        <div className="grid gap-3 sm:grid-cols-2">
          <Input label="City (optional)" placeholder="e.g. Gurugram" value={city} maxLength={100} onChange={(e) => setCity(e.target.value)} helperText="Use when addresses name a village after the city." />
          <Input label="Source label (optional)" placeholder="e.g. Google Maps export Nov 2026" value={source} maxLength={100} onChange={(e) => setSource(e.target.value)} />
        </div>
        <Button disabled={!file || run.isPending} onClick={() => run.mutate()}>
          {run.isPending ? 'Importing…' : 'Import'}
        </Button>
        {run.isError && <p role="alert" className="text-rose-700">{errorMessage(run.error)}</p>}
        {run.data && (
          <div role="status" className="text-sm">
            <p>
              {run.data.created} added, {run.data.updated} updated, {run.data.skipped} skipped.
            </p>
            {run.data.problems.length > 0 && (
              <ul className="list-disc pl-5 text-slate-600">
                {run.data.problems.map((p) => <li key={p}>{p}</li>)}
              </ul>
            )}
          </div>
        )}
      </div>
    </details>
  );
}

export function LeadCard({ lead }: { lead: Lead }) {
  const qc = useQueryClient();
  const [status, setStatus] = useState<LeadStatus>(lead.status);
  const [notes, setNotes] = useState(lead.notes || '');
  const dirty = status !== lead.status || notes !== (lead.notes || '');
  const save = useMutation({
    mutationFn: async () => client.patch(`/api/admin/spa-leads/${lead.id}`, { status, notes }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['spa-leads'] })
  });
  const website = safeUrl(lead.website);
  const maps = safeUrl(lead.mapsUrl);
  return (
    <article className="card space-y-3 p-4" aria-labelledby={`lead-${lead.id}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 id={`lead-${lead.id}`} className="font-semibold">{lead.name}</h2>
          <p className="text-sm text-slate-600">{lead.address}</p>
        </div>
        {lead.googleRating != null && (
          <span className="shrink-0 text-sm">
            ★ {Number(lead.googleRating).toFixed(1)}
            {lead.googleReviewCount != null && ` · ${lead.googleReviewCount} reviews`}
          </span>
        )}
      </div>
      <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
        {lead.phone && <a className="underline" href={`tel:${lead.phone.replace(/[^+\d]/g, '')}`}>{lead.phone}</a>}
        {lead.email && <a className="underline break-all" href={`mailto:${lead.email}`}>{lead.email}</a>}
        {website && <a className="underline" href={website} target="_blank" rel="noreferrer">Website</a>}
        {maps && <a className="underline" href={maps} target="_blank" rel="noreferrer">Google Maps</a>}
        {socials(lead.socialsJson).map(([name, url]) => (
          <a key={name} className="underline capitalize" href={url} target="_blank" rel="noreferrer">{name}</a>
        ))}
      </p>
      <div className="grid gap-3 sm:grid-cols-[12rem_1fr_auto] sm:items-end">
        <label className="flex flex-col gap-1 text-sm font-medium">
          Status
          <select className="min-h-11 rounded-xl border bg-white px-3" value={status} onChange={(e) => setStatus(e.target.value as LeadStatus)}>
            {STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm font-medium">
          Notes
          <input className="min-h-11 rounded-xl border px-3 font-normal" maxLength={2000} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Who you spoke to, next step…" />
        </label>
        <Button disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
      </div>
      {lead.lastContactedAt && (
        <p className="text-xs text-slate-500">Last contacted {new Date(lead.lastContactedAt).toLocaleDateString()}</p>
      )}
      {save.isError && <p role="alert" className="text-rose-700">{errorMessage(save.error)}</p>}
    </article>
  );
}

export default function SpaLeads() {
  const { user } = useAuth();
  const allowed = isAdmin(user?.roles);
  const [status, setStatus] = useState<string>('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const leads = useQuery({
    queryKey: ['spa-leads', status, q, page],
    queryFn: async () =>
      (await client.get<Page>('/api/admin/spa-leads', { params: { status: status || undefined, q: q.trim() || undefined, page, size: 25 } })).data,
    enabled: allowed
  });
  if (!allowed)
    return (
      <div className="mx-auto max-w-xl space-y-3 p-8">
        <h1 className="text-2xl font-bold">Admin access required</h1>
        <Link to="/">Go home</Link>
      </div>
    );
  const pages = Math.max(1, Math.ceil((leads.data?.total || 0) / 25));
  return (
    <div className="mx-auto max-w-4xl space-y-5 p-4 md:p-8">
      <div>
        <h1 className="text-3xl font-bold">Spa leads</h1>
        <p className="text-slate-600">
          Spas to approach about joining Nirvana. When one is interested, send them to{' '}
          <span className="font-medium">For spa owners</span> to sign up and claim their listing.
        </p>
      </div>
      <ImportLeads />
      <nav aria-label="Lead status" className="flex flex-wrap gap-2">
        {['', ...STATUSES].map((s) => (
          <button
            key={s || 'all'}
            type="button"
            aria-pressed={status === s}
            onClick={() => { setStatus(s); setPage(0); }}
            className={`rounded-xl border px-3 py-1.5 text-sm ${status === s ? 'bg-primary-700 text-white' : 'bg-white'}`}
          >
            {s ? statusLabel(s) : 'All'}
            {s && leads.data?.counts?.[s] != null && ` (${leads.data.counts[s]})`}
          </button>
        ))}
      </nav>
      <Input label="Search name, address or phone" value={q} maxLength={100} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      {leads.isPending && <p role="status">Loading leads…</p>}
      {leads.isError && <p role="alert" className="text-rose-700">{errorMessage(leads.error)}</p>}
      {leads.data?.content.length === 0 && <p className="card p-6 text-slate-600">No spas here yet.</p>}
      <div className="space-y-3">
        {leads.data?.content.map((lead) => <LeadCard key={`${lead.id}-${lead.status}-${lead.notes || ''}`} lead={lead} />)}
      </div>
      {pages > 1 && (
        <div className="flex items-center justify-between">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
          <span className="text-sm">Page {page + 1} of {pages}</span>
          <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</Button>
        </div>
      )}
    </div>
  );
}
