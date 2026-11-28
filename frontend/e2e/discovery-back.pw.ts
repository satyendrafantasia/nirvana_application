import { expect, test, type Page } from '@playwright/test';

async function mockDiscovery(page: Page) {
  const requests: { city: string; pageToken?: string }[] = [];
  const user = { userId: 4, username: 'guest@example.test', roles: ['ROLE_CUSTOMER'] };
  const place = { id: 'lotus', displayName: { text: 'Lotus Spa' }, photos: [{ name: 'places/lotus/photos/cover' }] };
  await page.addInitScript(() => localStorage.setItem('nirvana_session_present', 'browser-fixture'));
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/auth/browser-session')) return route.fulfill({ json: { ...user, accessToken: 'fixture' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ json: user });
    if (path.endsWith('/discovery/status')) return route.fulfill({ json: { configured: true } });
    if (path.endsWith('/discovery/search')) {
      const request = route.request().postDataJSON();
      requests.push(request);
      const places = request.pageToken ? [place] : [{ id: 'first', displayName: { text: 'First Spa' } }];
      return route.fulfill({ json: { places, nextPageToken: request.pageToken ? undefined : 'page-two' } });
    }
    if (path.endsWith('/discovery/places/lotus')) return route.fulfill({ json: place });
    if (path.endsWith('/photos/cover/image')) {
      return route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"/>' });
    }
    return route.fulfill({ json: [] });
  });
  return requests;
}

for (const entry of ['/discover', '/spas?source=google', '/']) {
  test('return to paginated Google results from ' + entry, async ({ page }) => {
    const requests = await mockDiscovery(page);
    await page.goto(entry);
    if (entry === '/') await page.getByRole('button', { name: 'Google Places', exact: true }).click();
    const city = page.getByLabel('City', { exact: true });
    await city.fill('Lucknow');
    await page.getByRole('button', { name: 'Search Google Places' }).click();
    await page.getByRole('button', { name: 'Next Google page' }).click();
    const results = page.getByRole('region', { name: 'Live Google results' });
    const details = results.getByRole('link', { name: 'View details' });
    await page.evaluate(() => document.fonts.ready.then(() => undefined));
    await details.scrollIntoViewIfNeeded();
    await details.evaluate(link => link.addEventListener('click', () => {
      link.setAttribute('data-scroll-at-click', String(window.scrollY));
    }, { once: true }));
    await details.click();
    await expect(page.getByRole('heading', { level: 1, name: 'Lotus Spa' })).toBeVisible();
    await page.goBack();
    await expect(city).toHaveValue('Lucknow');
    await expect(details).toBeFocused();
    await expect(results.getByRole('heading', { name: 'Lotus Spa' })).toBeVisible();
    const scroll = Number(await details.getAttribute('data-scroll-at-click'));
    expect(scroll).toBeGreaterThan(0);
    // Font layout and browser scroll anchoring can shift the viewport by a few pixels.
    await expect.poll(() => page.evaluate(y => Math.abs(window.scrollY - y), scroll)).toBeLessThan(4);
    await page.goForward();
    await page.getByRole('navigation', { name: 'On this page' }).getByRole('link', { name: 'Reviews' }).click();
    await expect(page).toHaveURL(/#reviews$/);
    await page.getByRole('button', { name: 'Back to results' }).click();
    await expect(page).toHaveURL(url => url.pathname + url.search === entry);
    await expect(details).toBeFocused();
    expect(requests).toHaveLength(2);
    expect(requests[1]).toMatchObject({ city: 'Lucknow', pageToken: 'page-two' });
  });
}

test('a direct Google listing opens a fresh search', async ({ page }) => {
  const requests = await mockDiscovery(page);
  await page.goto('/discover/places/lotus');
  await page.getByRole('link', { name: 'Search spas', exact: false }).click();
  await expect(page.getByLabel('City', { exact: true })).toHaveValue('');
  expect(requests).toHaveLength(0);
});

test('opening a photo on a narrow screen returns to the same card and allows a new search', async ({ page }) => {
  const requests = await mockDiscovery(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/spas?source=google&city=Lucknow');
  const search = page.getByRole('button', { name: 'Search Google Places' });
  await search.click();
  await page.getByRole('button', { name: 'Next Google page' }).click();
  const results = page.getByRole('region', { name: 'Live Google results' });
  await results.scrollIntoViewIfNeeded();
  const photo = results.getByRole('link', { name: 'Photo of Lotus Spa' });
  await photo.click();
  await page.getByRole('button', { name: 'Back to results' }).click();
  await expect(photo).toBeFocused();
  expect(requests).toHaveLength(2);
  await page.getByLabel('City', { exact: true }).fill('Noida');
  await expect(results).toHaveCount(0);
  await search.click();
  await expect(results.getByRole('heading', { name: 'First Spa' })).toBeVisible();
  expect(requests[2]).toMatchObject({ city: 'Noida' });
  expect(requests[2]).not.toHaveProperty('pageToken');
});
