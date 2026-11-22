import Input from './Input';
import type { PhoneCodeMode } from '../../api/phoneCodes';

export type ContactKind = 'email' | 'phone';

/**
 * Email or phone for a one-time code. The phone option only appears when the site can send phone
 * codes; Indian mobiles can be typed without +91.
 */
export default function ContactChoice({
  mode,
  kind,
  onKind,
  value,
  onChange,
  emailLabel = 'Email'
}: {
  mode: PhoneCodeMode | undefined;
  kind: ContactKind;
  onKind: (kind: ContactKind) => void;
  value: string;
  onChange: (value: string) => void;
  emailLabel?: string;
}) {
  const phoneAvailable = mode === 'FIREBASE' || mode === 'SMS';
  return (
    <div className="space-y-3">
      {phoneAvailable && (
        <div role="radiogroup" aria-label="Send the code to" className="flex gap-2">
          {(['email', 'phone'] as const).map((k) => (
            <button
              key={k}
              type="button"
              role="radio"
              aria-checked={kind === k}
              onClick={() => {
                if (kind !== k) onChange('');
                onKind(k);
              }}
              className={`rounded-full border px-4 py-1.5 text-sm font-semibold ${
                kind === k
                  ? 'border-primary-600 bg-primary-50 text-primary-800'
                  : 'border-slate-300 text-slate-600'
              }`}
            >
              {k === 'email' ? 'Email' : 'Mobile number'}
            </button>
          ))}
        </div>
      )}
      {kind === 'phone' && phoneAvailable ? (
        <Input
          label="Mobile number"
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          placeholder="98765 43210"
          required
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      ) : (
        <Input
          label={emailLabel}
          type="email"
          autoComplete="email"
          required
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      )}
    </div>
  );
}
