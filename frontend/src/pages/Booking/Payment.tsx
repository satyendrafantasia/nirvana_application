import axios from 'axios';
import { track } from '../../telemetry';
import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { createBooking, CreatedBooking } from '../../api/bookingApi';
import { resumePayment, payWithRazorpay } from '../../api/paymentApi';
import client, { errorMessage } from '../../api/client';
import { getAccount, MAX_POINTS_PER_BOOKING } from '../../api/accountApi';
import { BookingSummary } from '../../types/booking';

type Mode = 'ONLINE' | 'OFFLINE';

/**
 * Two buttons: pay now with Razorpay, or pay at the spa. Razorpay is always shown; when the
 * backend has no Razorpay keys it is shown as unavailable rather than failing at checkout.
 */
export function PaymentChoice({
  value,
  online,
  byPackage = false,
  onChange
}: {
  value: Mode;
  /** Whether Razorpay is ready on the backend; undefined while that is still loading. */
  online?: boolean;
  byPackage?: boolean;
  onChange: (mode: Mode) => void;
}) {
  const choices: { mode: Mode; title: string; note: string; available: boolean }[] = [
    {
      mode: 'ONLINE',
      title: 'Pay now with Razorpay',
      note:
        online === false
          ? 'Online payment isn’t available yet. Please pay at the spa.'
          : 'UPI, cards, netbanking and wallets. Your slot is confirmed once paid.',
      available: online === true
    },
    {
      mode: 'OFFLINE',
      title: 'Pay at spa',
      note: 'Book now and pay the spa when you visit.',
      available: true
    }
  ];
  return (
    <div role="radiogroup" aria-label="Payment method" className="space-y-2">
      <p className="font-medium">Payment method</p>
      {byPackage ? (
        <p className="rounded-xl border border-primary-100 bg-primary-50 p-3 text-sm">
          Paid with your package. Nothing to pay now or at the spa.
        </p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {choices.map((c) => {
            const selected = value === c.mode && c.available;
            return (
              <button
                key={c.mode}
                type="button"
                role="radio"
                aria-checked={selected}
                disabled={!c.available}
                onClick={() => onChange(c.mode)}
                className={`rounded-xl border-2 p-4 text-left transition ${
                  selected
                    ? 'border-primary-700 bg-primary-50'
                    : 'border-slate-200 bg-white hover:border-primary-300'
                } disabled:cursor-not-allowed disabled:opacity-60`}
              >
                <span className="flex items-center gap-2 font-semibold text-slate-900">
                  <span
                    aria-hidden="true"
                    className={`inline-block h-4 w-4 rounded-full border-2 ${
                      selected ? 'border-primary-700 bg-primary-700' : 'border-slate-400'
                    }`}
                  />
                  {c.title}
                </span>
                <span className="mt-1 block text-sm text-slate-600">{c.note}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function Payment({
  onTimeConflict,onNext,
  onBack,
  onConfirm
}: {
  onTimeConflict:()=>void;onNext: () => void;
  onBack: () => void;
  onConfirm: (b: BookingSummary) => void;
}) {
  const { state,setSlot, setPaymentMode, setNotes } = useBookingFlow();
  const qc=useQueryClient();
  const [created, setCreated] = useState<CreatedBooking>();
  const submitting = useRef(false);
  const [coupon, setCoupon] = useState('');
  const [loyalty, setLoyalty] = useState(false);
  const [usePackage, setUsePackage] = useState(false);
  const [useSpaPackage, setUseSpaPackage] = useState(false);
  const account = useQuery({ queryKey: ['account'], queryFn: getAccount, staleTime: 30_000 });
  const options = useQuery({
    queryKey: ['payment-options'],
    queryFn: async () => (await client.get<{online: boolean}>('/api/bookings/payment-options')).data
  });
  // Razorpay first when it's switched on; pay at spa when it isn't, or when the guest chose it.
  const method = options.data?.online ? (state.paymentMode ?? 'ONLINE') : 'OFFLINE';
  const points = account.data?.loyaltyPoints;
  const eligibility = useQuery({
    queryKey: ['booking-package-eligibility', state.spaId, useSpaPackage],
    enabled: !!state.spaId && (usePackage || useSpaPackage) && !created,
    queryFn: async () =>
      (
        await client.get<{ canUse: boolean; reason?: string; remainingSessions?: number }>(
          useSpaPackage ? '/api/v1/user/packages/can-use' : '/api/packages/can-use',
          { params: { spaId: state.spaId } }
        )
      ).data
  });
  const mutation = useMutation({
    mutationFn: async () => {
      if (!state.spaId || !state.service || !state.selectedSlot || !method)
        throw new Error('Select a service and available appointment time.');
      if (!created) track('booking_submitted');
      const booking =
        created ||
        (await createBooking({
          holdToken: state.holdToken,
          spaId: state.spaId,
          serviceId: state.service.id,
          slotId: state.selectedSlot.id,
          guests: state.guests,
          therapistId: state.therapistId,
          therapistType: state.therapistType,
          paymentMode: method,
          paymentMethod: 'RAZORPAY',
          specialRequest: state.notes,
          couponCode: coupon.trim() || undefined,
          redeemLoyaltyPoints: loyalty,
          payWithPackage: usePackage,
          payWithSpaPackage: useSpaPackage,
          bookingSource: 'WEB'
        }));
      if (!created && coupon.trim() && !usePackage && !useSpaPackage) track('coupon_applied');
      setCreated(booking);
      let status = booking.status;
      if (booking.paymentMode === 'ONLINE' && booking.status === 'PENDING_PAYMENT')
        status = (
          await (booking.razorpay
            ? payWithRazorpay(booking.razorpay)
            : resumePayment(String(booking.bookingId)))
        ).status;
      return {
        ...booking,
        id: String(booking.bookingId),
        reference: booking.bookingReference,
        spaName: state.spaName || '',
        service: state.service,
        slot: state.slot || '',
        status
      };
    },
    onError: err => {track('booking_failed');void qc.invalidateQueries({queryKey:['slots']});if(!created && axios.isAxiosError(err) && err.response?.status === 409){setSlot(undefined);onTimeConflict();}},
    onSuccess: (b) => {
      if (loyalty) void qc.invalidateQueries({ queryKey: ['account'] }); // the balance changed
      if (b.status === 'CONFIRMED') track('booking_completed', b.id);
      onConfirm(b);
      onNext();
    }
  });
  return (
    <div className="space-y-5">
      <h2 className="text-2xl font-semibold">Complete your appointment</h2>
      <p>Final prices, discounts and eligibility are calculated by the spa before payment.</p>
      <fieldset disabled={!!created || mutation.isPending} className="space-y-4">
        <PaymentChoice
          value={method}
          online={options.data?.online}
          byPackage={usePackage || useSpaPackage}
          onChange={setPaymentMode}
        />
        <label className="block">
          Coupon code
          <input
            className="block border rounded-xl p-3 w-full"
            value={coupon}
            maxLength={64}
            onChange={(e) => setCoupon(e.target.value)}
          />
        </label>
        <label className="block">
          <input
            type="checkbox"
            checked={loyalty}
            disabled={points === 0}
            onChange={(e) => setLoyalty(e.target.checked)}
          />{' '}
          Redeem available loyalty points
          {points !== undefined && (
            <span className="block text-sm text-slate-600">
              {points === 0
                ? 'You have no points yet. You earn points when an appointment is completed.'
                : `You have ${points.toLocaleString('en-IN')} points. Up to ${Math.min(points, MAX_POINTS_PER_BOOKING).toLocaleString('en-IN')} can be used here, one paisa each.`}
            </span>
          )}
        </label>
        <label className="block">
          <input
            type="checkbox"
            checked={usePackage}
            onChange={(e) => {
              setUsePackage(e.target.checked);
              setUseSpaPackage(false);
            }}
          />{' '}
          Use a Nirvana central package I own
        </label>
        <label className="block">
          <input
            type="checkbox"
            checked={useSpaPackage}
            onChange={(e) => {
              setUseSpaPackage(e.target.checked);
              setUsePackage(false);
            }}
          />{' '}
          Use a package I bought for this spa
        </label>
        <label className="block">
          Special requests
          <textarea
            className="block border rounded-xl p-3 w-full"
            maxLength={2000}
            value={state.notes || ''}
            onChange={(e) => setNotes(e.target.value)}
          />
        </label>
      </fieldset>
      {(usePackage || useSpaPackage) && !created && (
        <p role="status">
          {eligibility.isPending
            ? 'Checking package eligibility…'
            : eligibility.isError
              ? errorMessage(eligibility.error)
              : eligibility.data?.canUse
                ? 'Your package is eligible for this spa.'
                : eligibility.data?.reason || 'This package cannot be used for this appointment.'}
        </p>
      )}
      {created && (
        <p>
          Reference: {created.bookingReference} · Total:{' '}
          {new Intl.NumberFormat('en-IN', { style: 'currency', currency: created.currency }).format(
            created.totalCents / 100
          )}
        </p>
      )}
      {mutation.isError && (
        <p role="alert" className="text-rose-700">
          {errorMessage(mutation.error)}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <Button variant="secondary" disabled={!!created || mutation.isPending} onClick={onBack}>
          Back
        </Button>
        <Button
          disabled={
            mutation.isPending || options.isPending ||
            (!created && (usePackage || useSpaPackage) && eligibility.data?.canUse !== true)
          }
          onClick={() => {
            if (submitting.current) return;
            submitting.current = true;
            mutation.mutate(undefined, { onSettled: () => { submitting.current = false; } });
          }}
        >
          {mutation.isPending ? 'Processing…' : created ? 'Retry payment' : 'Book appointment'}
        </Button>
      </div>
    </div>
  );
}
