import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import client from '../../api/client';
import { track } from '../../telemetry';
import { useEffect, useMemo, useRef, useState } from 'react';
import StepIndicator from '../../components/booking/StepIndicator';
import SelectService from './SelectService';
import SelectSlot from './SelectSlot';
import ReviewBooking from './ReviewBooking';
import Payment from './Payment';
import BookingConfirmation from './BookingConfirmation';
import GuestCountPicker from './GuestCountPicker';
import GroupBooking from './GroupBooking';
import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { BookingSummary } from '../../types/booking';
import './appointment.css';

const steps = ['Service', 'Time & therapist', 'Review', 'Payment', 'Done'];

const Booking = () => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { state } = useBookingFlow();
  const [currentStep, setCurrentStep] = useState(state.service && state.spaId ? 2 : 1);
  const [timeConflict,setTimeConflict]=useState(false);
  const [summary, setSummary] = useState<BookingSummary | null>(null);
  // Couples and groups (2 to 6 guests) book together in their own flow; one guest uses the steps below.
  const [party, setParty] = useState(1);
  const [grouping, setGrouping] = useState(false);
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const heading = panel.current?.querySelector('h2');
    if (heading) {
      heading.tabIndex = -1;
      heading.focus();
    }
  }, [currentStep]);

  const pending=typeof localStorage==='undefined'?null:localStorage.getItem('nirvana.pendingCheckout');
  const recovered=useQuery({queryKey:['checkout-recovery',pending],enabled:!!pending,queryFn:async()=>{
    const attempt=JSON.parse(pending!) as {key:string;spa:string};
    return (await client.get<{reference?:string;status:string}>('/api/me/checkout-attempts/'+encodeURIComponent(attempt.key),{params:{spa:attempt.spa}})).data;
  }});
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
  if (grouping && party > 1 && state.spaId)
    return (
      <div className="appointment-shell">
        <div className="booking-step appointment-panel">
          <GroupBooking spaId={state.spaId} spaName={state.spaName} size={party} firstService={state.service?.id}
            onBack={() => setGrouping(false)} />
        </div>
      </div>
    );
  return (
    <div className="appointment-shell">
      {recovered.data?.reference && <p className="card p-4" role="status">Your previous checkout created booking {recovered.data.reference} · {recovered.data.status}. <a href="/me/bookings">Open My bookings</a></p>}
      <StepIndicator currentStep={currentStep>2?currentStep-1:currentStep} steps={steps} />
      <div key={currentStep} ref={panel} className="booking-step appointment-panel">
        {currentStep === 1 && (
          <div className="space-y-6">
            <GuestCountPicker value={party} onChange={setParty} />
            {party > 1 && state.spaId && (
              <Button onClick={() => setGrouping(true)}>Choose treatments for {party} guests</Button>
            )}
            <SelectService onNext={() => (party > 1 ? setGrouping(true) : setCurrentStep(2))} />
          </div>
        )}
        {currentStep === 2 && (
          <><div className="mb-6"><GuestCountPicker value={party} onChange={(n) => { setParty(n); if (n > 1) setGrouping(true); }} /></div>
          {timeConflict && !state.selectedSlot && <p role="alert">This time is no longer available. Select another time.</p>}<SelectSlot onNext={() => {setTimeConflict(false);setCurrentStep(4);}} onBack={() => setCurrentStep(1)} /></>
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
