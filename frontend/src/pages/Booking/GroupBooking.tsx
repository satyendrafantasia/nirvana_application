import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Button from '../../components/common/Button';
import { getSpaServices } from '../../api/spaApi';
import { getTherapistDirectory } from '../../api/bookingApi';
import { payWithRazorpay } from '../../api/paymentApi';
import client, { errorMessage } from '../../api/client';
import {
  createGroup,
  getGroup,
  getGroupTimes,
  getSideBySide,
  GroupView,
  GuestChoice,
  holdGroup,
  readyForTimes
} from '../../api/groupApi';
import { therapistLabel } from './therapistLabel';
import { PaymentChoice } from './Payment';
import GroupConfirmation from './GroupConfirmation';
import { Service } from '../../types/spa';

type Step = 'guests' | 'time' | 'pay' | 'done';

const newKey = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `group-${Date.now()}-${Math.random()}`;

export function resize(guests: GuestChoice[], size: number, firstService?: string): GuestChoice[] {
  const next = guests.slice(0, size);
  while (next.length < size) next.push({ name: '', serviceId: next.length === 0 ? firstService : next[0]?.serviceId });
  return next;
}

/** One guest's service and therapist. Names are asked for everyone but the booker. */
export function GuestEditor({
  index,
  spaId,
  guest,
  services,
  sideBySide,
  onChange
}: {
  index: number;
  spaId: string;
  guest: GuestChoice;
  services: Service[];
  sideBySide?: Record<string, number>;
  onChange: (guest: GuestChoice) => void;
}) {
  const directory = useQuery({
    queryKey: ['therapist-directory', spaId, guest.serviceId],
    enabled: !!guest.serviceId,
    queryFn: () => getTherapistDirectory(spaId, guest.serviceId!)
  });
  const label = index === 0 ? 'You' : `Guest ${index + 1}`;
  return (
    <fieldset className="space-y-3 rounded-2xl border p-4" aria-label={label}>
      <legend className="px-1 font-semibold">{label}</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          {index === 0 ? 'Your name (optional)' : 'Name'}
          <input
            className="block w-full rounded-xl border p-2"
            value={guest.name}
            maxLength={120}
            required={index > 0}
            onChange={(e) => onChange({ ...guest, name: e.target.value })}
          />
        </label>
        {index > 0 && (
          <label className="block text-sm">
            Phone (optional)
            <input
              className="block w-full rounded-xl border p-2"
              type="tel"
              value={guest.phone || ''}
              maxLength={20}
              onChange={(e) => onChange({ ...guest, phone: e.target.value })}
            />
          </label>
        )}
        <label className="block text-sm">
          Treatment
          <select
            className="block w-full rounded-xl border p-2"
            value={guest.serviceId || ''}
            onChange={(e) => onChange({ ...guest, serviceId: e.target.value || undefined, preference: undefined })}
          >
            <option value="">Choose a treatment</option>
            {services.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} · {s.durationMinutes} min{sideBySide?.[s.id] ? ' · couples room' : ''}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm">
          Therapist
          <select
            className="block w-full rounded-xl border p-2"
            value={guest.preference || ''}
            disabled={!guest.serviceId}
            onChange={(e) => onChange({ ...guest, preference: e.target.value || undefined })}
          >
            <option value="">Anyone available</option>
            {directory.data?.labels.map((t) => (
              <option key={'type:' + t} value={'type:' + t}>
                Any {therapistLabel(t)}
              </option>
            ))}
            {directory.data?.named.map((t) => (
              <option key={t.id} value={String(t.id)}>
                {t.name}
                {t.type ? ' · ' + therapistLabel(t.type) : ''}
              </option>
            ))}
          </select>
        </label>
      </div>
    </fieldset>
  );
}

