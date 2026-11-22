import { beforeEach, it, expect, vi } from 'vitest';
import { sendEmailLink, cachedEmail } from './emailLinks';
const mocks = vi.hoisted(() => ({ get: vi.fn(), load: vi.fn(), send: vi.fn() }));
vi.mock('./client', () => ({ default: { get: mocks.get } }));
vi.mock('./phoneCodes', () => ({ loadFirebase: mocks.load }));
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('window', { location: { origin: 'http://127.0.0.1:5173' } });
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', { setItem: store.set.bind(store), getItem: store.get.bind(store) });
  mocks.get.mockResolvedValue({ data: { apiKey: 'test' } });
  mocks.load.mockResolvedValue({ auth: {}, sdk: { sendSignInLinkToEmail: mocks.send } });
});
it.each(['login', 'register', 'owner-register'] as const)(
  'sends a %s callback without the recipient in the URL',
  async (flow) => {
    await sendEmailLink(' user@example.test ', flow, '/me/bookings');
    const [, email, settings] = mocks.send.mock.calls[0];
    const url = new URL(settings.url);
    expect(email).toBe('user@example.test');
    expect(settings.handleCodeInApp).toBe(true);
    expect(url.pathname).toBe('/auth/email-link');
    expect(url.searchParams.get('flow')).toBe(flow);
    expect(url.searchParams.get('redirect')).toBe('/me/bookings');
    expect(settings.url).not.toContain('example.test');
    expect(cachedEmail(url.searchParams.get('request')!)).toBe(email);
  }
);
it('reports a disabled provider without reporting a successful send', async () => {
  mocks.send.mockRejectedValue({ code: 'auth/operation-not-allowed' });
  await expect(sendEmailLink('user@example.test', 'login', '/')).rejects.toThrow('must be enabled');
});
