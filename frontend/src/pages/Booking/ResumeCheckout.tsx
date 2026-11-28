import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { getSlots } from '../../api/bookingApi';
import { getSpaServices } from '../../api/spaApi';
import {
  AlternativeTime,
  CheckoutResume,
  applyPrefill,
  getCheckoutResume,
  resumePrefill
} from '../../api/checkoutRecoveryApi';
import GroupBooking from './GroupBooking';
import { getGroup } from '../../api/groupApi';
import Button from '../../components/common/Button';
import Spinner from '../../components/common/Spinner';
import { useBookingFlow } from '../../hooks/useBookingFlow';

const time = (iso: string) =>
  new Date(iso).toLocaleString([], { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });

/**
 * Opened from a checkout reminder: puts the same spa, service, therapist and day back into the
 * booking flow. If the original time is still free it is selected; otherwise the nearest free
 * times are offered.
 */
export default function ResumeCheckout() {
  const { token = '' } = useParams();
  const navigate = useNavigate();
  const flow = useBookingFlow();
  const [choosing, setChoosing] = useState<string | null>(null);
  const [problem, setProblem] = useState<string | null>(null);

  const resume = useQuery({
    queryKey: ['checkout-resume', token],
    queryFn: () => getCheckoutResume(token),
    staleTime: Infinity,
    retry: false
  });
  const r = resume.data;
  const group = useQuery({
    queryKey: ['checkout-resume-group', r?.groupId],
    enabled: r?.groupId != null && !r.pendingBookingReference && !r.checkoutResolved,
    queryFn: () => getGroup(r!.groupId!)
  });
  const services = useQuery({
    queryKey: ['booking-services', r ? String(r.spaId) : ''],
    queryFn: () => getSpaServices(String(r!.spaId)),
    enabled: Boolean(r?.serviceAvailable && !r.groupId && !r.checkoutResolved)
  });

  const goTo = async (data: CheckoutResume, chosen?: AlternativeTime) => {
    const day = chosen ? chosen.date : data.date;
    const wantSlot = chosen || data.originalAvailable;
    const slots = wantSlot ? await getSlots(String(data.spaId), String(data.serviceId), day, data.guests, data.addOns?.map((a) => a.id)) : [];
    const prefill = resumePrefill(data, services.data || [], slots, chosen);
    if (!prefill) {
      setProblem('This treatment can no longer be booked online.');
      return;
    }
    applyPrefill(prefill, flow);
    navigate('/booking', { replace: true });
  };

  // The original time is still free: go straight back to the booking flow with it selected.
  useEffect(() => {
    if (r?.originalAvailable && !r.checkoutResolved && !r.addOnsChanged && !r.groupId && services.data && !choosing) {
      setChoosing('original');
      goTo(r).catch((e) => { setChoosing(null); setProblem(errorMessage(e)); });
    }
  }, [r, services.data]);

  if (resume.isError) {
    return (
      <section className="mx-auto max-w-xl px-4 py-12">
        <h1 className="text-2xl font-semibold">This link has expired</h1>
        <p className="my-4 text-slate-600">Reminder links work for a couple of days. You can still book from the spa's page.</p>
        <Link className="font-semibold underline" to="/spas">Browse spas</Link>
      </section>
    );
  }
  if (r?.checkoutResolved) return (
    <section className="card mx-auto max-w-xl space-y-4 p-6">
      <h1>This checkout is closed</h1><Link to="/me/bookings" className="underline">View booking status in My bookings</Link>
    </section>
  );
  if (r?.groupId != null) {
    if (r.pendingBookingReference) return (
      <section className="card mx-auto max-w-xl space-y-4 p-6"><h1>Your group booking is awaiting payment</h1>
        <Link to="/me/bookings" className="underline">Continue payment in My bookings</Link></section>
    );
    if (group.isError) return <p role="alert">Could not load your group. <Link to="/me/bookings">Open My bookings</Link></p>;
    if (!group.data) return <Spinner />;
    return <section className="mx-auto max-w-4xl space-y-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">Resume your group booking</h1>
      <p>Review each guest’s treatment and therapist preference, then choose a new time for everyone.</p>
      <GroupBooking spaId={String(r.spaId)} spaName={r.spaName} size={group.data.size}
        initialGuests={group.data.members.map((m) => ({ name: m.guestName, serviceId: String(m.serviceId) }))}
        initialDate={r.date} onBack={() => navigate('/me/bookings')} />
    </section>;
  }
  if (!r || (r.serviceAvailable && !services.data && !services.isError) || choosing === 'original') {
    return <div className="flex h-96 items-center justify-center"><Spinner /></div>;
  }
  if (!r.serviceAvailable || services.isError || (services.data && !services.data.some((s) => s.id === String(r.serviceId)))) {
    return (
      <section className="mx-auto max-w-xl px-4 py-12">
        <h1 className="text-2xl font-semibold">{r.serviceName} isn't available online right now</h1>
        <p className="my-4 text-slate-600">Have a look at what else {r.spaName} offers.</p>
        <Link className="font-semibold underline" to={`/spas/${r.spaId}`}>Open {r.spaName}</Link>
      </section>
    );
  }
  return (
    <section className="mx-auto max-w-xl space-y-4 px-4 py-12">
      <h1 className="text-2xl font-semibold">Pick up where you left off</h1>
      <p className="text-slate-700">
        {r.serviceName} at {r.spaName}
        {r.therapistName ? ` with ${r.therapistName}` : ''}.
      </p>
      {r.addOnsChanged && <p role="status">The spa updated its add-ons. Review the available extras and current prices before confirming.</p>}
      {r.pendingBookingReference ? (
        <p role="status" className="card p-4">
          Booking {r.pendingBookingReference} is still waiting for payment and holds {time(r.originalStart)}.{' '}
          <Link className="font-semibold underline" to="/me/bookings">Open My bookings</Link>
        </p>
      ) : r.originalAvailable ? (
        <Button disabled={choosing !== null} onClick={() => {
          setChoosing('continue');
          goTo(r).catch((e) => { setChoosing(null); setProblem(errorMessage(e)); });
        }}>Continue with this time</Button>
      ) : (
        <p role="status">The time you chose, {time(r.originalStart)}, has been taken.</p>
      )}
      {r.alternatives.length > 0 ? (
        <>
          <h2 className="text-lg font-semibold">Nearest free times</h2>
          <ul className="grid gap-2 sm:grid-cols-2">
            {r.alternatives.map((t) => (
              <li key={t.slotId}>
                <Button
                  variant="secondary"
                  disabled={choosing !== null}
                  onClick={() => {
                    setChoosing(String(t.slotId));
                    goTo(r, t).catch((e) => { setChoosing(null); setProblem(errorMessage(e)); });
                  }}
                >
                  {time(t.startTs)}
                </Button>
              </li>
            ))}
          </ul>
        </>
      ) : (
        !r.originalAvailable && <p>There are no free times in the next week.</p>
      )}
      {problem && <p role="alert" className="text-rose-700">{problem}</p>}
      <Button
        variant="ghost"
        disabled={choosing !== null}
        onClick={() => {
          setChoosing('any');
          goTo({ ...r, originalAvailable: false }).catch((e) => { setChoosing(null); setProblem(errorMessage(e)); });
        }}
      >
        See all times
      </Button>
    </section>
  );
}
