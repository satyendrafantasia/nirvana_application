import { test, expect } from '@playwright/test';

const service = { id: 6, name: 'Swedish Massage', durationMinutes: 60, basePriceCents: 200000,
  currency: 'INR', isActive: true, isVisibleOnMarketplace: true };
const spa = { id: 7, name: 'Lotus Spa', city: 'Pune', timezone: 'Asia/Kolkata' };
const summary = { bookingId: 8, bookingReference: 'PREVIOUS', spaId: 7, spaName: spa.name, serviceId: 6,
  serviceName: service.name, serviceDurationMinutes: 60, servicePriceCents: 150000, startTs: '2025-04-23T11:00:00+05:30',
  status: 'COMPLETED', paymentMode: 'OFFLINE', therapistName: 'Maya', repeatTherapistId: 12 };
const slot = (available: boolean) => ({ slotId: 45, startTs: '2030-05-02T11:00:00+05:30', endTs: '2030-05-02T12:00:00+05:30',
  status: 'AVAILABLE', remainingCapacityUnits: 1, therapists: [{ therapistId: 12, name: 'Maya', available }], therapistTypes: [] });

test('signed-out guest opens a shared spa link and its WhatsApp enquiry', async ({ page }) => {
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/storefront')) return route.fulfill({json: {spaId: 7, name: spa.name, whatsappNumber: '+12025550123'}});
    if (path.endsWith('/spas/7')) return route.fulfill({json: spa});
    if (path.endsWith('/services')) return route.fulfill({json: [service]});
    if (path.endsWith('/reviews')) return route.fulfill({json: {page: {content: []}}});
    return route.fulfill({json: []});
  });
  const response = await page.goto('/s/7/lotus-spa');
  expect(response?.status()).toBe(200);
  await expect(page.getByRole('heading', {name: spa.name, exact: true})).toBeVisible();
  await expect(page.getByRole('heading', {name: service.name})).toBeVisible();
  const whatsapp = page.getByRole('link', {name: 'Ask on WhatsApp'});
  await expect(whatsapp).toHaveAttribute('href', /^https:\/\/wa\.me\/12025550123\?text=/);
  await expect(page.getByRole('button', {name: 'Copy storefront link'})).toBeVisible();
});

for (const active of [true, false]) {
  test('book again ' + (active ? 'selects the first day the previous therapist is free' : 'requires a choice when the therapist has left'), async ({ page }) => {
    let holds = 0;
    const user = {userId: 4, username: 'guest@example.test', roles: ['ROLE_CUSTOMER']};
    await page.addInitScript(() => localStorage.setItem('nirvana_session_present', 'browser-fixture'));
    await page.route('**/api/**', route => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('/auth/browser-session')) return route.fulfill({json: {...user, accessToken: 'fixture'}});
      if (path.endsWith('/auth/me')) return route.fulfill({json: user});
      if (path.endsWith('/me/bookings')) return route.fulfill({json: {page: {content: [summary], totalPages: 1}}});
      if (path.endsWith('/services')) return route.fulfill({json: [service]});
      if (path.endsWith('/booking-policy')) return route.fulfill({json: {mode: 'INSTANT', timezone: 'Asia/Kolkata'}});
      if (path.endsWith('/therapists')) return route.fulfill({json: {named: active ? [{id: 12, name: 'Maya'}] : [], labels: []}});
      if (path.endsWith('/slots/week')) return route.fulfill({json: {days: [
        {date: '2030-05-01', slots: [slot(false)]}, {date: '2030-05-02', slots: [slot(true)]}
      ]}});
      if (path.endsWith('/slots')) return route.fulfill({json: [slot(true)]});
      if (path.endsWith('/hold') || path === '/api/bookings') holds++;
      return route.fulfill({json: []});
    });
    await page.goto('/me/bookings');
    await page.getByRole('button', {name: 'Book again with Maya'}).click();
    if (active) {
      await expect(page).toHaveURL(/\/booking$/);
      await expect(page.getByLabel('Therapist', {exact:true})).toHaveValue('12');
      await expect(page.getByLabel('Date', {exact:true})).toHaveValue('2030-05-02');
    } else {
      await expect(page.getByText('Your previous therapist choice is no longer offered', {exact:false})).toBeVisible();
      await page.getByRole('button', {name: 'Choose any available therapist'}).click();
      await expect(page).toHaveURL(/\/booking$/);
      await expect(page.getByLabel('Therapist', {exact:true})).toHaveValue('');
    }
    expect(holds).toBe(0);
  });
}
