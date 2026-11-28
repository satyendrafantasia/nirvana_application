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
  const services = useQuery({
    queryKey: ['booking-services', r ? String(r.spaId) : ''],
    queryFn: () => getSpaServices(String(r!.spaId)),
    enabled: Boolean(r?.serviceAvailable)
  });

  const goTo = async (data: CheckoutResume, chosen?: AlternativeTime) => {
    const day = chosen ? chosen.date : data.date;
    const wantSlot = chosen || data.originalAvailable;
    const slots = wantSlot ? await getSlots(String(data.spaId), String(data.serviceId), day, data.guests) : [];
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
    if (r?.originalAvailable && services.data && !choosing) {
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
      {r.pendingBookingReference ? (
        <p role="status" className="card p-4">
          Booking {r.pendingBookingReference} is still waiting for payment and holds {time(r.originalStart)}.{' '}
          <Link className="font-semibold underline" to="/me/bookings">Open My bookings</Link>
        </p>
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
        <p>There are no free times in the next week.</p>
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
