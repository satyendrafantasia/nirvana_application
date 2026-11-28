import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';
import { getSpaServices } from '../../api/spaApi';
import { getTherapistDirectory } from '../../api/bookingApi';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import type { BookingSummary } from '../../types/booking';
import Button from '../common/Button';

interface Day {
  date: string;
  slots: { status: string; remainingCapacityUnits: number; therapists: { therapistId: number; available: boolean }[];
    therapistTypes: { type: string; availableCount: number }[] }[];
}

export function firstRepeatDay(days: Day[], guests: number, id?: number, label?: string) {
  return days.find(day => day.slots.some(slot => slot.status === 'AVAILABLE' && slot.remainingCapacityUnits >= guests &&
    (!id || slot.therapists.some(t => t.therapistId === id && t.available)) &&
    (!label || slot.therapistTypes.some(t => t.type === label && t.availableCount > 0))))?.date;
}

export default function BookAgain({ booking }: { booking: BookingSummary }) {
  const flow = useBookingFlow();
  const navigate = useNavigate();
  const [message, setMessage] = useState('');
  const [allowAlternative, setAllowAlternative] = useState(false);
  const search = useMutation({
    mutationFn: async (anyone: boolean) => {
      setMessage('');
      setAllowAlternative(false);
      const spa = booking.spaId!;
      const [services, policy] = await Promise.all([getSpaServices(spa),
        client.get<{mode: string; timezone: string}>(`/api/spas/${spa}/booking-policy`)]);
      const service = services.find(s => s.id === booking.service.id);
      if (!service || policy.data.mode !== 'INSTANT') {
        setMessage('This treatment is no longer available for instant booking. Check the spa’s current menu.');
        return;
      }
      const id = anyone ? undefined : booking.repeatTherapistId;
      const label = !anyone && !id && booking.originalPreference?.startsWith('type:') ? booking.originalPreference.slice(5) : undefined;
      let name: string | undefined;
      if (id || label || (!anyone && booking.originalPreference && !booking.originalPreference.startsWith('type:'))) {
        const directory = await getTherapistDirectory(spa, service.id);
        name = directory.named.find(t => t.id === id)?.name;
        if (label ? !directory.labels.includes(label) : !name) {
          setMessage('Your previous therapist choice is no longer offered for this treatment.');
          setAllowAlternative(true);
          return;
        }
      }
      const date = new Intl.DateTimeFormat('en-CA', {timeZone: policy.data.timezone || 'Asia/Kolkata'}).format(new Date());
      const guests = service.minPersons || 1;
      const { data } = await client.get<{days: Day[]}>(`/api/spas/${spa}/services/${service.id}/slots/week`,
        {params: {date, days: 7, guests}});
      const nextDay = firstRepeatDay(data.days, guests, id, label);
      flow.reset();
      flow.setSpa(spa, booking.spaName);
      flow.setService(service);
      flow.setAvailabilityDate(nextDay || date);
      flow.setTherapist(id ? String(id) : undefined, label, name || label);
      navigate('/booking');
    }
  });
  if (booking.status !== 'COMPLETED' || !booking.spaId) return null;
  return <div className="space-y-2">
    <Button disabled={search.isPending} onClick={() => search.mutate(false)}>
      {search.isPending ? 'Checking availability…' : 'Book again' + (booking.repeatTherapistId && booking.therapistName ? ' with ' + booking.therapistName : '')}
    </Button>
    {message && <p role="status">{message} <Link className="underline" to={`/spas/${booking.spaId}#services`}>View spa</Link></p>}
    {allowAlternative && <Button variant="secondary" onClick={() => search.mutate(true)}>Choose any available therapist</Button>}
    {search.isError && <p role="alert">{errorMessage(search.error)}</p>}
  </div>;
}
