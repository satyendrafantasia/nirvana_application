import { useQuery } from '@tanstack/react-query';
import { findMember, getSpaGroups } from '../../api/groupApi';

const DAY = 86_400_000;

/** Group bookings around today (a month either side) for the desk and the schedule. */
export function useSpaGroups(spa: string) {
  return useQuery({
    queryKey: ['owner-booking-groups', spa],
    staleTime: 30_000,
    queryFn: () => {
      const now = Date.now();
      return getSpaGroups(spa, new Date(now - 31 * DAY).toISOString(), new Date(now + 31 * DAY).toISOString());
    }
  });
}

/**
 * On an appointment: who else is in the same group. Each guest is still checked in, collected and
 * closed on their own appointment, so this is information only.
 */
export function DeskGroupBadge({ spa, booking }: { spa: string; booking: number | string }) {
  const groups = useSpaGroups(spa);
  const found = findMember(groups.data, booking);
  if (!found) return null;
  const others = found.group.members.filter((m) => m.bookingId !== found.member.bookingId);
  return (
    <p className="rounded-lg bg-primary-50 px-3 py-2 text-sm text-primary-900">
      {found.group.size === 2 ? 'Couple' : `Group of ${found.group.size}`} · guest {found.member.position}: {found.member.guestName}
      {found.member.guestPhone ? ` (${found.member.guestPhone})` : ''}
      {found.group.bookerName ? ` · booked by ${found.group.bookerName}` : ''}
      <span className="block">With: {others.map((m) => m.guestName).join(', ')}</span>
    </p>
  );
}

/** On a schedule slot: mark appointments that belong to a group. */
export function ScheduleGroupBadges({ spa, bookingIds }: { spa: string; bookingIds: number[] }) {
  const groups = useSpaGroups(spa);
  const labels = bookingIds
    .map((id) => findMember(groups.data, id))
    .filter((f): f is NonNullable<typeof f> => !!f)
    .map((f) => `${f.member.guestName} · group #${f.group.groupId} (${f.member.position}/${f.group.size})`);
  if (labels.length === 0) return null;
  return <p className="text-xs text-primary-800">{labels.join(' · ')}</p>;
}
