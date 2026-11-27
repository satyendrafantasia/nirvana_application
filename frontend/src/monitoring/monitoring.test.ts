import { describe, expect, it } from 'vitest';
import { monitoringConfig } from './index';
import { scrubBreadcrumb, scrubEvent, scrubSpan, scrubText, scrubUrl } from './scrub';
import { pageKey } from '../seo/PageHead';

const dsn = 'https://abc123@o1.ingest.sentry.io/42';

describe('error reporting configuration', () => {
  it('is off without a DSN, so nothing is sent', () => {
    expect(monitoringConfig({})).toBeNull();
    expect(monitoringConfig({ VITE_SENTRY_DSN: '' })).toBeNull();
    expect(monitoringConfig({ VITE_SENTRY_DSN: 'not a dsn' })).toBeNull();
  });
  it('tags environment and release and keeps trace sampling low', () => {
    const config = monitoringConfig({ VITE_SENTRY_DSN: dsn, MODE: 'production' })!;
    expect(config.environment).toBe('production');
    expect(config.release).toMatch(/^nirvana-frontend@/);
    expect(config.tracesSampleRate).toBe(0.05);
    expect(
      monitoringConfig({ VITE_SENTRY_DSN: dsn, VITE_SENTRY_TRACES_SAMPLE_RATE: '1', VITE_SENTRY_RELEASE: 'r9', VITE_SENTRY_ENVIRONMENT: 'staging' })
    ).toMatchObject({ tracesSampleRate: 0.2, release: 'r9', environment: 'staging' });
    expect(monitoringConfig({ VITE_SENTRY_DSN: dsn, VITE_SENTRY_TRACES_SAMPLE_RATE: '0' })!.tracesSampleRate).toBe(0);
  });
});

describe('personal data scrubbing', () => {
  it('masks emails, phone numbers, one-time codes and tokens in text', () => {
    const out = scrubText(
      'User priya@example.com +91 98765 43210 9876543210 entered 482913, Bearer abc.def.ghi, otp=1234 eyJhbGciOi.eyJzdWIi.sig'
    );
    expect(out).not.toMatch(/priya|98765|9876543210|482913|abc\.def|1234|eyJ/);
    expect(out).toContain('[email]');
    expect(out).toContain('[phone]');
  });
  it('drops query strings and masks private links in URLs', () => {
    expect(scrubUrl('https://nirvana.in/auth/otp?phone=9876543210&code=111111#x')).toBe('https://nirvana.in/auth/otp');
    expect(scrubUrl('/b/Zx81kQ0pLm2vN7rT4yW9aB3c')).toBe('/b/:token');
    expect(scrubUrl('/api/spas/12/reviews?page=2')).toBe('/api/spas/:id/reviews');
  });
  it('strips request bodies, headers, cookies and the user from events', () => {
    const event = scrubEvent({
      message: 'Failed for a@b.co',
      user: { email: 'a@b.co', ip_address: '1.2.3.4' },
      extra: { form: { phone: '9876543210' } },
      request: { url: 'https://nirvana.in/booking?token=x', data: '{"otp":"123456"}', cookies: 'sid=1', headers: { Authorization: 'Bearer t' }, query_string: 'token=x' },
      exception: { values: [{ value: 'OTP 654321 rejected for 9876543210' }] },
      breadcrumbs: [
        { category: 'console', message: 'state {"phone":"9876543210"}' },
        { category: 'fetch', data: { url: '/api/auth/otp/verify?phone=9876543210', method: 'POST', status_code: 400, request_body: 'x' } },
        { category: 'navigation', data: { from: '/auth/email-link?oobCode=abc', to: '/me/bookings' } }
      ]
    });
    const json = JSON.stringify(event);
    expect(json).not.toMatch(/a@b\.co|9876543210|123456|654321|sid=|Authorization|oobCode|request_body|1\.2\.3\.4/);
    expect(event.user).toBeUndefined();
    expect(event.request).toEqual({ url: 'https://nirvana.in/booking' });
    expect(event.breadcrumbs).toHaveLength(2);
    expect(event.breadcrumbs![0].data).toEqual({ url: '/api/auth/otp/verify', method: 'POST', status_code: 400 });
  });
  it('drops console breadcrumbs and scrubs span names and attributes', () => {
    expect(scrubBreadcrumb({ category: 'console', message: 'x' })).toBeNull();
    const span = scrubSpan({ name: 'GET /api/spas/7?city=Pune', attributes: { 'url.full': 'https://x.in/api/me?email=a@b.co', 'http.response.status_code': 200 } });
    expect(span.name).toBe('GET /api/spas/:id');
    expect(span.attributes).toEqual({ 'url.full': 'https://x.in/api/me', 'http.response.status_code': 200 });
  });
});

describe('server-rendered page metadata', () => {
  it('distinguishes a canonical city listing from a filtered view', () => {
    expect(pageKey('/spas', '?city=Pune&page=2')).not.toBe(pageKey('/spas/', '?city=pune'));
    expect(pageKey('/spas', '?city=Pune')).toBe(pageKey('/spas/', '?city=pune'));
    expect(pageKey('/spas/12', '')).not.toBe(pageKey('/spas/13', ''));
    expect(pageKey('/spas', '?city=Pune')).not.toBe(pageKey('/spas', '?city=Goa'));
  });
});
