import Button from '../../components/common/Button';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { therapistLabel } from './therapistLabel';
import { addOnTotals, extraTime } from '../../api/addOnApi';
import { money } from '../../api/rewardsApi';

interface ReviewBookingProps {
  onNext: () => void;
  onBack: () => void;
}

export default function ReviewBooking({ onNext, onBack }: ReviewBookingProps) {
  const { state } = useBookingFlow();
  const { service, guests, selectedSlot } = state;
  const namedTherapist = selectedSlot?.therapists.find(t => t.id === state.therapistId);
  const therapist = state.heldTherapistName || namedTherapist?.name || state.preferredTherapistName ||
    (state.therapistType ? therapistLabel(state.therapistType) :
      state.therapistId ? 'Selected therapist' : 'Any available therapist');
  const addOns = state.addOns ?? [];
  const extras = addOnTotals(addOns, guests);
  const appointment = state.slot ? new Date(state.slot) : null;
  const baseCents = service ? Math.round(service.price * 100) : 0;

  return (
    <section className="appointment-review">
      <header className="review-heading">
        <h2>Review your booking</h2>
        <p>Check your appointment details before choosing how to pay.</p>
      </header>

      <div className="review-layout">
        <div className="review-treatment">
          <img src="/hero-spa-800.webp" alt="" width="800" height="600" />
          <div className="review-treatment-copy">
            {state.spaName && <p>{state.spaName}</p>}
            {service && <h3>{service.name}</h3>}
            {service && <span>{service.durationMinutes} min treatment</span>}
          </div>
        </div>

        <div className="review-itinerary">
          <dl className="review-facts">
            {appointment && <>
              <div><dt>Date</dt><dd>{appointment.toLocaleDateString(undefined, {weekday: 'short', month: 'short', day: 'numeric', year: 'numeric'})}</dd></div>
              <div><dt>Time</dt><dd><time dateTime={state.slot}>{appointment.toLocaleTimeString(undefined, {hour: 'numeric', minute: '2-digit', timeZoneName: 'short'})}</time></dd></div>
            </>}
            {selectedSlot && <div><dt>Therapist</dt><dd>{therapist}</dd></div>}
            <div><dt>Guests</dt><dd>{guests} {guests === 1 ? 'guest' : 'guests'}</dd></div>
          </dl>
          {service && <div className="review-pricing">
            {addOns.length > 0 && <>
              <h3>Add-ons</h3>
              <ul>{addOns.map(a => <li key={a.id}>
                <span>{a.name} {extraTime(a.extraMinutes)}</span>
                <span>{money(a.priceCents * guests, a.currency)}{guests > 1 && <small> ({money(a.priceCents, a.currency)} × {guests})</small>}</span>
              </li>)}</ul>
              {extras.minutes > 0 && <p>Appointment length: {service.durationMinutes + extras.minutes} mins</p>}
            </>}
            <div className="review-subtotal">
              <div><h3>Service subtotal</h3><p>{money(baseCents, service.currency)} per guest</p></div>
              <strong>{money(baseCents * guests + extras.cents, service.currency)}</strong>
            </div>
          </div>}
          <div className="review-actions">
            <Button variant="secondary" onClick={onBack}>Back</Button>
            <Button onClick={onNext} disabled={!state.slot || !service}>Continue <span aria-hidden="true">→</span></Button>
          </div>
        </div>
      </div>
    </section>
  );
}
