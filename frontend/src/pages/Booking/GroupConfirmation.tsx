import { useState } from 'react';
import Button from '../../components/common/Button';
import HealthForm from '../../components/health/HealthForm';
import { GroupView } from '../../api/groupApi';

const money = (cents: number, currency: string) =>
  new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(cents / 100);

/** The group shown together: one row per guest, each with its own booking reference. */
export function GroupMembers({ group }: { group: GroupView }) {
  return (
    <ul className="divide-y rounded-2xl border bg-white text-left" aria-label={`Group of ${group.size}`}>
      {group.members.map((m) => (
        <li key={m.bookingId} className="p-3">
          <p className="font-semibold">
            {m.booker ? `${m.guestName} (you)` : m.guestName} · {m.serviceName}
          </p>
          <p className="text-sm text-slate-600">
            {m.bookingReference} · {m.status.replaceAll('_', ' ').toLowerCase()}
            {m.therapistName ? ` · ${m.therapistName}` : ''} · {money(m.payableCents, group.currency)}
          </p>
        </li>
      ))}
    </ul>
  );
}

export default function GroupConfirmation({ group }: { group: GroupView }) {
  const [healthOpen, setHealthOpen] = useState(false);
  const confirmed = group.members.every((m) => m.status === 'CONFIRMED');
  const own = group.members.find((m) => m.booker) || group.members[0];
  return (
    <div className="space-y-6 text-center">
      <h2 className="text-3xl font-semibold text-slate-900">{confirmed ? 'Your group is booked' : 'Payment pending'}</h2>
      <p className="text-slate-600">
        {group.spaName} · {new Date(group.startTs).toLocaleString(undefined, { dateStyle: 'long', timeStyle: 'short' })} ·{' '}
        {group.size} guests
      </p>
      <div className="mx-auto max-w-lg space-y-3">
        <GroupMembers group={group} />
        <p>
          {group.paymentMode === 'OFFLINE' ? 'Pay at the spa' : 'Paid online'} · Group total {money(group.totalCents, group.currency)}
        </p>
      </div>
      {own && (
        <div className="mx-auto max-w-lg rounded-2xl border p-5 text-left">
          {healthOpen ? (
            <HealthForm bookingId={String(own.bookingId)} onDone={() => setHealthOpen(false)} />
          ) : (
            <div className="space-y-2">
              <p className="font-semibold">Health and consent forms</p>
              <p className="text-sm">
                Each guest has their own form on their own booking. Fill in yours now; your guests&rsquo; forms are in My
                bookings, where you can complete them together, or they can fill them in at the spa&rsquo;s front desk.
              </p>
              <Button variant="secondary" onClick={() => setHealthOpen(true)}>
                Fill in mine now
              </Button>
            </div>
          )}
        </div>
      )}
      <div className="flex flex-wrap justify-center gap-3">
        <Button variant="secondary" as="a" href="/me/bookings">
          View my bookings
        </Button>
        <Button as="a" href="/" variant="ghost">
          Return home
        </Button>
      </div>
    </div>
  );
}
