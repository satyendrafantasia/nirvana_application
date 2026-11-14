import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import { track } from '../../telemetry';
import { useEffect, useMemo, useRef, useState } from 'react';
import StepIndicator from '../../components/booking/StepIndicator';
import SelectService from './SelectService';
import SelectSlot from './SelectSlot';
import ReviewBooking from './ReviewBooking';
import Payment from './Payment';
import BookingConfirmation from './BookingConfirmation';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { BookingSummary } from '../../types/booking';

const steps = ['Service', 'Time & therapist', 'Review', 'Payment', 'Done'];

const Booking = () => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { state } = useBookingFlow();
  const [currentStep, setCurrentStep] = useState(state.service && state.spaId ? 2 : 1);
  const [timeConflict,setTimeConflict]=useState(false);
  const [summary, setSummary] = useState<BookingSummary | null>(null);

  const entered = useRef(false);
  useEffect(() => {
    if (!entered.current && pathname !== '/booking/receipt') track('begin_booking');
    entered.current = true;
  }, [pathname]);
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

  if (pathname === '/booking/receipt' && !summary) return <Navigate replace to="/me/bookings" />;
  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <StepIndicator currentStep={currentStep>2?currentStep-1:currentStep} steps={steps} />
      <div className="mt-6 rounded-3xl bg-white p-6 shadow-card">
        {currentStep === 1 && <SelectService onNext={() => setCurrentStep(2)} />}
        {currentStep === 2 && (
          <>{timeConflict && <p role="alert">This time is no longer available. Select another time.</p>}<SelectSlot onNext={() => {setTimeConflict(false);setCurrentStep(4);}} onBack={() => setCurrentStep(1)} /></>
        )}
        {currentStep === 4 && (
          <ReviewBooking onNext={() => setCurrentStep(5)} onBack={() => setCurrentStep(2)} />
        )}
        {currentStep === 5 && (
          <Payment onTimeConflict={()=>{setTimeConflict(true);setCurrentStep(2);}}
            onNext={() => setCurrentStep(6)}
            onBack={() => setCurrentStep(4)}
            onConfirm={(booking) => { setSummary(booking); navigate("/booking/receipt", { replace: true }); }}
          />
        )}
        {currentStep === 6 && (
          <BookingConfirmation summary={summary} onRestart={() => { setSummary(null); setCurrentStep(1); navigate("/booking", {replace:true}); }} />
        )}
      </div>
      {!canProceed && (
        <p className="mt-3 text-sm text-rose-500">Complete the required details to continue.</p>
      )}
    </div>
  );
};

export default Booking;
