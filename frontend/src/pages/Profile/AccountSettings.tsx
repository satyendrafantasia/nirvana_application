import { showToast } from '../../components/common/Toast';
import ConnectGoogle from './ConnectGoogle';
import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import client, { errorMessage } from '../../api/client';
import { authView, requestOtp } from '../../api/authApi';
import { otpBody, usePhoneCode } from '../../api/phoneCodes';
import {
  Account,
  changePassword,
  downloadMyData,
  getAccount,
  getDeletionStatus,
  passwordProblem,
  ProfileInput,
  requestDeletion,
  updateAccount
} from '../../api/accountApi';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import { LoyaltyPoints, ReferFriends } from './Rewards';

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
          <LoyaltyPoints />
          <ReferFriends />
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
          <YourData />
          <DeleteAccount account={account.data} />
        </>
      )}
    </div>
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
      showToast('Profile updated.', 'success');
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
        <Link className="underline" to="/support">
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

function YourData() {
  const download = useMutation({ mutationFn: downloadMyData });
  return (
    <section className="card space-y-3 p-6" aria-labelledby="data-heading">
      <h2 id="data-heading" className="text-xl font-semibold">
        Your data
      </h2>
      <p className="text-sm text-slate-600">
        Download a copy of the personal data Nirvana holds about you: your profile, bookings,
        payments, packages, reviews, consents and notification choices. The file is JSON, a format
        other services can read.
      </p>
      {download.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(download.error)}
        </p>
      )}
      <Button type="button" disabled={download.isPending} onClick={() => download.mutate()}>
        {download.isPending ? 'Preparing your file…' : 'Download my data'}
      </Button>
    </section>
  );
}

function DeleteAccount({ account }: { account: Account }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const status = useQuery({ queryKey: ['account-deletion'], queryFn: getDeletionStatus });
  const phone = usePhoneCode();
  const [open, setOpen] = useState(false);
  const [useCode, setUseCode] = useState(false);
  const [password, setPassword] = useState('');
  const [challenge, setChallenge] = useState('');
  const [code, setCode] = useState('');
  const [understood, setUnderstood] = useState(false);
  const mobile = account.phoneVerified && account.phone ? account.phone : '';
  const email = account.emailVerified && account.email ? account.email : '';
  const sendCode = useMutation({
    mutationFn: async () => {
      if (!mobile && !email)
        throw new Error('Your account has no verified email or mobile to send a code to.');
      const result = await requestOtp(mobile ? { phoneNumber: mobile } : { email });
      if (mobile) await phone.start(mobile);
      setChallenge(result.verificationId);
    }
  });
  const remove = useMutation({
    mutationFn: async () => {
      if (!useCode) return requestDeletion({ password });
      const { data } = await client.post<{ loginToken: string }>('/api/auth/login/otp/verify', {
        verificationId: challenge,
        ...otpBody(await phone.proof(code))
      });
      return requestDeletion({ loginToken: data.loginToken });
    },
    onSuccess: (result) => {
      // Every session has already ended on the server; finish signing out here too.
      logout();
      navigate('/privacy', { replace: true, state: { deletionDate: result.eraseAfter } });
    }
  });
  const graceDays = status.data?.graceDays ?? 7;
  const blockers = status.data?.blockers ?? [];
  const ready = understood && (useCode ? Boolean(challenge) && code.length === 6 : password.length > 0);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (ready) remove.mutate();
  };
  const switchProof = () => {
    setUseCode(!useCode);
    setChallenge('');
    setCode('');
    phone.reset();
    sendCode.reset();
    remove.reset();
  };
  return (
    <section className="card space-y-4 p-6" aria-labelledby="delete-heading">
      <h2 id="delete-heading" className="text-xl font-semibold">
        Delete your account
      </h2>
      <p className="text-sm text-slate-600">
        Your account is deleted {graceDays} days after you ask. Until then you can change your
        mind: sign in again and the deletion is cancelled. After that, your name, email, phone,
        photo, sign-in methods, saved spas and preferences are erased, and your reviews stay
        without your name. Unused loyalty points, vouchers and package sessions are lost. Records
        of bookings, payments, invoices and refunds are kept without your details, as tax and
        accounting law requires.
      </p>
      {blockers.length > 0 && (
        <div role="alert" className="space-y-1 text-rose-700">
          {blockers.map((b) => (
            <p key={b}>{b}</p>
          ))}
        </div>
      )}
      {!open ? (
        <Button
          type="button"
          variant="ghost"
          disabled={!status.data || blockers.length > 0}
          onClick={() => setOpen(true)}
        >
          Delete my account
        </Button>
      ) : (
        <form className="space-y-4" onSubmit={submit}>
          {!useCode ? (
            <Input
              label="Your password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          ) : challenge ? (
            <Input
              label="Verification code"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            />
          ) : (
            <Button type="button" disabled={sendCode.isPending} onClick={() => sendCode.mutate()}>
              {sendCode.isPending ? 'Sending…' : `Send a code to ${mobile || email}`}
            </Button>
          )}
          {(mobile || email) && (
            <button type="button" className="text-sm underline" onClick={switchProof}>
              {useCode ? 'Use my password instead' : 'No password? Confirm with a code instead'}
            </button>
          )}
          <label className="flex items-start gap-2">
            <input
              type="checkbox"
              checked={understood}
              onChange={(e) => setUnderstood(e.target.checked)}
            />
            <span>
              I understand my account will be deleted after {graceDays} days unless I sign in again
              before then.
            </span>
          </label>
          {(sendCode.isError || remove.isError) && (
            <p role="alert" className="text-rose-700">
              {errorMessage(sendCode.error || remove.error)}
            </p>
          )}
          <Button type="submit" disabled={!ready || remove.isPending}>
            {remove.isPending ? 'Deleting…' : 'Delete my account'}
          </Button>
        </form>
      )}
      <div ref={phone.slot} />
    </section>
  );
}
