import { runInNewContext } from 'node:vm';
import { describe, expect, it, vi } from 'vitest';
import script from '../../../public/owner-push-sw.js?raw';

function worker() {
  const listeners = new Map<string, (event: unknown) => void>();
  const showNotification = vi.fn().mockResolvedValue(undefined);
  const openWindow = vi.fn().mockResolvedValue(undefined);
  runInNewContext(script, { self: { addEventListener: listeners.set.bind(listeners),
    registration: { showNotification }, clients: { openWindow } } });
  return { listeners, showNotification, openWindow };
}

describe('owner booking push', () => {
  it('opens an authenticated booking review without exposing guest details in the alert', () => {
    const { listeners, showNotification, openWindow } = worker();
    listeners.get('push')!({ data: { json: () => ({ data: { bookingId: '42', guestName: 'Private Name' } }) },
      waitUntil: vi.fn() });
    const [title, options] = showNotification.mock.calls[0];
    expect(title).toBe('New Nirvana booking');
    expect(JSON.stringify(options)).not.toContain('Private Name');
    listeners.get('notificationclick')!({ notification: { data: options.data, close: vi.fn() }, waitUntil: vi.fn() });
    expect(openWindow).toHaveBeenCalledWith('/owner/booking-alerts/42');
  });
  it('opens the updated booking when its time changes', () => {
    const { listeners, showNotification } = worker();
    listeners.get('push')!({ data: { json: () => ({ data: { bookingId: '42', event: 'RESCHEDULED' } }) }, waitUntil: vi.fn() });
    expect(showNotification.mock.calls[0][0]).toBe('Nirvana booking rescheduled');
  });

  it('ignores invalid booking IDs', () => {
    const { listeners, showNotification } = worker();
    listeners.get('push')!({ data: { json: () => ({ data: { bookingId: 'https://example.org' } }) } });
    expect(showNotification).not.toHaveBeenCalled();
  });
});
