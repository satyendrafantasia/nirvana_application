import { beforeEach, expect, it, vi } from 'vitest';
import { AxiosError, InternalAxiosRequestConfig } from 'axios';
import client, { renewSession, sessionClient } from './client';
beforeEach(() => {
  const values = new Map([
    ['nirvana_token', 'old'],
    ['nirvana_refresh', 'refresh-old']
  ]);
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => values.get(k) || null,
    setItem: (k: string, v: string) => values.set(k, v),
    removeItem: (k: string) => values.delete(k)
  });
  vi.stubGlobal('window', new EventTarget());
});
const reply = (config: InternalAxiosRequestConfig, data: unknown) => ({
  config,
  data,
  status: 200,
  statusText: 'OK',
  headers: {}
});
it('shares one refresh across concurrent callers and rotates both credentials', async () => {
  const adapter = vi.fn(async (config) =>
    reply(config, { accessToken: 'new', refreshToken: 'refresh-new' })
  );
  sessionClient.defaults.adapter = adapter;
  const first = renewSession(),
    second = renewSession();
  expect(first).toBe(second);
  await Promise.all([first, second]);
  expect(adapter).toHaveBeenCalledTimes(1);
  expect(localStorage.getItem('nirvana_token')).toBe('new');
  expect(localStorage.getItem('nirvana_refresh')).toBe('refresh-new');
});
it('does not restore a session logged out while refresh was pending', async () => {
  sessionClient.defaults.adapter = async (config) => {
    localStorage.removeItem('nirvana_refresh');
    return reply(config, { accessToken: 'new', refreshToken: 'refresh-new' });
  };
  await expect(renewSession()).rejects.toThrow('Session changed');
  expect(localStorage.getItem('nirvana_refresh')).toBeNull();
  expect(localStorage.getItem('nirvana_token')).not.toBe('new');
});
it('clears an expired session when renewal fails', async () => {
  sessionClient.defaults.adapter = async () => {
    throw new Error('Revoked');
  };
  const expired = vi.fn();
  window.addEventListener('nirvana:expired', expired);
  await expect(renewSession()).rejects.toThrow('Revoked');
  expect(localStorage.getItem('nirvana_token')).toBeNull();
  expect(expired).toHaveBeenCalledTimes(1);
});
it('retries an authenticated request once after refreshing and never loops', async () => {
  const refresh = vi.fn(async (config) =>
    reply(config, { accessToken: 'new', refreshToken: 'refresh-new' })
  );
  sessionClient.defaults.adapter = refresh;
  const request = vi.fn(async (config) => {
    throw new AxiosError('Unauthorized', '401', config, undefined, {
      ...reply(config, {}),
      status: 401
    });
  });
  client.defaults.adapter = request;
  await expect(client.get('/api/me/bookings')).rejects.toThrow('Unauthorized');
  expect(request).toHaveBeenCalledTimes(2);
  expect(refresh).toHaveBeenCalledTimes(1);
});
it('does not refresh rejected login credentials', async () => {
  const refresh = vi.fn();
  sessionClient.defaults.adapter = refresh;
  client.defaults.adapter = async (config) => {
    throw new AxiosError('Unauthorized', '401', config, undefined, {
      ...reply(config, {}),
      status: 401
    });
  };
  await expect(client.post('/api/auth/login', {})).rejects.toThrow();
  expect(refresh).not.toHaveBeenCalled();
});
