import { beforeEach, expect, it, vi } from 'vitest';
import { AxiosError, InternalAxiosRequestConfig } from 'axios';
import client, { beginSession, currentAccess, forgetSession, purgeLegacyTokens, sessionMarker, errorMessage, renewSession, sessionClient } from './client';
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
  purgeLegacyTokens();
  beginSession('old');
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
  expect(currentAccess()).toBe('new');
  expect(localStorage.getItem('nirvana_token')).toBeNull();
  expect(localStorage.getItem('nirvana_refresh')).toBeNull();
});
it('does not restore a session logged out while refresh was pending', async () => {
  sessionClient.defaults.adapter = async (config) => {
    forgetSession();
    return reply(config, { accessToken: 'new', refreshToken: 'refresh-new' });
  };
  await expect(renewSession()).rejects.toThrow('Session changed');
  expect(localStorage.getItem('nirvana_refresh')).toBeNull();
  expect(currentAccess()).toBeNull();
});
it('clears an expired session when renewal fails', async () => {
  sessionClient.defaults.adapter = async (config) => {
    throw new AxiosError('Revoked', '401', config, undefined, { ...reply(config, {}), status: 401 });
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

it('keeps validation actionable and infrastructure details out of notifications', () => {
  const failure = (status: number, data: unknown) => ({isAxiosError:true, response:{status,data}});
  expect(errorMessage(failure(503, {message:'internal diagnostic'}))).toMatch(/temporarily unavailable/);
  expect(errorMessage(failure(403, {}))).toMatch(/access/);
  expect(errorMessage(new AxiosError('network error'))).toMatch(/connection/i);
  expect(errorMessage(failure(422, {fieldErrors:[{field:'date', message:'Choose a future date'}]}))).toBe('date: Choose a future date');
  expect(errorMessage(failure(400, {fieldErrors:{},message:{}}))).toMatch(/retry/i);
});

it('sends the CSRF header for the cookie exchange and keeps the access token in memory', async () => {
  sessionClient.defaults.adapter = async (config) => {
    expect(config.url).toBe('/api/auth/browser-session');
    expect(config.headers.get('X-Nirvana-Session')).toBe('browser');
    expect(config.data).toBeUndefined();
    return reply(config, {accessToken: 'only-in-memory'});
  };
  await renewSession();
  expect(currentAccess()).toBe('only-in-memory');
  expect(localStorage.getItem(sessionMarker)).not.toContain('only-in-memory');
  expect(localStorage.getItem('nirvana_token')).toBeNull();
  expect(localStorage.getItem('nirvana_refresh')).toBeNull();
});

it('rotates a packaged WebView session without cookies or persisted credentials', async () => {
  vi.stubEnv('VITE_AUTH_TRANSPORT', 'memory');
  try {
    vi.resetModules();
    const native = await vi.importActual<typeof import('./client')>('./client');
    native.rememberRefresh('temporary-native-refresh');
    native.beginSession('temporary-native-access');
    native.sessionClient.defaults.adapter = async (request) => {
      expect(request.withCredentials).toBe(false);
      expect(request.headers.has('X-Nirvana-Session')).toBe(false);
      expect(request.url).toBe('/api/auth/refresh');
      expect(JSON.parse(request.data).refreshToken).toBe('temporary-native-refresh');
      return reply(request,{accessToken:'renewed',refreshToken:'rotated'});
    };
    expect(await native.renewSession()).toBe('renewed');
    expect(localStorage.getItem('nirvana_token')).toBeNull();
    expect(localStorage.getItem('nirvana_refresh')).toBeNull();
    native.forgetSession();
    await expect(native.renewSession()).rejects.toThrow(/sign in/);
  } finally {
    vi.unstubAllEnvs();
  }
});
