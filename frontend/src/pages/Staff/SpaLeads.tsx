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
  googlePlaceId?: string;
  googleRating?: number;
  googleReviewCount?: number;
  mapsUrl?: string;
  status: LeadStatus;
  notes?: string;
  lastContactedAt?: string;
  spaId?: number;
  published?: number;
  needsOwner?: number;
};
type Page = { content: Lead[]; total: number; page: number; counts: Record<string, number> };
type ImportResult = { created: number; updated: number; skipped: number; problems: string[] };
type OnboardResult = { onboarded: number; linked: number; skipped: number; slots: number; problems: string[] };

/** Leads that onboarding picks up: everything not onboarded or declined. */
export const readyToOnboard = (counts?: Record<string, number>) =>
  (counts?.NEW || 0) + (counts?.CONTACTED || 0) + (counts?.INTERESTED || 0);

export function OnboardLeads({ ready }: { ready: number }) {
  const qc = useQueryClient();
  const [confirmed, setConfirmed] = useState(false);
  const run = useMutation({
    mutationFn: async () =>
      (await client.post<OnboardResult>('/api/admin/spa-leads/onboard', { leadIds: [], permissionConfirmed: true, days: 30 }, { timeout: 600000 })).data,
    onSuccess: () => {
      setConfirmed(false);
      void qc.invalidateQueries({ queryKey: ['spa-leads'] });
    }
  });
  return (
    <section className="card space-y-3 p-4" aria-labelledby="onboard-leads">
      <h2 id="onboard-leads" className="font-semibold">Publish imported spas</h2>
      <p className="text-sm text-slate-600">
        Each spa opens daily from 11 am to 9 pm with five therapists and five rooms. Sessions are ₹1,500 for 45 minutes or ₹2,000 for 60 minutes. Owners can edit these settings.
      </p>
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" className="mt-1" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />
        <span>Each of these spas has agreed to be listed and booked on Nirvana. Your name is recorded against each one.</span>
      </label>
      <Button disabled={!confirmed || !ready || run.isPending} onClick={() => run.mutate()}>
        {run.isPending ? 'Publishing…' : `Publish ${ready} spas`}
      </Button>
      {run.isError && <p role="alert" className="text-rose-700">{errorMessage(run.error)}</p>}
      {run.data && (
        <div role="status" className="text-sm">
          <p>
            {run.data.onboarded} live with {run.data.slots.toLocaleString()} appointment times
            {run.data.linked > 0 && `, ${run.data.linked} already on Nirvana and linked`}
            {run.data.skipped > 0 && `, ${run.data.skipped} skipped`}.
          </p>
          {run.data.problems.length > 0 && (
            <ul className="list-disc pl-5 text-slate-600">
              {run.data.problems.map((p) => <li key={p}>{p}</li>)}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}

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
      const searchResult =
        rows && typeof rows === 'object' && Array.isArray((rows as { local_results?: unknown }).local_results);
      if (!Array.isArray(rows) && !searchResult)
        throw new Error('The file must contain a list of spas or a Google Maps search result.');
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
          A JSON export from Google Maps: either a list of spas, or a Maps search result with
          “local_results”. Spas already in the list are matched by their Maps ID or Place ID and
          updated, keeping the status and notes you have recorded.
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
  const [ownerEmail, setOwnerEmail] = useState('');
  const assign = useMutation({
    mutationFn: () => client.post(`/api/admin/spa-leads/${lead.id}/owner`, {email: ownerEmail.trim()}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['spa-leads'] })
  });
  const [review, setReview] = useState(false);
  const inventory = useQuery({
    queryKey: ['lead-inventory', lead.id], enabled: review,
    queryFn: async () => (await client.get<{services: {name:string;price:number;minutes:number}[]; hours: {weekday:number; opens:string; closes:string}[]; capacity:number}>(`/api/admin/spa-leads/${lead.id}/inventory`)).data
  });
  const publish = useMutation({
    mutationFn: () => client.post(`/api/admin/spas/${lead.spaId}/kyc/approve?inventoryConfirmed=true`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['spa-leads'] })
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
        {lead.spaId != null && !!lead.published && (
          <Link className="font-medium underline" to={`/spas/${lead.spaId}`}>Live on Nirvana</Link>
        )}
        {lead.spaId == null && lead.googlePlaceId && /^[A-Za-z0-9_-]+$/.test(lead.googlePlaceId) && (
          <Link className="underline" to={`/discover/places/${lead.googlePlaceId}`}>Spa page</Link>
        )}
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
      {lead.spaId != null && !lead.published && <div>
        <Button variant="secondary" onClick={() => setReview(!review)}>Review inventory</Button>
        {review && inventory.isError && <p role="alert">{errorMessage(inventory.error)}</p>}
        {review && inventory.data && <div className="space-y-2">
          <ul>{inventory.data.services.map(s => <li key={s.name}>{s.name} · {s.minutes} min · ₹{s.price / 100}</li>)}</ul>
          <ul>{inventory.data.hours.map(h => <li key={h.weekday}>{['Mon','Tue','Wed','Thu','Fri','Sat','Sun'][h.weekday-1]} {h.opens}–{h.closes}</li>)}</ul>
          <p>Simultaneous appointments: {inventory.data.capacity}</p>
        <Button disabled={publish.isPending || !inventory.data.services.length || !inventory.data.hours.length || inventory.data.capacity < 1} onClick={() => {
          if (window.confirm(`Has ${lead.name} confirmed its proposed services, prices, opening hours and simultaneous appointment capacity? Publish only after verifying each item.`)) publish.mutate();
        }}>Confirm inventory and publish</Button></div>}
        {publish.isError && <p role="alert">{errorMessage(publish.error)}</p>}
      </div>}
      {!!lead.needsOwner && <form className="flex flex-wrap items-end gap-3" onSubmit={event => {
        event.preventDefault();
        if (window.confirm(`Grant ${ownerEmail.trim()} control of ${lead.name}? Confirm you have verified this owner's authority.`)) assign.mutate();
      }}>
        <Input type="email" required label="Owner account email" value={ownerEmail} onChange={e => setOwnerEmail(e.target.value)} />
        <Button type="submit" disabled={assign.isPending}>Assign verified owner</Button>
        {assign.isError && <p role="alert">{errorMessage(assign.error)}</p>}
      </form>}
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
          <span className="font-medium">For spa owners</span> to sign up and claim their listing,
          or, once they have agreed, put them live yourself below.
        </p>
      </div>
      <ImportLeads />
      {leads.data && readyToOnboard(leads.data.counts) > 0 && <OnboardLeads ready={readyToOnboard(leads.data.counts)} />}
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
