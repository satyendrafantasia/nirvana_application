import { afterEach, describe, expect, it, vi } from 'vitest';
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});
describe('optional booking analytics', () => {
  it('requires consent, sends only event names and suppresses repeat completion', async () => {
    vi.stubEnv('MODE', 'production');
    vi.stubEnv('PROD', true);
    vi.stubEnv('VITE_ANALYTICS_PATH', '/events');
    const getItem = vi.fn().mockReturnValue(null);
    vi.stubGlobal('localStorage', { getItem });
    const send = vi.fn().mockResolvedValue({});
    vi.stubGlobal('fetch', send);
    const { track } = await import('./telemetry');
    track('begin_booking');
    expect(send).not.toHaveBeenCalled();
    getItem.mockReturnValue('yes');
    track('booking_completed', 'private-receipt');
    track('booking_completed', 'private-receipt');
    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0][1].body).toBe(JSON.stringify({ event: 'booking_completed' }));
    getItem.mockReturnValue('no');
    track('select_service');
    expect(send).toHaveBeenCalledTimes(1);
  });
});
