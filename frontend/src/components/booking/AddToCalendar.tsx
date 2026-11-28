import { useState } from 'react';
import { downloadCalendarInvite } from '../../api/bookingApi';
import { errorMessage } from '../../api/client';

export interface CalendarBooking {
  id: string;
  reference?: string;
  serviceName: string;
  spaName: string;
  /** ISO start time. */
  start: string;
  end?: string;
  durationMinutes?: number;
  location?: string;
}

const googleTime = (date: Date) => date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');

/** A Google Calendar "add event" link holding the same details as the .ics invite. */
export function googleCalendarUrl(booking: CalendarBooking, origin = globalThis.location?.origin): string {
  const start = new Date(booking.start);
  const end = booking.end ? new Date(booking.end) : new Date(start.getTime() + (booking.durationMinutes || 60) * 60_000);
  const details = [
    booking.reference && `Booking reference: ${booking.reference}`,
    origin && `Reschedule or cancel in My bookings: ${origin}/me/bookings`
  ].filter(Boolean).join('\n');
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: `${booking.serviceName} at ${booking.spaName}`,
    dates: `${googleTime(start)}/${googleTime(end)}`,
    details,
    location: booking.location || booking.spaName
  });
  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}

/** "Add to calendar": a Google Calendar link, or the .ics file for Apple Calendar and Outlook. */
export default function AddToCalendar({ booking }: { booking: CalendarBooking }) {
  const [state, setState] = useState<'idle' | 'saving' | 'error'>('idle');
  const [problem, setProblem] = useState('');
  const download = async () => {
    setState('saving');
    try {
      await downloadCalendarInvite(booking.id, booking.reference);
      setState('idle');
    } catch (error) {
      setProblem(errorMessage(error));
      setState('error');
    }
  };
  return (
    <details className="add-to-calendar relative inline-block text-left">
      <summary className="ui-button inline-flex cursor-pointer items-center gap-2 rounded-xl border border-primary-100 bg-white px-4 py-2 text-sm font-semibold text-primary-700 hover:bg-primary-50">
        Add to calendar
      </summary>
      <ul className="mt-2 space-y-1 rounded-xl border bg-white p-2 text-sm shadow" aria-label="Calendar options">
        <li>
          <a className="block rounded-lg px-3 py-2 hover:bg-slate-100" href={googleCalendarUrl(booking)}
            target="_blank" rel="noopener noreferrer">
            Google Calendar
          </a>
        </li>
        <li>
          <button type="button" className="block w-full rounded-lg px-3 py-2 text-left hover:bg-slate-100"
            disabled={state === 'saving'} onClick={() => void download()}>
            Apple / Outlook (.ics)
          </button>
        </li>
      </ul>
      {state === 'error' && <p role="alert" className="mt-1 text-sm text-rose-700">{problem}</p>}
    </details>
  );
}
