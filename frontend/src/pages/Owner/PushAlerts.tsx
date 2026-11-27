import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import client, { errorMessage } from '../../api/client';

interface Config {
  eligible: boolean;
  enabled: boolean;
  vapidKey: string;
  firebase: { projectId: string; messagingSenderId: string; apiKey: string; appId: string };
}

export default function PushAlerts({ spa, user }: { spa: string; user: string }) {
  const path = '/api/owner/push/spas/' + spa;
  const config = useQuery({ queryKey: ['owner-push', user, spa],
    queryFn: async () => (await client.get<Config>(path)).data });
  const storedKey = 'owner-push-user';
  const [storedToken, setStoredToken] = useState(() => localStorage.getItem(storedKey) === user
    ? localStorage.getItem('owner-push-token') : null);
  const device = useQuery({ queryKey: ['owner-device-status', spa, user, Boolean(storedToken)],
    enabled: !!storedToken && !!config.data?.eligible,
    queryFn: async () => (await client.post<{ subscribed: boolean }>(path + '/device-status', { token: storedToken })).data });
  const enabled = !!storedToken && device.data?.subscribed && 'Notification' in window && Notification.permission === 'granted';
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  if (config.isError || device.isError) return <p role="alert">Booking alert settings could not be loaded.
    {' '}<button className="underline" onClick={() => { void config.refetch(); if (storedToken) void device.refetch(); }}>Retry</button></p>;
  if (!config.data?.eligible) return null;
  const settings = config.data;
  async function toggle() {
    setBusy(true);
    setMessage('');
    try {
      if (enabled) {
        await client.post('/api/owner/push/unsubscribe', { token: localStorage.getItem('owner-push-token') });
        localStorage.removeItem('owner-push-token');
        localStorage.removeItem(storedKey);
        setStoredToken(null);
        return;
      }
      if (!('Notification' in window) || !('serviceWorker' in navigator) || !window.isSecureContext)
        throw new Error('Use a browser that supports notifications over HTTPS.');
      if (await Notification.requestPermission() !== 'granted')
        throw new Error('Allow notifications in your browser settings to receive booking alerts.');
      const messaging = await import('firebase/messaging');
      if (!await messaging.isSupported()) throw new Error('Push notifications are unavailable in this browser.');
      const { getApps, initializeApp } = await import('firebase/app');
      const app = getApps().find(a => a.name === 'owner-push') ?? initializeApp(settings.firebase, 'owner-push');
      const registration = await navigator.serviceWorker.register('/owner-push-sw.js', { scope: '/owner-push/' });
      const worker = registration.installing || registration.waiting;
      if (!registration.active && worker && worker.state !== 'activated') {
        await new Promise<void>((resolve, reject) => {
          const changed = () => {
            if (worker.state !== 'activated' && worker.state !== 'redundant') return;
            worker.removeEventListener('statechange', changed);
            if (worker.state === 'activated') resolve();
            else reject(new Error('Could not start notifications. Please retry.'));
          };
          worker.addEventListener('statechange', changed);
          changed();
        });
      }
      const token = await messaging.getToken(messaging.getMessaging(app), {
        vapidKey: settings.vapidKey, serviceWorkerRegistration: registration
      });
      await client.put(path, { token });
      localStorage.setItem('owner-push-token', token);
      localStorage.setItem(storedKey, user);
      setStoredToken(token);
      if (storedToken) void device.refetch();
    } catch (e) { setMessage(errorMessage(e)); }
    finally { setBusy(false); }
  }
  return <section className="card p-4 space-y-2">
    <button type="button" className="btn-secondary" disabled={busy || device.isFetching || (!enabled && !settings.enabled)}
      onClick={toggle}>{enabled ? 'Disable booking alerts' : 'Enable booking alerts'}</button>
    <p role="status">{message || (!settings.enabled ? 'Push notifications are awaiting setup.'
      : enabled ? 'This device is registered for alerts from your claimed spas.' : 'Get new bookings on this device.')}</p>
  </section>;
}
