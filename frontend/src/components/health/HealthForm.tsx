import { ReactNode, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import {
  Allergy,
  GuestHealthForm,
  HealthAnswers,
  Pressure,
  blankAnswers,
  getMyHealthForm,
  submitHealthForm,
  withdrawHealthForm
} from '../../api/healthFormApi';
import Button from '../common/Button';

const allergyLabels: Record<Allergy, string> = { OILS: 'Massage oils', NUTS: 'Nuts', LATEX: 'Latex' };
const pressureLabels: Record<Pressure, string> = { LIGHT: 'Light', MEDIUM: 'Medium', FIRM: 'Firm' };
const field = 'border rounded-xl p-2 w-full';

/** The answers to edit: saved or carried over, laid out against this service's questions. */
export function startingAnswers(form: GuestHealthForm): HealthAnswers {
  const a = { ...blankAnswers(), ...(form.answers || {}) };
  const saved = new Map((form.answers?.custom || []).map((c) => [c.questionId, c.answer]));
  a.custom = form.questions.map((q) => ({ questionId: q.id, prompt: q.prompt, answer: saved.get(q.id) || '' }));
  return a;
}

export function readyToSubmit(a: HealthAnswers, consent: boolean, prefilled: boolean, stillTrue: boolean) {
  return (
    consent &&
    (!prefilled || stillTrue) &&
    [a.pregnant, a.recentInjuryOrSurgery, a.skinCondition, a.heartCondition].every((v) => v !== null)
  );
}

function YesNo({
  label,
  value,
  onChange,
  children
}: {
  label: string;
  value: boolean | null;
  onChange: (v: boolean) => void;
  children?: ReactNode;
}) {
  return (
    <fieldset className="space-y-1">
      <legend className="font-medium">{label}</legend>
      <div className="flex gap-4">
        {[true, false].map((v) => (
          <label key={String(v)}>
            <input type="radio" checked={value === v} onChange={() => onChange(v)} /> {v ? 'Yes' : 'No'}
          </label>
        ))}
      </div>
      {value && children}
    </fieldset>
  );
}

/**
 * The guest's health and consent form for one booking. It can be skipped and completed any time
 * before the visit; answers from the guest's last visit are offered and must be confirmed.
 */
export default function HealthForm({ bookingId, onDone }: { bookingId: string | number; onDone?: () => void }) {
  const qc = useQueryClient();
  const form = useQuery({
    queryKey: ['health-form', String(bookingId)],
    queryFn: () => getMyHealthForm(bookingId),
    staleTime: 0,
    gcTime: 0
  });
  const [a, setA] = useState<HealthAnswers>(blankAnswers());
  const [consent, setConsent] = useState(false);
  const [stillTrue, setStillTrue] = useState(false);
  useEffect(() => {
    if (form.data) setA(startingAnswers(form.data));
  }, [form.data]);
  const save = useMutation({
    mutationFn: () => submitHealthForm(bookingId, a, form.data?.prefilled ?? false),
    onSuccess: (data) => {
      qc.setQueryData(['health-form', String(bookingId)], data);
      setConsent(false);
      setStillTrue(false);
      onDone?.();
    }
  });
  const withdraw = useMutation({
    mutationFn: () => withdrawHealthForm(bookingId),
    onSuccess: () => void form.refetch()
  });
  if (form.isLoading) return <p role="status">Loading health form…</p>;
  if (form.isError) return <p role="alert">{errorMessage(form.error)}</p>;
  const f = form.data!;
  if (!f.enabled) return <p role="status">The health form is not available right now. Tell the spa about any health conditions when you arrive.</p>;
  if (!f.editable)
    return <p>{f.status === 'SUBMITTED' ? 'You shared your health form with the spa.' : 'The health form is closed for this appointment.'}</p>;
  const set = (patch: Partial<HealthAnswers>) => setA({ ...a, ...patch });
  const text = (key: keyof HealthAnswers, label: string, max = 500, area = false) => (
    <label className="grid gap-1">
      {label}
      {area ? (
        <textarea className={field} maxLength={max} value={String(a[key] || '')} onChange={(e) => set({ [key]: e.target.value })} />
      ) : (
        <input className={field} maxLength={max} value={String(a[key] || '')} onChange={(e) => set({ [key]: e.target.value })} />
      )}
    </label>
  );
  return (
    <form
      className="space-y-4"
      autoComplete="off"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <h3 className="text-lg font-semibold">Health and consent form</h3>
      <p className="text-sm text-slate-600">
        Your answers help your therapist treat you safely. Only this spa can read them, they are stored encrypted, and
        they are deleted after your visit.
      </p>
      {f.status === 'SUBMITTED' && <p role="status">Shared with the spa. You can update it until your appointment.</p>}
      {f.status === 'UNREADABLE' && <p role="alert">We could not open your saved answers. Please fill in the form again.</p>}
      {f.prefilled && (
        <p className="rounded-xl bg-amber-50 p-3 text-amber-900" role="status">
          We filled this in from your last visit. Check each answer before you submit.
        </p>
      )}
      <YesNo label="Are you pregnant, or could you be?" value={a.pregnant} onChange={(v) => set({ pregnant: v })} />
      <fieldset className="space-y-1">
        <legend className="font-medium">Allergies</legend>
        {(Object.keys(allergyLabels) as Allergy[]).map((k) => (
          <label className="mr-4" key={k}>
            <input
              type="checkbox"
              checked={a.allergies.includes(k)}
              onChange={(e) => set({ allergies: e.target.checked ? [...a.allergies, k] : a.allergies.filter((x) => x !== k) })}
            />{' '}
            {allergyLabels[k]}
          </label>
        ))}
        {text('allergyDetails', 'Other allergies or details')}
      </fieldset>
      <YesNo label="Any injury or surgery in the last six months?" value={a.recentInjuryOrSurgery} onChange={(v) => set({ recentInjuryOrSurgery: v })}>
        {text('injuryDetails', 'What and where?')}
      </YesNo>
      <YesNo label="Any skin condition (eczema, rash, sunburn, open wounds)?" value={a.skinCondition} onChange={(v) => set({ skinCondition: v })}>
        {text('skinDetails', 'Please describe')}
      </YesNo>
      <YesNo label="High blood pressure or a heart condition?" value={a.heartCondition} onChange={(v) => set({ heartCondition: v })} />
      {text('medications', 'Medications you take (leave blank if none)')}
      <fieldset>
        <legend className="font-medium">Pressure you prefer</legend>
        {(Object.keys(pressureLabels) as Pressure[]).map((k) => (
          <label className="mr-4" key={k}>
            <input type="radio" checked={a.pressure === k} onChange={() => set({ pressure: k })} /> {pressureLabels[k]}
          </label>
        ))}
      </fieldset>
      {text('avoidAreas', 'Areas to avoid')}
      {a.custom.map((c, i) => (
        <label className="grid gap-1" key={c.questionId}>
          {c.prompt}
          <input
            className={field}
            maxLength={500}
            value={c.answer}
            onChange={(e) => set({ custom: a.custom.map((x, j) => (j === i ? { ...x, answer: e.target.value } : x)) })}
          />
        </label>
      ))}
      {text('notes', 'Anything else your therapist should know', 1000, true)}
      {f.prefilled && (
        <label className="block">
          <input type="checkbox" checked={stillTrue} onChange={(e) => setStillTrue(e.target.checked)} /> These answers are still
          true today
        </label>
      )}
      <div className="rounded-xl bg-stone-50 p-3 text-sm whitespace-pre-line">{f.terms}</div>
      <label className="block">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} /> I agree to the spa's terms above
      </label>
      {save.isError && <p role="alert">{errorMessage(save.error)}</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={save.isPending || !readyToSubmit(a, consent, f.prefilled, stillTrue)}>
          {f.status === 'SUBMITTED' ? 'Update health form' : 'Share with the spa'}
        </Button>
        {onDone && (
          <Button type="button" variant="ghost" onClick={onDone}>
            Skip for now
          </Button>
        )}
        {f.status === 'SUBMITTED' && (
          <Button
            type="button"
            variant="ghost"
            disabled={withdraw.isPending}
            onClick={() => {
              if (window.confirm('Delete your health form? The spa will no longer see it.')) withdraw.mutate();
            }}
          >
            Delete my answers
          </Button>
        )}
      </div>
    </form>
  );
}
