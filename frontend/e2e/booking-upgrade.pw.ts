import { expect, test } from '@playwright/test';

test('recovered upgrade uses the extended slot search until the guest removes it', async ({ page }) => {
  const customer = { userId: 4, username: 'guest@example.test', roles: ['ROLE_CUSTOMER'] };
  const extra = { id: 9, name: 'Extra massage time', priceCents: 50000, currency: 'INR', extraMinutes: 30 };
  const date = '2030-10-20';
  const start = date + 'T11:00:00+05:30';
  const searches: Array<string | null> = [];
  const slot = {
    slotId: 501, startTs: start, endTs: date + 'T12:30:00+05:30', remainingCapacityUnits: 1,
    status: 'AVAILABLE', therapistSelectionEnabled: false, therapistTypeSelectionEnabled: false
  };

  await page.addInitScript(() => localStorage.setItem('nirvana_session_present', 'browser-fixture'));
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    if (path.endsWith('/auth/browser-session')) return route.fulfill({ json: { ...customer, accessToken: 'browser-fixture' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: customer });
    if (path.endsWith('/checkout-recovery/test-reminder')) return route.fulfill({ json: {
      spaId: 7, spaName: 'Lotus Wellness', serviceId: 6, serviceName: 'Massage', serviceAvailable: true, guests: 1,
      date, originalStart: start, originalAvailable: true, originalSlotId: 501, alternatives: [], addOns: [extra]
    } });
    if (path.endsWith('/services')) return route.fulfill({ json: [{
      id: 6, name: 'Massage', durationMinutes: 60, basePriceCents: 150000, currency: 'INR', isActive: true,
      isVisibleOnMarketplace: true
    }] });
    if (path.endsWith('/add-ons')) return route.fulfill({ json: [extra] });
    if (path.endsWith('/therapists')) return route.fulfill({ json: { named: [], labels: [] } });
    if (path.endsWith('/slots')) {
      searches.push(url.searchParams.get('addOnIds'));
      return route.fulfill({ json: [slot] });
    }
    return route.fulfill({ json: [] });
  });

  await page.goto('/booking/resume/test-reminder');
  await expect(page).toHaveURL(/\/booking$/);
  await expect(page.getByText(/90-minute appointment/)).toBeVisible();
  const upgrade = page.getByRole('checkbox', { name: /Extra massage time/ });
  await expect(upgrade).toBeChecked();
  expect(searches).toContain('9');
  await upgrade.uncheck();
  await expect.poll(() => searches.at(-1)).toBeNull();
  await expect(page.getByText(/90-minute appointment/)).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Continue', exact: true })).toBeDisabled();
});
