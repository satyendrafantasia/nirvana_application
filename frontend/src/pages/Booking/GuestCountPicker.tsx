import { MAX_GROUP } from '../../api/groupApi';

/** "Just me / Couple / Group (up to 6)". Group sizes 3 to 6 are picked from a list. */
export default function GuestCountPicker({ value, onChange }: { value: number; onChange: (guests: number) => void }) {
  const choices = [
    { label: 'Just me', note: 'One appointment', size: 1, active: value === 1 },
    { label: 'Couple', note: 'Two guests at the same time', size: 2, active: value === 2 },
    { label: `Group (up to ${MAX_GROUP})`, note: 'Three or more guests together', size: Math.max(3, value), active: value >= 3 }
  ];
  return (
    <fieldset className="space-y-3">
      <legend className="font-medium text-slate-900">Who is coming?</legend>
      <div role="radiogroup" aria-label="Who is coming?" className="grid gap-3 sm:grid-cols-3">
        {choices.map((c) => (
          <button
            key={c.label}
            type="button"
            role="radio"
            aria-checked={c.active}
            onClick={() => onChange(c.size)}
            className={`rounded-xl border-2 p-4 text-left transition ${
              c.active ? 'border-primary-700 bg-primary-50' : 'border-slate-200 bg-white hover:border-primary-300'
            }`}
          >
            <span className="block font-semibold text-slate-900">{c.label}</span>
            <span className="mt-1 block text-sm text-slate-600">{c.note}</span>
          </button>
        ))}
      </div>
      {value >= 3 && (
        <label className="block text-sm">
          Number of guests, including you
          <select
            className="ml-2 rounded-xl border p-2"
            value={value}
            onChange={(e) => onChange(Number(e.target.value))}
          >
            {Array.from({ length: MAX_GROUP - 2 }, (_, i) => i + 3).map((n) => (
              <option key={n} value={n}>
                {n} guests
              </option>
            ))}
          </select>
        </label>
      )}
    </fieldset>
  );
}
