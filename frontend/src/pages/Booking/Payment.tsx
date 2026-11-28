import { showToast } from '../../components/common/Toast';
import axios from 'axios';
import { track } from '../../telemetry';
import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { createBooking, CreatedBooking, isSlotConflict } from '../../api/bookingApi';
import { resumePayment, payAtSpa, payWithRazorpay } from '../../api/paymentApi';
import client, { errorMessage } from '../../api/client';
import { money, previewCheckout } from '../../api/rewardsApi';
import CheckoutSummary from './CheckoutSummary';
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
  const [progress, setProgress] = useState('');
  const [coupon, setCoupon] = useState('');
  const [loyalty, setLoyalty] = useState(false);
  const [giftCard, setGiftCard] = useState('');
  const [useWallet, setUseWallet] = useState(false);
  const wallet = useQuery({
    queryKey: ['my-wallet'],
    queryFn: async () => (await client.get<{currency: string; balance: number}[]>('/api/me/wallet')).data
  });
  // Codes are priced when the field is left, not on every key press.
  const [applied, setApplied] = useState({ coupon: '', giftCard: '' });
  const [usePackage, setUsePackage] = useState(false);
  const [useSpaPackage, setUseSpaPackage] = useState(false);
  const options = useQuery({
    queryKey: ['payment-options'],
    queryFn: async () => (await client.get<{online: boolean}>('/api/bookings/payment-options')).data
  });
  // Razorpay first when it's switched on; pay at spa when it isn't, or when the guest chose it.
  const method = options.data?.online ? (state.paymentMode ?? 'ONLINE') : 'OFFLINE';
  const byPackage = usePackage || useSpaPackage;
  const addOnIds = (state.addOns ?? []).map((a) => a.id);
  // A package session pays for the treatment only, so add-ons are paid another way.
  const packageBlocked = addOnIds.length > 0;
  const preview = useQuery({
    queryKey: ['checkout-preview', state.spaId, state.service?.id, state.selectedSlot?.id, state.guests, addOnIds, applied, loyalty, useWallet],
    enabled: !!state.spaId && !!state.service && !byPackage && !created,
    placeholderData: (previous) => previous,
    queryFn: () =>
      previewCheckout({
        spaId: state.spaId!,
        serviceId: state.service!.id,
        slotId: state.selectedSlot?.id,
        guests: state.guests,
        addOnIds: addOnIds.length ? addOnIds : undefined,
        couponCode: applied.coupon || undefined,
        redeemLoyaltyPoints: loyalty,
        giftCardCode: useWallet ? undefined : applied.giftCard || undefined,
        useWalletCredit: useWallet || undefined
      })
  });
  const points = preview.data?.pointsBalance;
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
      setProgress('Confirming booking…');
      if (!created) track('booking_submitted');
      const booking =
        created ||
        (await createBooking({
          holdToken: state.holdToken,
          spaId: state.spaId,
          serviceId: state.service.id,
          slotId: state.selectedSlot.id,
          guests: state.guests,
          addOnIds: addOnIds.length ? addOnIds : undefined,
          therapistId: state.therapistId,
          therapistType: state.therapistType,
          paymentMode: method,
          paymentMethod: 'RAZORPAY',
          specialRequest: state.notes,
          couponCode: coupon.trim() || undefined,
          redeemLoyaltyPoints: loyalty,
          giftCardCode: byPackage || useWallet ? undefined : giftCard.trim() || undefined,
          useWalletCredit: !byPackage && useWallet || undefined,
          expectedWalletCents: !byPackage && useWallet ? preview.data?.walletCents : undefined,
          payWithPackage: usePackage,
          payWithSpaPackage: useSpaPackage,
          bookingSource: 'WEB'
        }).catch(error => {
          if (isSlotConflict(error)) {
            void qc.invalidateQueries({ queryKey: ['slots', state.spaId, state.service!.id] });
            setSlot(undefined);
            onTimeConflict();
          }
          throw error;
        }));
      if (!created && coupon.trim() && !usePackage && !useSpaPackage) track('coupon_applied');
      setCreated(booking);
      if (useWallet) void qc.invalidateQueries({ queryKey: ['my-wallet'] });
      let status = booking.status;
      let paymentMode = booking.paymentMode;
      if (created && method === 'OFFLINE' && booking.status === 'PENDING_PAYMENT') {
        ({ status, paymentMode } = await payAtSpa(String(booking.bookingId)));
      } else if (booking.paymentMode === 'ONLINE' && booking.status === 'PENDING_PAYMENT') {
        setProgress('Waiting for payment…');
        const verifying = () => setProgress('Verifying payment…');
        status = (
          await (!created && booking.razorpay
            ? payWithRazorpay(booking.razorpay, verifying)
            : resumePayment(String(booking.bookingId), verifying))
        ).status;
      }
      return {
        ...booking,
        paymentMode,
        id: String(booking.bookingId),
        reference: booking.bookingReference,
        spaName: state.spaName || '',
        service: state.service,
        slot: state.slot || '',
        status
      };
    },
    onError: err => {
      track('booking_failed');
      if (axios.isAxiosError(err) && err.response?.status === 422 && useWallet) {
        setUseWallet(false);
        void qc.invalidateQueries({ queryKey: ['my-wallet'] });
        void qc.invalidateQueries({ queryKey: ['checkout-preview'] });
      }
    },
    onSuccess: (b) => {
      void qc.invalidateQueries({ queryKey: ['my-bookings'] });
      if (loyalty) void qc.invalidateQueries({ queryKey: ['loyalty'] }); // the balance changed
      if (b.status === 'CONFIRMED') {
        track('booking_completed', b.id);
        showToast('Appointment confirmed.', 'success');
      }
      onConfirm(b);
      onNext();
    }
  });
  return (
    <div className="space-y-5">
      <h2 className="text-2xl font-semibold">Complete your appointment</h2>
      <p>Final prices, discounts and eligibility are calculated by the spa before payment.</p>
      <fieldset disabled={mutation.isPending}>
        <PaymentChoice
          value={method}
          online={options.data?.online}
          byPackage={usePackage || useSpaPackage}
          onChange={setPaymentMode}
        />
      </fieldset>
      <fieldset disabled={!!created || mutation.isPending} className="space-y-4">
        <label className="block">
          Coupon code
          <input
            className="block border rounded-xl p-3 w-full"
            value={coupon}
            maxLength={64}
            onChange={(e) => setCoupon(e.target.value)}
            onBlur={() => setApplied((a) => ({ ...a, coupon: coupon.trim() }))}
          />
        </label>
        <label className="block">
          <input
            type="checkbox"
            checked={loyalty}
            disabled={points === 0 || byPackage}
            onChange={(e) => setLoyalty(e.target.checked)}
          />{' '}
          Redeem available loyalty points
          {points !== undefined && (
            <span className="block text-sm text-slate-600">
              {points === 0
                ? 'You have no points yet. You earn points when an appointment is completed.'
                : loyalty && preview.data
                  ? `Using ${preview.data.pointsRedeemed.toLocaleString('en-IN')} of your ${points.toLocaleString('en-IN')} points (${money(preview.data.pointsCents, preview.data.currency)} off).`
                  : `You have ${points.toLocaleString('en-IN')} points.`}
            </span>
          )}
        </label>
        {wallet.data?.some(w => w.currency === preview.data?.currency && w.balance > 0) && <label className="block">
          <input type="checkbox" disabled={byPackage} checked={useWallet} onChange={e => setUseWallet(e.target.checked)} />{' '}
          Use my cancellation wallet credit ({money(wallet.data.find(w => w.currency === preview.data?.currency)!.balance, preview.data!.currency)})
        </label>}
        <label className="block">
          Gift card code
          <input
            className="block border rounded-xl p-3 w-full uppercase"
            value={giftCard}
            maxLength={32}
            disabled={byPackage || useWallet}
            placeholder="NVG-XXXX-XXXX-XXXX"
            onChange={(e) => setGiftCard(e.target.value)}
            onBlur={() => setApplied((a) => ({ ...a, giftCard: giftCard.trim() }))}
          />
        </label>
        <label className="block">
          <input
            type="checkbox"
            checked={usePackage}
            disabled={packageBlocked}
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
            disabled={packageBlocked}
            onChange={(e) => {
              setUseSpaPackage(e.target.checked);
              setUsePackage(false);
            }}
          />{' '}
          Use a package I bought for this spa
        </label>
        {packageBlocked && (
          <p className="text-sm text-slate-600">Package sessions can't pay for add-ons. Remove the add-ons to use a package.</p>
        )}
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
      {!created && !byPackage && preview.data && <CheckoutSummary wallet={useWallet} preview={preview.data} payAtSpa={method === 'OFFLINE'} />}
      {created && (
        <p>
          Reference: {created.bookingReference} ·{' '}
          {created.giftCardCents ? `Gift card ${money(created.giftCardCents, created.currency)} · ` : ''}
          {created.walletCents ? `Wallet credit ${money(created.walletCents, created.currency)} · ` : ''}
          {created.giftCardCents || created.walletCents ? 'Left to pay' : 'Total'}: {money(created.totalCents, created.currency)}
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
          pressFeedback
          disabled={
            mutation.isPending || options.isPending ||
            (!created && !byPackage && useWallet && (preview.isFetching || !preview.data?.walletCents || !!preview.data.walletError)) ||
            (!created && (usePackage || useSpaPackage) && eligibility.data?.canUse !== true)
          }
          onClick={() => {
            if (submitting.current) return;
            submitting.current = true;
            mutation.mutate(undefined, { onSettled: () => { submitting.current = false; } });
          }}
        >
          {mutation.isPending ? progress || 'Confirming booking…' : created ? (method === 'OFFLINE' ? 'Confirm pay at spa' : 'Retry payment') : 'Book appointment'}
        </Button>
        <span className="sr-only" role="status">{mutation.isPending ? progress : ''}</span>
      </div>
    </div>
  );
}
