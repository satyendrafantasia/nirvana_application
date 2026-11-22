import ConnectGoogle from './ConnectGoogle';
import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import { errorMessage } from '../../api/client';
import { authView } from '../../api/authApi';
import {
  Account,
  changePassword,
  getAccount,
  passwordProblem,
  pointsSummary,
  ProfileInput,
  updateAccount
} from '../../api/accountApi';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';

const LOCALES: [string, string][] = [
  ['', 'Default'],
  ['en-IN', 'English (India)'],
  ['en-GB', 'English (United Kingdom)'],
  ['en-US', 'English (United States)'],
  ['hi-IN', 'Hindi (India)']
];

const timeZones = (current: string): string[] => {
  const all =
    (Intl as unknown as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf?.(
      'timeZone'
    ) ?? [];
  return all.includes(current) ? all : [current, ...all];
};

export default function AccountSettings() {
  const account = useQuery({ queryKey: ['account'], queryFn: getAccount });
  return (
    <div className="mx-auto my-10 max-w-2xl space-y-6 px-4">
      <h1 className="text-3xl font-bold">Your account</h1>
      {account.isPending && <p role="status">Loading your account…</p>}
      {account.isError && (
        <div className="card space-y-3 p-6">
          <p role="alert" className="text-rose-700">
            {errorMessage(account.error)}
          </p>
          <Button onClick={() => void account.refetch()}>Try again</Button>
        </div>
      )}
      {account.data && (
        <>
          <Points account={account.data} />
          <Profile key={account.data.id} account={account.data} />
          <ConnectGoogle key={account.data.id} account={account.data} />
          <Password />
          <section className="card space-y-2 p-6" aria-labelledby="benefits-heading">
            <h2 id="benefits-heading" className="text-xl font-semibold">
              Employer benefits
            </h2>
            <p className="text-sm text-slate-600">
              If your employer offers wellness benefits through Nirvana, you can see them and use
              your coupons here.
            </p>
            <Link className="underline" to="/features/benefits">
              View corporate benefits
            </Link>
          </section>
        </>
      )}
    </div>
  );
}

function Points({ account }: { account: Account }) {
  return (
    <section className="card space-y-2 p-6" aria-labelledby="points-heading">
      <h2 id="points-heading" className="text-xl font-semibold">
        Loyalty points
      </h2>
      <p className="text-3xl font-bold">{account.loyaltyPoints.toLocaleString('en-IN')}</p>
      <p>{pointsSummary(account.loyaltyPoints)}</p>
      <p className="text-sm text-slate-600">
        Each point takes one paisa (or one cent) off a booking. Points spent on a booking that is
        cancelled are returned to you.
      </p>
    </section>
  );
}

function Profile({ account }: { account: Account }) {
  const { user, setUser } = useAuth();
  const qc = useQueryClient();
  const [form, setForm] = useState<ProfileInput>({
    firstName: account.firstName || '',
    lastName: account.lastName || '',
    displayName: account.displayName || '',
    timezone: account.timezone || 'UTC',
    locale: account.locale || '',
    marketingOptIn: account.marketingOptIn,
    // Sent only when the guest can have WhatsApp updates, so the server keeps the current choice otherwise.
    ...(account.phone && account.phoneVerified ? { whatsappUpdates: Boolean(account.whatsappUpdates) } : {})
  });
  const [saved, setSaved] = useState(false);
  useEffect(() => setSaved(false), [form]);
  const save = useMutation({
    mutationFn: () => updateAccount(form),
    onSuccess: (updated) => {
      qc.setQueryData(['account'], updated);
      if (user) setUser({ ...user, firstName: updated.displayName || updated.firstName || user.firstName });
      setSaved(true);
    }
  });
  const set = <K extends keyof ProfileInput>(key: K, value: ProfileInput[K]) =>
    setForm((f) => ({ ...f, [key]: value }));
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };
  return (
    <section className="card space-y-4 p-6" aria-labelledby="profile-heading">
      <h2 id="profile-heading" className="text-xl font-semibold">
        Profile
      </h2>
      <dl className="grid gap-1 text-sm">
        <div>
          <dt className="inline font-medium">Email: </dt>
          <dd className="inline">
            {account.email || 'Not set'}
            {account.email && !account.emailVerified && ' (not verified)'}
          </dd>
        </div>
        <div>
          <dt className="inline font-medium">Phone: </dt>
          <dd className="inline">{account.phone || 'Not set'}</dd>
        </div>
      </dl>
      <p className="text-sm text-slate-600">
        To change your email or phone number,{' '}
        <Link className="underline" to="/features/messages">
          contact support
        </Link>
        .
      </p>
      <form className="space-y-4" onSubmit={submit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="First name"
            required
            maxLength={100}
            autoComplete="given-name"
            value={form.firstName}
            onChange={(e) => set('firstName', e.target.value)}
          />
          <Input
            label="Last name"
            maxLength={100}
            autoComplete="family-name"
            value={form.lastName}
            onChange={(e) => set('lastName', e.target.value)}
          />
        </div>
        <Input
          label="Name shown to spas"
          helperText="Leave empty to use your first name."
          maxLength={100}
          value={form.displayName}
          onChange={(e) => set('displayName', e.target.value)}
        />
        <label className="block text-sm font-medium text-slate-700">
          Time zone
          <select
            className="mt-1 block min-h-11 w-full rounded-xl border border-gray-200 bg-white px-3"
            value={form.timezone}
            onChange={(e) => set('timezone', e.target.value)}
          >
            {timeZones(form.timezone).map((z) => (
              <option key={z} value={z}>
                {z.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm font-medium text-slate-700">
          Language
          <select
            className="mt-1 block min-h-11 w-full rounded-xl border border-gray-200 bg-white px-3"
            value={form.locale}
            onChange={(e) => set('locale', e.target.value)}
          >
            {(LOCALES.some(([v]) => v === form.locale)
              ? LOCALES
              : [...LOCALES, [form.locale || '', form.locale || ''] as [string, string]]
            ).map(([value, name]) => (
              <option key={value} value={value}>
                {name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={form.marketingOptIn}
            onChange={(e) => set('marketingOptIn', e.target.checked)}
          />
          Send me offers and news by email
        </label>
        {account.phone && account.phoneVerified && (
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={Boolean(form.whatsappUpdates)}
              onChange={(e) => set('whatsappUpdates', e.target.checked)}
            />
            Send my booking confirmations and reminders on WhatsApp to {account.phone}
          </label>
        )}
        {save.isError && (
          <p role="alert" className="text-rose-700">
            {errorMessage(save.error)}
          </p>
        )}
        {saved && <p role="status">Your profile is saved.</p>}
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save profile'}
        </Button>
      </form>
    </section>
  );
}

function Password() {
  const { user, acceptAuth } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [problem, setProblem] = useState<string>();
  const [done, setDone] = useState(false);
  const change = useMutation({
    mutationFn: () => changePassword(current, next),
    onSuccess: (wire) => {
      // The old session was ended on every device; keep this one signed in with the new tokens.
      const fresh = authView(wire);
      acceptAuth({ token: fresh.token, user: user || fresh.user });
      setCurrent('');
      setNext('');
      setConfirmation('');
      setDone(true);
    }
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    setDone(false);
    const found = passwordProblem(current, next, confirmation);
    setProblem(found);
    if (!found) change.mutate();
  };
  return (
    <section className="card space-y-4 p-6" aria-labelledby="password-heading">
      <h2 id="password-heading" className="text-xl font-semibold">
        Change password
      </h2>
      <p className="text-sm text-slate-600">
        Changing your password signs you out on every other device. Signed up with Google or
        another provider, or forgotten your password?{' '}
        <Link className="underline" to="/auth/password">
          Reset it with a code by email
        </Link>
        .
      </p>
      <form className="space-y-4" onSubmit={submit}>
        <Input
          label="Current password"
          type="password"
          required
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
        <Input
          label="New password"
          type="password"
          required
          minLength={8}
          autoComplete="new-password"
          helperText="At least 8 characters."
          value={next}
          onChange={(e) => setNext(e.target.value)}
        />
        <Input
          label="Confirm new password"
          type="password"
          required
          autoComplete="new-password"
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
        />
        {(problem || change.isError) && (
          <p role="alert" className="text-rose-700">
            {problem || errorMessage(change.error)}
          </p>
        )}
        {done && <p role="status">Your password is changed. Other devices have been signed out.</p>}
        <Button type="submit" disabled={change.isPending}>
          {change.isPending ? 'Changing…' : 'Change password'}
        </Button>
      </form>
    </section>
  );
}
