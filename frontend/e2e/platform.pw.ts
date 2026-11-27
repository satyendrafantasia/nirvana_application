import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/spas/7'))
      return route.fulfill({ json: { id: 7, name: 'Lotus Wellness', city: 'Pune' } });
    if (path.endsWith('/services'))
      return route.fulfill({
        json: [
          {
            id: 6,
            name: 'Swedish Massage',
            durationMinutes: 60,
            basePriceCents: 199900,
            currency: 'INR',
            isActive: true,
            isVisibleOnMarketplace: true
          }
        ]
      });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { page: { content: [] } } });
    if (path.endsWith('/search'))
      return route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } });
    return route.fulfill({ json: [] });
  });
});
for (const width of [320, 375, 390, 430, 768, 1024, 1440, 1920]) {
  test('public navigation and menu fit at ' + width + 'px', async ({ page }) => {
    await page.setViewportSize({ width, height: 850 });
    await page.goto('/spas/7');
    await expect(page.getByRole('heading', { name: 'Swedish Massage' })).toBeVisible();
    await expect(page.getByText('₹1,999.00')).toBeVisible();
    const overflow = () =>
      page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth
      );
    expect(await overflow()).toBeLessThanOrEqual(1);
    if (width < 1024) {
      const toggle = page.locator('button[aria-controls="site-navigation"]');
      await toggle.click();
      await expect(toggle).toHaveAttribute('aria-expanded', 'true');
      expect(await overflow()).toBeLessThanOrEqual(1);
      await page.keyboard.press('Escape');
      await expect(toggle).toBeFocused();
      await toggle.click();
    }
    await page
      .getByRole('navigation', { name: 'Main navigation' })
      .getByRole('link', { name: 'Browse Spas' })
      .click();
    await expect(page).toHaveURL(/\/spas$/);
    if (width < 1024)
      await expect(page.getByRole('button', { name: 'Menu', exact: true })).toHaveAttribute(
        'aria-expanded',
        'false'
      );
    await page.goBack();
    await expect(page.getByRole('heading', { name: 'Swedish Massage' })).toBeVisible();
    await page.goForward();
    await expect(page).toHaveURL(/\/spas$/);
  });
}
test('footer, shareable HTML, 404 and restricted booking', async ({ page, request }) => {
  await page.goto('/about');
  const footer = page.getByRole('navigation', { name: 'Footer' });
  const paths = await footer
    .getByRole('link')
    .evaluateAll((links) => links.map((link) => link.getAttribute('href')!));
  expect(new Set(paths).size).toBe(paths.length);
  for (const path of paths)
    expect((await request.get(path, { headers: { Accept: 'text/html' } })).status()).toBe(200);
  const home = await (await request.get('/')).text();
  expect(home).toContain('class="wellness-hero"');
  expect(home).toContain('image/webp');
  const about = await (await request.get('/about', { headers: { Accept: 'text/html' } })).text();
  expect(about).not.toContain('class="wellness-hero"');
  expect(about).toContain('property="og:description"');
  expect(about).toContain('About Nirvana Wellness');
  const response = await page.goto('/missing-wellness-page');
  expect(response?.status()).toBe(404);
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
  await page.getByRole('link', { name: 'Go home', exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto('/booking');
  await expect(page).toHaveURL(/auth\/login\?redirect=/);
  await expect(page.locator('meta[name=robots]')).toHaveAttribute('content', /noindex/);
});

test('a new failure remains readable after an earlier success timer expires', async ({ page }) => {
  await page.goto('/about');
  await expect(page.getByRole('navigation', { name: 'Footer' })).toBeVisible();
  await page.clock.install();
  const notify = (message: string, type: string) =>
    page.evaluate((detail) => window.dispatchEvent(new CustomEvent('nirvana:toast', { detail })), {
      message,
      type
    });
  await notify('Profile saved.', 'success');
  await expect(page.getByRole('status')).toHaveText('Profile saved.');
  await page.clock.fastForward(4000);
  await notify('Please retry the update.', 'error');
  await page.clock.fastForward(7000);
  await expect(page.getByRole('alert')).toHaveText('Please retry the update.');
  await page.getByRole('button', { name: 'Dismiss notification' }).click();
  await expect(page.getByRole('alert')).toBeEmpty();
});

test('a customer opening an administrative link gets an accessible recovery page', async ({
  page
}) => {
  let adminRequests = 0;
  await page.addInitScript(() => localStorage.setItem('nirvana_session_present', 'browser-fixture'));
  await page.route('**/api/auth/browser-session', (r) => r.fulfill({
    json: {accessToken: 'browser-fixture-in-memory', userId: 4, username: 'qa@example.test', roles: ['ROLE_CUSTOMER']}
  }));
  await page.route('**/api/auth/me', (r) =>
    r.fulfill({ json: { userId: 4, username: 'qa@example.test', roles: ['ROLE_CUSTOMER'] } })
  );
  await page.route('**/api/admin/**', (r) => {
    adminRequests++;
    return r.fulfill({ status: 403 });
  });
  await page.goto('/admin/gift-cards');
  await expect(page.getByRole('heading', { name: 'Access restricted' })).toBeVisible();
  await expect(page.getByRole('main').getByRole('link', { name: 'Browse spas' })).toHaveAttribute(
    'href',
    '/spas'
  );
  expect(adminRequests).toBe(0);
});
