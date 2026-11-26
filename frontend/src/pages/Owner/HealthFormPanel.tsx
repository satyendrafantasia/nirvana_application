import { useQuery } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { HealthAnswers, SpaHealthForm, getSpaHealthForm } from '../../api/healthFormApi';

const yesNo = (v: boolean | null) => (v === null ? 'Not answered' : v ? 'Yes' : 'No');

export function healthRows(a: HealthAnswers): [string, string][] {
  const rows: [string, string][] = [
    ['Pregnant', yesNo(a.pregnant)],
    ['Allergies', [...a.allergies.map((x) => x.toLowerCase()), a.allergyDetails].filter(Boolean).join(', ') || 'None'],
    ['Recent injury or surgery', yesNo(a.recentInjuryOrSurgery) + (a.injuryDetails ? ' · ' + a.injuryDetails : '')],
    ['Skin condition', yesNo(a.skinCondition) + (a.skinDetails ? ' · ' + a.skinDetails : '')],
    ['High blood pressure or heart condition', yesNo(a.heartCondition)],
    ['Medications', a.medications || 'None'],
    ['Pressure', a.pressure.toLowerCase()],
    ['Areas to avoid', a.avoidAreas || 'None']
  ];
  for (const c of a.custom || []) rows.push([c.prompt || 'Question', c.answer || 'Not answered']);
  if (a.notes) rows.push(['Notes', a.notes]);
  return rows;
}

export function HealthFormView({ form }: { form: SpaHealthForm }) {
  if (!form.enabled) return <p className="text-sm">Health forms are switched off on this server.</p>;
  if (form.status === 'NONE') return <p className="text-sm">The guest has not shared a health form. Ask about health conditions before the treatment.</p>;
  if (form.status === 'UNREADABLE') return <p role="alert" className="text-sm">This health form can no longer be read. Ask the guest before the treatment.</p>;
  return (
    <div className="space-y-2 text-sm">
      {form.attention.length > 0 && (
        <p role="alert" className="rounded-xl bg-rose-50 p-2 font-semibold text-rose-900">
          Needs attention: {form.attention.join(' · ')}
        </p>
      )}
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        {healthRows(form.answers!).map(([k, v]) => (
          <div className="contents" key={k}>
            <dt className="text-slate-600">{k}</dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>
      {form.consentedAt && <p className="text-slate-600">Guest agreed to the terms · {new Date(form.consentedAt).toLocaleString()}</p>}
    </div>
  );
}

/** The guest's health form on an appointment, with a flag when something needs the therapist's attention. */
export default function HealthFormPanel({ spa, booking }: { spa: string; booking: number | string }) {
  const form = useQuery({
    queryKey: ['desk', spa, 'health-form', String(booking)],
    queryFn: () => getSpaHealthForm(spa, booking),
    staleTime: 60_000
  });
  const flags = form.data?.attention || [];
  return (
    <details>
      <summary className="text-sm text-primary-700">
        Health form
        {form.data?.status === 'SUBMITTED' &&
          (flags.length ? (
            <span className="ml-2 rounded-full bg-rose-100 px-2 py-0.5 font-semibold text-rose-900">⚠ {flags.join(' · ')}</span>
          ) : (
            <span className="ml-2 text-slate-600">· nothing flagged</span>
          ))}
        {form.data?.status === 'NONE' && <span className="ml-2 text-slate-600">· not shared</span>}
      </summary>
      {form.isError ? <p role="alert">{errorMessage(form.error)}</p> : form.data ? <HealthFormView form={form.data} /> : <p>Loading…</p>}
    </details>
  );
}