export default function GroupBooking({
  spaId,
  spaName,
  size,
  firstService,
  onBack
}: {
  spaId: string;
  spaName?: string;
  size: number;
  firstService?: string;
  onBack: () => void;
}) {
  const qc = useQueryClient();
  const [step, setStep] = useState<Step>('guests');
  const [guests, setGuests] = useState<GuestChoice[]>(() => resize([], size, firstService));
  useEffect(() => setGuests((g) => resize(g, size, firstService)), [size, firstService]);
  const [date, setDate] = useState(() => new Date().toLocaleDateString('en-CA'));
  const [start, setStart] = useState<string>();
  const [hold, setHold] = useState<{ holdToken: string; expiresAt: string; therapists: (string | null)[] }>();
  const [mode, setMode] = useState<'ONLINE' | 'OFFLINE'>('ONLINE');
  const [coupon, setCoupon] = useState('');
  const [loyalty, setLoyalty] = useState(false);
  const [giftCard, setGiftCard] = useState('');
  const [notes, setNotes] = useState('');
  const [result, setResult] = useState<GroupView>();
  const [progress, setProgress] = useState('');
  const key = useRef(newKey());

  const services = useQuery({ queryKey: ['booking-services', spaId], queryFn: () => getSpaServices(spaId) });
  const sideBySide = useQuery({ queryKey: ['side-by-side', spaId], queryFn: () => getSideBySide(spaId) });
  const ready = readyForTimes(guests);
  const named = guests.map((g) => g.preference).filter((p) => p && !p.startsWith('type:'));
  const duplicateTherapist = new Set(named).size !== named.length;
  const times = useQuery({
    queryKey: ['group-times', spaId, date, guests.map((g) => [g.serviceId, g.preference])],
    enabled: step === 'time' && ready && !!date,
    queryFn: () => getGroupTimes(spaId, date, guests),
    refetchInterval: 15000
  });
  const options = useQuery({
    queryKey: ['payment-options'],
    queryFn: async () => (await client.get<{ online: boolean }>('/api/bookings/payment-options')).data
  });
  const method = options.data?.online ? mode : 'OFFLINE';

  const reserve = useMutation({
    mutationFn: (time: string) => holdGroup(spaId, time, guests),
    onSuccess: (data, time) => {
      setStart(time);
      setHold(data);
      setStep('pay');
    },
    onError: () => void times.refetch()
  });

  const book = useMutation({
    mutationFn: async () => {
      if (!start) throw new Error('Choose a time for your group.');
      setProgress('Confirming your group…');
      let group =
        result ||
        (await createGroup({
          spaId,
          startTs: start,
          holdToken: hold?.holdToken,
          paymentMode: method,
          couponCode: coupon.trim() || undefined,
          redeemLoyaltyPoints: loyalty,
          giftCardCode: giftCard.trim() || undefined,
          specialRequest: notes || undefined,
          requestKey: key.current,
          guests
        }));
      setResult(group);
      if (group.razorpay && group.members.some((m) => m.status === 'PENDING_PAYMENT')) {
        setProgress('Waiting for payment…');
        await payWithRazorpay(group.razorpay, () => setProgress('Verifying payment…'));
        group = await getGroup(group.groupId);
      }
      return group;
    },
    onSuccess: (group) => {
      setResult(group);
      void qc.invalidateQueries({ queryKey: ['my-bookings'] });
      void qc.invalidateQueries({ queryKey: ['my-booking-groups'] });
      setStep('done');
    }
  });

  if (step === 'done' && result) return <GroupConfirmation group={result} />;

  return (
    <div className="space-y-5">
      <h2 className="text-2xl font-semibold">{size === 2 ? 'Book for two' : `Book for ${size} guests`}</h2>
      <p className="text-slate-600">
        Everyone starts at the same time{spaName ? ` at ${spaName}` : ''}, each with their own treatment and therapist.
      </p>

      {step === 'guests' && (
        <>
          {services.isError && <p role="alert">Unable to load treatments. {errorMessage(services.error)}</p>}
          {guests.map((g, i) => (
            <GuestEditor
              key={i}
              index={i}
              spaId={spaId}
              guest={g}
              services={services.data || []}
              sideBySide={sideBySide.data}
              onChange={(next) => setGuests((all) => all.map((x, j) => (j === i ? next : x)))}
            />
          ))}
          {duplicateTherapist && <p role="alert">Each guest needs a different therapist.</p>}
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={onBack}>
              Back
            </Button>
            <Button disabled={!ready || guests.slice(1).some((g) => !g.name.trim())} onClick={() => setStep('time')}>
              Find times for everyone
            </Button>
          </div>
        </>
      )}

      {step === 'time' && (
        <>
          <label className="block">
            Date
            <input className="ml-2 rounded-xl border p-2" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
          {times.isLoading && <p role="status">Finding times that fit your whole group…</p>}
          {times.isError && <p role="alert">{errorMessage(times.error)}</p>}
          {reserve.isError && <p role="alert">{errorMessage(reserve.error)}</p>}
          {times.data?.length === 0 && <p>No time that day fits everyone. Try another date or different treatments.</p>}
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-6" aria-label="Times that fit everyone">
            {times.data?.map((t) => (
              <Button key={t.startTs} variant="secondary" disabled={reserve.isPending} onClick={() => reserve.mutate(t.startTs)}>
                {new Date(t.startTs).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </Button>
            ))}
          </div>
          <Button variant="secondary" onClick={() => setStep('guests')}>
            Back
          </Button>
        </>
      )}

      {step === 'pay' && start && (
        <>
          <div className="rounded-2xl bg-gray-50 p-4">
            <p className="font-semibold">{new Date(start).toLocaleString(undefined, { dateStyle: 'long', timeStyle: 'short' })}</p>
            <ul className="mt-2 list-disc pl-5 text-sm">
              {guests.map((g, i) => (
                <li key={i}>
                  {i === 0 ? g.name.trim() || 'You' : g.name} · {services.data?.find((s) => s.id === g.serviceId)?.name}
                  {hold?.therapists[i] ? ` · ${hold.therapists[i]}` : ''}
                </li>
              ))}
            </ul>
            {hold && <p className="mt-2 text-sm">Held for your group until {new Date(hold.expiresAt).toLocaleTimeString()}.</p>}
          </div>
          <fieldset disabled={book.isPending || !!result}>
            <PaymentChoice value={method} online={options.data?.online} onChange={setMode} />
          </fieldset>
          <fieldset disabled={book.isPending || !!result} className="space-y-3">
            <p className="text-sm text-slate-600">
              One payment covers the whole group. A coupon, points or a gift card apply once to the group total and are shared
              across the guests. Wallet credit and packages can&rsquo;t be used for group bookings.
            </p>
            <label className="block">
              Coupon code
              <input className="block w-full rounded-xl border p-3" value={coupon} maxLength={64} onChange={(e) => setCoupon(e.target.value)} />
            </label>
            <label className="block">
              <input type="checkbox" checked={loyalty} onChange={(e) => setLoyalty(e.target.checked)} /> Redeem available loyalty points
            </label>
            <label className="block">
              Gift card code
              <input className="block w-full rounded-xl border p-3 uppercase" value={giftCard} maxLength={32} onChange={(e) => setGiftCard(e.target.value)} />
            </label>
            <label className="block">
              Special requests
              <textarea className="block w-full rounded-xl border p-3" maxLength={2000} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </label>
          </fieldset>
          {book.isError && (
            <p role="alert" className="text-rose-700">
              {errorMessage(book.error)}
            </p>
          )}
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" disabled={book.isPending || !!result} onClick={() => setStep('time')}>
              Back
            </Button>
            <Button pressFeedback disabled={book.isPending || options.isPending} onClick={() => book.mutate()}>
              {book.isPending ? progress || 'Confirming…' : result ? 'Retry payment' : `Book for ${size}`}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
