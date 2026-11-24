// Firebase sends a data message; the private booking details are fetched after sign-in.
self.addEventListener('push', event => {
  let id;
  try { id = event.data?.json()?.data?.bookingId; } catch { return; }
  if (typeof id !== 'string' || !/^[1-9][0-9]{0,18}$/.test(id)) return;
  event.waitUntil(self.registration.showNotification('New Nirvana booking', {
    body: 'Open the booking to confirm or decline.',
    tag: 'nirvana-booking-' + id,
    icon: '/apple-touch-icon.png',
    data: { id }
  }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const id = event.notification.data?.id;
  if (/^[1-9][0-9]{0,18}$/.test(id)) {
    event.waitUntil(self.clients.openWindow('/owner/booking-alerts/' + id));
  }
});
