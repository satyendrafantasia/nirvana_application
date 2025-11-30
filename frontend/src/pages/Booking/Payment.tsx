import { useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import PaymentMethodCard from '../../components/booking/PaymentMethodCard';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { createBooking } from '../../api/bookingApi';
import { initiatePayment } from '../../api/paymentApi';
import { BookingSummary } from '../../types/booking';
import { showToast } from '../../components/common/Toast';

interface PaymentProps {
  onNext: () => void;
  onBack: () => void;
  onConfirm: (booking: BookingSummary) => void;
}

const Payment = ({ onNext, onBack, onConfirm }: PaymentProps) => {
  const { state, setPaymentMode, setNotes } = useBookingFlow();
  const [notes, updateNotes] = useState(state.notes || '');

  const mutation = useMutation({
    mutationFn: async () => {
      if (!state.service || !state.slot || !state.spaId || !state.paymentMode) {
        throw new Error('Missing booking details');
      }
      const booking = await createBooking({
        spaId: state.spaId,
        serviceId: state.service.id,
        slot: state.slot,
        therapistId: state.therapistId,
        therapistType: state.therapistType,
        paymentMode: state.paymentMode,
        notes,
        bookingSource: state.bookingSource
      });

      if (state.paymentMode === 'ONLINE') {
        const paymentIntent = await initiatePayment(booking.id, 'ONLINE');
        if (paymentIntent.redirectUrl) {
          window.location.href = paymentIntent.redirectUrl;
        }
      }

      return booking;
    },
    onSuccess: (booking) => {
      onConfirm(booking);
      showToast('Booking confirmed', 'success');
      onNext();
    },
    onError: () => {
      showToast('Unable to process booking. Please try again.', 'error');
    }
  });

  const canPay = useMemo(() => Boolean(state.paymentMode), [state.paymentMode]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">Payment</h2>
          <p className="text-sm text-slate-600">Choose how you'd like to pay.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onBack} size="sm">
            Back
          </Button>
          <Button onClick={() => mutation.mutate()} size="sm" disabled={!canPay || mutation.isPending}>
            {mutation.isPending ? 'Processing...' : 'Confirm booking'}
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <PaymentMethodCard
          label="Pay online"
          description="Secure online payment to confirm instantly."
          method="ONLINE"
          selected={state.paymentMode === 'ONLINE'}
          onSelect={(method) => setPaymentMode(method as 'ONLINE' | 'PAY_AT_SPA')}
        />
        <PaymentMethodCard
          label="Pay at spa"
          description="Reserve now, pay when you arrive."
          method="PAY_AT_SPA"
          selected={state.paymentMode === 'PAY_AT_SPA'}
          onSelect={(method) => setPaymentMode(method as 'ONLINE' | 'PAY_AT_SPA')}
        />
      </div>

      <div>
        <label className="text-sm font-medium text-slate-700">Notes (optional)</label>
        <textarea
          className="mt-2 w-full rounded-xl border border-gray-200 bg-white p-3 text-sm text-slate-800 shadow-sm focus:border-primary-400 focus:outline-none focus:ring-2 focus:ring-primary-100"
          rows={3}
          placeholder="Add preferences or comments"
          value={notes}
          onChange={(e) => {
            updateNotes(e.target.value);
            setNotes(e.target.value);
          }}
        />
      </div>
    </div>
  );
};

export default Payment;
