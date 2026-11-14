import { useState } from 'react';
type EventName =
  | 'view_service'
  | 'select_service'
  | 'begin_booking'
  | 'select_appointment_slot'
  | 'booking_submitted'
  | 'booking_completed'
  | 'booking_failed'
  | 'coupon_applied';
const endpoint =
  import.meta.env.MODE === 'production' &&
  import.meta.env.PROD &&
  import.meta.env.VITE_ANALYTICS_PATH;
const enabled =
  typeof endpoint === 'string' &&
  /^\/[a-zA-Z0-9/_-]+$/.test(endpoint) &&
  !endpoint.startsWith('//');
const preference = 'nirvana.analytics.consent';
const completed = new Set<string>();
function hasConsent() {
  try {
    return localStorage.getItem(preference) === 'yes';
  } catch {
    return false;
  }
}
export function track(event: EventName, bookingId?: string) {
  if (!enabled || !hasConsent()) return;
  if (event === 'booking_completed' && bookingId) {
    if (completed.has(bookingId)) return;
    completed.add(bookingId);
  }
  void fetch(endpoint, {
    method: 'POST',
    credentials: 'omit',
    referrerPolicy: 'no-referrer',
    redirect: 'error',
    keepalive: true,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ event })
  }).catch(() => {});
}
export function TrackingChoice() {
  const [allowed, setAllowed] = useState(hasConsent);
  if (!enabled) return <p>Optional analytics are disabled.</p>;
  return (
    <label>
      <input
        type="checkbox"
        checked={allowed}
        onChange={(e) => {
          try {
            localStorage.setItem(preference, e.target.checked ? 'yes' : 'no');
            setAllowed(e.target.checked);
          } catch {
            setAllowed(false);
          }
        }}
      />{' '}
      Allow optional booking analytics on this browser
    </label>
  );
}
