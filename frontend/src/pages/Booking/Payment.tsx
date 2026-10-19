import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { createBooking, CreatedBooking } from '../../api/bookingApi';
import { resumePayment, payWithRazorpay } from '../../api/paymentApi';
import { errorMessage } from '../../api/client';
import { BookingSummary } from '../../types/booking';
export default function Payment({
  onNext,
  onBack,
  onConfirm
}: {
  onNext: () => void;
  onBack: () => void;
  onConfirm: (b: BookingSummary) => void;
}) {
  const { state, setPaymentMode, setNotes } = useBookingFlow();
  const [created, setCreated] = useState<CreatedBooking>();
  const [coupon, setCoupon] = useState('');
  const [loyalty, setLoyalty] = useState(false);
  const [usePackage, setUsePackage] = useState(false);
  const mutation = useMutation({
    mutationFn: async () => {
      if (!state.spaId || !state.service || !state.selectedSlot || !state.paymentMode)
        throw new Error('Select a service and available appointment time.');
      const booking =
        created ||
        (await createBooking({
          spaId: state.spaId,
          serviceId: state.service.id,
          slotId: state.selectedSlot.id,
          guests: state.guests,
          therapistId: state.therapistId,
          therapistType: state.therapistType,
          paymentMode: state.paymentMode,
          paymentMethod: 'RAZORPAY',
          specialRequest: state.notes,
          couponCode: coupon || undefined,
          redeemLoyaltyPoints: loyalty,
          payWithPackage: usePackage,
          bookingSource: 'WEB'
        }));
      setCreated(booking);
      let status=booking.status;
      if (booking.paymentMode === 'ONLINE' && booking.status === 'PENDING_PAYMENT')
        status=(await (booking.razorpay ? payWithRazorpay(booking.razorpay) : resumePayment(String(booking.bookingId)))).status;
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
    onSuccess: (b) => {
      onConfirm(b);
      onNext();
    }
  });
  return (
    <div className="space-y-5">
      <h2 className="text-2xl font-semibold">Complete your appointment</h2>
      <p>Final prices, discounts and eligibility are calculated by the spa before payment.</p>
      <fieldset disabled={!!created || mutation.isPending} className="space-y-4">
        <label className="block">
          Payment method
          <select
            className="block border rounded-xl p-3 w-full"
            value={state.paymentMode}
            onChange={(e) => setPaymentMode(e.target.value as 'ONLINE' | 'OFFLINE')}
          >
            <option value="ONLINE">Pay securely with Razorpay</option>
            <option value="OFFLINE">Pay at spa</option>
          </select>
        </label>
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
          <input type="checkbox" checked={loyalty} onChange={(e) => setLoyalty(e.target.checked)} />{' '}
          Redeem available loyalty points
        </label>
        <label className="block">
          <input
            type="checkbox"
            checked={usePackage}
            onChange={(e) => setUsePackage(e.target.checked)}
          />{' '}
          Use an eligible package I already own
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
      <div className="flex gap-3">
        <Button variant="secondary" disabled={!!created || mutation.isPending} onClick={onBack}>
          Back
        </Button>
        <Button disabled={mutation.isPending} onClick={() => mutation.mutate()}>
          {mutation.isPending ? 'Processing…' : created ? 'Retry payment' : 'Book appointment'}
        </Button>
      </div>
    </div>
  );
}
