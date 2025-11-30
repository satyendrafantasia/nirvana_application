import { useMemo, useState } from 'react';
import StepIndicator from '../../components/booking/StepIndicator';
import SelectService from './SelectService';
import SelectSlot from './SelectSlot';
import SelectTherapist from './SelectTherapist';
import ReviewBooking from './ReviewBooking';
import Payment from './Payment';
import BookingConfirmation from './BookingConfirmation';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { BookingSummary } from '../../types/booking';

const steps = ['Service', 'Slot', 'Therapist', 'Review', 'Payment', 'Done'];

const Booking = () => {
  const [currentStep, setCurrentStep] = useState(1);
  const { state } = useBookingFlow();
  const [summary, setSummary] = useState<BookingSummary | null>(null);

  const canProceed = useMemo(() => {
    switch (currentStep) {
      case 1:
        return Boolean(state.service && state.spaId);
      case 2:
        return Boolean(state.slot);
      case 3:
        return true;
      case 4:
        return Boolean(state.service && state.slot);
      case 5:
        return Boolean(state.paymentMode);
      default:
        return true;
    }
  }, [currentStep, state.paymentMode, state.service, state.slot, state.spaId]);

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <StepIndicator currentStep={currentStep} steps={steps} />
      <div className="mt-6 rounded-3xl bg-white p-6 shadow-card">
        {currentStep === 1 && <SelectService onNext={() => setCurrentStep(2)} />}
        {currentStep === 2 && <SelectSlot onNext={() => setCurrentStep(3)} onBack={() => setCurrentStep(1)} />}
        {currentStep === 3 && <SelectTherapist onNext={() => setCurrentStep(4)} onBack={() => setCurrentStep(2)} />}
        {currentStep === 4 && <ReviewBooking onNext={() => setCurrentStep(5)} onBack={() => setCurrentStep(3)} />}
        {currentStep === 5 && (
          <Payment
            onNext={() => setCurrentStep(6)}
            onBack={() => setCurrentStep(4)}
            onConfirm={(booking) => setSummary(booking)}
          />
        )}
        {currentStep === 6 && <BookingConfirmation summary={summary} onRestart={() => setCurrentStep(1)} />}
      </div>
      {!canProceed && (
        <p className="mt-3 text-sm text-rose-500">Complete the required details to continue.</p>
      )}
    </div>
  );
};

export default Booking;
