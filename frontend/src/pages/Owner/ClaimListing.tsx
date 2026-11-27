import { FormEvent, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { usePhoneCode } from '../../api/phoneCodes';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';

export type ClaimResult = {
  placeId: string;
  name: string;
  address: string;
  businessStatus?: string;
  alreadyListed: boolean;
  claimableExisting?: boolean;
};

export type ClaimForm = {
  placeId: string;
  name: string;
  phone: string;
  email?: string;
  websiteUrl?: string;
  description?: string;
  addressLine: string;
  locality?: string;
  city: string;
  state?: string;
  postalCode?: string;
  countryCode: string;
  latitude?: number;
  longitude?: number;
  timezone: string;
  ownerName?: string;
  termsVersion: string;
  declaration?: string;
  /** Whether Google lists a number we can text; never sent back. */
  listingHasPhone?: boolean;
};

/** `phoneNumber` comes back only with Firebase, which texts the listing's (public) number from the browser. */
type CodeSent = {
  verificationId: string;
  phone: string;
  phoneNumber?: string;
  expiresInMinutes: number;
};

/** Texts a code to the phone on the Google listing to show the owner can answer it. */
export function PhoneCheck({
  placeId,
  hasPhone,
  verifiedId,
  skipped,
  onVerified,
  onSkip
}: {
  placeId: string;
  hasPhone: boolean;
  verifiedId?: string;
  skipped: boolean;
  onVerified: (verificationId: string) => void;
  onSkip: (skip: boolean) => void;
}) {
  const [sent, setSent] = useState<CodeSent>();
  const [code, setCode] = useState('');
  const phone = usePhoneCode();
  const send = useMutation({
    mutationFn: async () => {
      const { data } = await client.post<CodeSent>(
        `/api/owner/claims/places/${encodeURIComponent(placeId)}/phone-code`
      );
      if (data.phoneNumber) await phone.start(data.phoneNumber);
      else phone.reset();
      return data;
    },
    onSuccess: (data) => {
      setSent(data);
      setCode('');
    }
  });
  const check = useMutation({
    mutationFn: async () =>
      client.post('/api/owner/claims/phone-code/verify', {
        verificationId: sent!.verificationId,
        ...(await phone.proof(code))
      }),
    onSuccess: () => onVerified(sent!.verificationId)
  });
  if (verifiedId)
    return (
      <p className="rounded-xl bg-green-50 p-4 text-green-900" role="status">
        ✓ Phone verified. We will show the review team that you answered the number on your Google
        listing.
      </p>
    );
  if (!hasPhone)
    return (
      <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
        Your Google listing has no phone number we can text, so our team will contact you to confirm
        you manage this spa.
      </p>
    );
  if (skipped)
    return (
      <div className="space-y-2 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
        <p>
          Our team will call the number on your Google listing to confirm you manage this spa.
          Review may take longer this way.
        </p>
        <button type="button" className="underline" onClick={() => onSkip(false)}>
          Verify by text message instead
        </button>
      </div>
    );
  return (
    <div className="space-y-3 rounded-xl border p-4">
      <p>
        We will text a 6-digit code to the phone number on your Google listing — not the one you
        typed above. This shows our team that you can answer the spa’s phone.
      </p>
      {sent && (
        <div className="space-y-3">
          <p role="status" className="text-sm text-slate-700">
            Code sent to <strong>{sent.phone}</strong>. It expires in {sent.expiresInMinutes}{' '}
            minutes.
          </p>
          <div className="flex flex-wrap items-end gap-3">
            <div className="w-40">
              <Input
                label="Code"
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="\d{6}"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              />
            </div>
            <Button
              type="button"
              disabled={code.length !== 6 || check.isPending}
              onClick={() => check.mutate()}
            >
              {check.isPending ? 'Checking…' : 'Verify'}
            </Button>
          </div>
        </div>
      )}
      {(send.isError || check.isError) && (
        <p role="alert" className="text-rose-700">
          {errorMessage(check.error || send.error)}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant={sent ? 'secondary' : 'primary'}
          disabled={send.isPending}
          onClick={() => {
            check.reset();
            send.mutate();
          }}
        >
          {send.isPending ? 'Sending…' : sent ? 'Send a new code' : 'Text me a code'}
        </Button>
        <button type="button" className="text-sm underline" onClick={() => onSkip(true)}>
          I can’t get texts on that number
        </button>
      </div>
      <div ref={phone.slot} />
    </div>
  );
}

const countries = [
  ['IN', 'India'],
  ['AE', 'United Arab Emirates'],
  ['TH', 'Thailand'],
  ['ID', 'Indonesia'],
  ['LK', 'Sri Lanka'],
  ['NP', 'Nepal'],
  ['SG', 'Singapore'],
  ['GB', 'United Kingdom'],
  ['US', 'United States']
];

const steps = ['Find your spa', 'Check your details', 'Submitted'];

function Steps({ current }: { current: number }) {
  return (
    <ol className="flex flex-wrap gap-2 text-sm" aria-label="Claim progress">
      {steps.map((s, i) => (
        <li
          key={s}
          aria-current={i === current ? 'step' : undefined}
          className={`rounded-full px-3 py-1 ${i === current ? 'bg-primary-700 text-white' : i < current ? 'bg-primary-100 text-primary-900' : 'bg-slate-100 text-slate-600'}`}
        >
          {i + 1}. {s}
        </li>
      ))}
    </ol>
  );
}

/** Step 1: the owner searches Google Maps for their own listing. */
export function ClaimSearch({ onPick }: { onPick: (placeId: string) => void }) {
  const [query, setQuery] = useState('');
  const [countryCode, setCountryCode] = useState('IN');
  const search = useMutation({
    mutationFn: async () =>
      (
        await client.post<ClaimResult[]>('/api/owner/claims/search', {
          query: query.trim(),
          countryCode
        })
      ).data
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (query.trim().length >= 2) search.mutate();
  };
  return (
    <section className="card space-y-4 p-6" aria-labelledby="claim-search">
      <h2 id="claim-search" className="text-xl font-semibold">
        Find your spa on Google Maps
      </h2>
      <p className="text-sm text-slate-600">
        Search with your spa’s name and area. We use your Google listing to fill in the form — you
        check and correct every detail before anything is saved.
      </p>
      <form className="grid gap-3 sm:grid-cols-[1fr_12rem_auto] sm:items-end" onSubmit={submit}>
        <Input
          label="Spa name and area"
          placeholder="e.g. Lotus Day Spa, Indiranagar"
          value={query}
          minLength={2}
          maxLength={200}
          required
          onChange={(e) => setQuery(e.target.value)}
        />
        <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
          Country
          <select
            className="min-h-11 rounded-xl border border-gray-200 bg-white px-3"
            value={countryCode}
            onChange={(e) => setCountryCode(e.target.value)}
          >
            {countries.map(([code, name]) => (
              <option key={code} value={code}>
                {name}
              </option>
            ))}
          </select>
        </label>
        <Button type="submit" disabled={search.isPending}>
          {search.isPending ? 'Searching…' : 'Search'}
        </Button>
      </form>
      {search.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(search.error)}
        </p>
      )}
      {search.data && (
        <div aria-live="polite" className="space-y-3">
          {search.data.length === 0 ? (
            <p>
              No spa listings matched. Try another spelling or nearby landmark, or{' '}
              <Link className="underline" to="/owner/onboarding">
                create your spa from scratch
              </Link>
              .
            </p>
          ) : (
            <ul className="divide-y rounded-xl border">
              {search.data.map((r) => (
                <li
                  key={r.placeId}
                  className="flex flex-wrap items-center justify-between gap-3 p-4"
                >
                  <div className="min-w-0">
                    <p className="font-semibold">{r.name}</p>
                    <p className="text-sm text-slate-600">{r.address}</p>
                    {r.businessStatus && r.businessStatus !== 'OPERATIONAL' && (
                      <p className="text-xs text-amber-700">
                        Google shows this as {r.businessStatus.replaceAll('_', ' ').toLowerCase()}
                      </p>
                    )}
                  </div>
                  {r.alreadyListed && !r.claimableExisting ? (
                    <span className="text-sm text-slate-600">
                      Already on Nirvana or under review
                    </span>
                  ) : (
                    <Button variant="secondary" onClick={() => onPick(r.placeId)}>
                      {r.claimableExisting ? 'Claim existing spa' : 'This is my spa'}
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
          <p className="text-sm text-slate-600">
            Not listed on Google Maps?{' '}
            <Link className="underline" to="/owner/onboarding">
              Create your spa from scratch
            </Link>
          </p>
        </div>
      )}
    </section>
  );
}

/** Step 2: the Google details, editable; only what the owner submits is saved. */
export function ClaimDetails({
  initial: { declaration, listingHasPhone, ...initialFields },
  onBack,
  onDone
}: {
  initial: ClaimForm;
  onBack: () => void;
  onDone: (spaId: number, existingInventory?: boolean) => void;
}) {
  const qc = useQueryClient();
  const [form, setForm] = useState(initialFields);
  const [authorised, setAuthorised] = useState(false);
  const [phoneVerificationId, setPhoneVerificationId] = useState<string>();
  const [skipPhone, setSkipPhone] = useState(false);
  const hasPhone = listingHasPhone !== false;
  const phoneSettled = !!phoneVerificationId || skipPhone || !hasPhone;
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [key]: e.target.value });
  const submit = useMutation({
    mutationFn: async () => {
      return (
        await client.post<{ spaId: number; existingInventory?: boolean }>('/api/owner/claims', {
          ...form,
          authorised,
          phoneVerificationId
        })
      ).data;
    },
    onSuccess: (data) => {
      void qc.invalidateQueries({ queryKey: ['owner-spas'] });
      void qc.invalidateQueries({ queryKey: ['draft-spas'] });
      onDone(data.spaId, data.existingInventory);
    }
  });
  const pin =
    form.latitude != null && form.longitude != null
      ? `https://www.google.com/maps/search/?api=1&query=${form.latitude},${form.longitude}`
      : undefined;
  return (
    <form
      className="card space-y-6 p-6"
      onSubmit={(e) => {
        e.preventDefault();
        submit.mutate();
      }}
    >
      <div className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
        We filled this in from your Google Maps listing. Check every field and correct anything that
        is out of date — what you submit becomes your spa’s details on Nirvana.
      </div>
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-2 text-lg font-semibold">Your spa</legend>
        <Input label="Spa name" required maxLength={200} value={form.name} onChange={set('name')} />
        <Input
          label="Phone"
          type="tel"
          required
          pattern="[+0-9 ()\-]{6,30}"
          value={form.phone}
          onChange={set('phone')}
          helperText="Guests see this only after opening more details."
        />
        <Input
          label="Email"
          type="email"
          maxLength={200}
          value={form.email || ''}
          onChange={set('email')}
        />
        <Input
          label="Website"
          type="url"
          maxLength={500}
          placeholder="https://"
          value={form.websiteUrl || ''}
          onChange={set('websiteUrl')}
        />
        <label className="flex flex-col gap-1 text-sm font-medium text-slate-700 sm:col-span-2">
          About your spa
          <textarea
            className="min-h-28 rounded-xl border border-gray-200 bg-white px-3 py-2"
            maxLength={2000}
            value={form.description || ''}
            onChange={set('description')}
            placeholder="Your treatments, setting and what guests love about you."
          />
        </label>
      </fieldset>
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-2 text-lg font-semibold">Address</legend>
        <div className="sm:col-span-2">
          <Input
            label="Street address"
            required
            maxLength={255}
            value={form.addressLine}
            onChange={set('addressLine')}
          />
        </div>
        <Input
          label="Area / locality"
          maxLength={255}
          value={form.locality || ''}
          onChange={set('locality')}
        />
        <Input label="City" required maxLength={100} value={form.city} onChange={set('city')} />
        <Input label="State" maxLength={100} value={form.state || ''} onChange={set('state')} />
        <Input
          label="Postal code"
          maxLength={20}
          value={form.postalCode || ''}
          onChange={set('postalCode')}
        />
        <Input
          label="Country code"
          required
          pattern="[A-Za-z]{2}"
          maxLength={2}
          value={form.countryCode}
          onChange={set('countryCode')}
        />
        <Input
          label="Time zone"
          required
          maxLength={64}
          placeholder="Asia/Kolkata"
          value={form.timezone}
          onChange={set('timezone')}
        />
        <p className="text-sm text-slate-600 sm:col-span-2">
          {pin ? (
            <>
              Map pin taken from your Google listing.{' '}
              <a className="underline" href={pin} target="_blank" rel="noreferrer">
                Check it on Google Maps
              </a>
              . If it is wrong, tell us during review and we will correct it.
            </>
          ) : (
            'Your Google listing has no map pin; guests will be directed by address.'
          )}
        </p>
      </fieldset>
      <fieldset className="space-y-3">
        <legend className="mb-2 text-lg font-semibold">Verify you manage this spa</legend>
        <PhoneCheck
          placeId={form.placeId}
          hasPhone={hasPhone}
          verifiedId={phoneVerificationId}
          skipped={skipPhone}
          onVerified={setPhoneVerificationId}
          onSkip={setSkipPhone}
        />
      </fieldset>
      <fieldset className="space-y-4">
        <legend className="mb-2 text-lg font-semibold">You</legend>
        <Input
          label="Your full legal name"
          required
          maxLength={200}
          value={form.ownerName || ''}
          onChange={set('ownerName')}
          helperText="As it appears on your business documents. We check it during verification."
        />
        <label className="flex items-start gap-3 rounded-xl border p-4">
          <input
            type="checkbox"
            className="mt-1"
            required
            checked={authorised}
            onChange={(e) => setAuthorised(e.target.checked)}
          />
          <span>
            {declaration ||
              'I own or am authorised to manage this spa, and the details I submitted are correct.'}{' '}
            <Link className="underline" to="/terms" target="_blank">
              Read the terms
            </Link>
          </span>
        </label>
      </fieldset>
      {submit.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(submit.error)}
        </p>
      )}
      {!phoneSettled && (
        <p className="text-sm text-slate-600">
          Verify your phone above, or choose “I can’t get texts on that number”, to submit.
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <Button type="button" variant="secondary" onClick={onBack} disabled={submit.isPending}>
          Back to search
        </Button>
        <Button type="submit" disabled={submit.isPending || !authorised || !phoneSettled}>
          {submit.isPending ? 'Submitting…' : 'Submit claim for review'}
        </Button>
      </div>
    </form>
  );
}

export function ClaimSubmitted({
  spaId,
  existingInventory
}: {
  spaId: number;
  existingInventory?: boolean;
}) {
  if (existingInventory)
    return (
      <section className="card space-y-3 p-6" role="status">
        <h2 className="text-xl font-semibold">Ownership claim submitted</h2>
        <p>
          Our team will verify that you manage spa #{spaId}. Once approved, its existing services,
          appointments and package records will be available in your owner dashboard.
        </p>
        <Link className="underline" to="/owner">
          Go to owner dashboard
        </Link>
      </section>
    );
  return (
    <section className="card space-y-4 p-6" role="status">
      <h2 className="text-xl font-semibold">Claim submitted — we’re reviewing it</h2>
      <p>
        Your spa (#{spaId}) is saved but not yet visible to guests. Our team will check that you
        manage this spa and let you know. While you wait, get your spa ready:
      </p>
      <ul className="list-disc space-y-1 pl-5">
        <li>
          <Link className="underline" to="/owner/services">
            Add your treatments and prices
          </Link>
        </li>
        <li>
          <Link className="underline" to="/owner/packages">
            Create packages to sell
          </Link>
        </li>
        <li>
          <Link className="underline" to="/owner/onboarding">
            Upload verification documents
          </Link>{' '}
          to speed up approval
        </li>
      </ul>
    </section>
  );
}

export default function ClaimListing() {
  const [params, setParams] = useSearchParams();
  const placeId = params.get('place') || '';
  const [spaId, setSpaId] = useState<number>();
  const [existingInventory, setExistingInventory] = useState(false);
  const prefill = useQuery({
    queryKey: ['claim-prefill', placeId],
    queryFn: async () =>
      (await client.get<ClaimForm>('/api/owner/claims/places/' + encodeURIComponent(placeId))).data,
    enabled: /^[A-Za-z0-9_-]{1,255}$/.test(placeId) && !spaId,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false
  });
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [placeId, spaId]);
  const pick = (id: string) => setParams({ place: id });
  const back = () => setParams({});
  const current = spaId ? 2 : placeId ? 1 : 0;
  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div className="space-y-2">
        <h1 className="text-2xl font-bold">Claim your spa’s listing</h1>
        <Steps current={current} />
      </div>
      {spaId ? (
        <ClaimSubmitted spaId={spaId} existingInventory={existingInventory} />
      ) : !placeId ? (
        <ClaimSearch onPick={pick} />
      ) : prefill.data ? (
        <ClaimDetails
          key={placeId}
          initial={prefill.data}
          onBack={back}
          onDone={(id, existing) => {
            setSpaId(id);
            setExistingInventory(!!existing);
          }}
        />
      ) : prefill.isError ? (
        <div className="card space-y-3 p-6" role="alert">
          <p>{errorMessage(prefill.error)}</p>
          <Button variant="secondary" onClick={back}>
            Search again
          </Button>
        </div>
      ) : (
        <p role="status">Loading your listing…</p>
      )}
    </div>
  );
}
