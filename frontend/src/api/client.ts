import axios, { InternalAxiosRequestConfig } from 'axios';

// Packaged WebViews can have a different site from the API. Keep their JSON
// refresh token in memory when third-party cookies are unavailable.
const nativeRuntime = (globalThis as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor;
const memoryTransport = import.meta.env.VITE_AUTH_TRANSPORT === 'memory' || nativeRuntime?.isNativePlatform?.() === true;
const config = {
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  withCredentials: !memoryTransport,
  headers: memoryTransport ? undefined : { 'X-Nirvana-Session': 'browser' }
};
const client = axios.create(config);
export const sessionClient = axios.create(config);
export const sessionMarker = 'nirvana_session_present';
const sessionEndpoint = '/api/auth/browser-session';
let accessToken: string | null = null;
let nativeRefresh: string | null = null;
let nativeMarker: string | null = null;
export const activeMarker = () => memoryTransport ? nativeMarker : localStorage.getItem(sessionMarker);
export function rememberRefresh(token?: string) {
  if (memoryTransport && token) nativeRefresh = token;
}
let renewal: Promise<string> | undefined;
export const currentAccess = () => accessToken;
export const clearAccess = () => { accessToken = null; };
export function beginSession(token: string) {
  accessToken = token;
  if (memoryTransport) nativeMarker = crypto.randomUUID();
  else localStorage.setItem(sessionMarker, crypto.randomUUID());
}
export function forgetSession() {
  clearAccess();
  nativeMarker = null;
  nativeRefresh = null;
  if (!memoryTransport) localStorage.removeItem(sessionMarker);
}
export function purgeLegacyTokens() {
  localStorage.removeItem('nirvana_token');
  localStorage.removeItem('nirvana_refresh');
}

// Only an opaque sign-in marker crosses tabs. The cookie can be used solely by the
// session exchange; ordinary authenticated APIs require the in-memory bearer.
client.interceptors.request.use((request) => {
  if (accessToken && !request.headers.has('Authorization'))
    request.headers.set('Authorization', 'Bearer ' + accessToken);
  return request;
});
const pageCorrelationId = globalThis.crypto?.randomUUID?.();
for (const api of [client, sessionClient]) {
  api.interceptors.request.use((request) => {
    if (pageCorrelationId) request.headers.set('X-Correlation-ID', pageCorrelationId);
    return request;
  });
}

async function withSessionLock<T>(work: () => Promise<T>): Promise<T> {
  return globalThis.navigator?.locks
    ? await navigator.locks.request('nirvana-session-cookie', work)
    : work();
}

export function revokeBrowserSession(): Promise<void> {
  const token = nativeRefresh;
  if (memoryTransport) return token ? sessionClient.post('/api/auth/logout', { refreshToken: token }).then(() => {}) : Promise.resolve();
  return withSessionLock(async () => { await sessionClient.delete(sessionEndpoint); });
}

export function renewSession(): Promise<string> {
  if (renewal) return renewal;
  const marker = activeMarker();
  if (!marker) return Promise.reject(new Error('Please sign in again.'));
  renewal = withSessionLock(async () => {
    if (activeMarker() !== marker) throw new Error('Session changed. Please retry.');
    const { data } = memoryTransport
      ? await sessionClient.post('/api/auth/refresh', { refreshToken: nativeRefresh })
      : await sessionClient.post(sessionEndpoint);
    if (activeMarker() !== marker) throw new Error('Session changed. Please retry.');
    if (typeof data.accessToken !== 'string' || !data.accessToken) throw new Error('Please sign in again.');
    rememberRefresh(data.refreshToken);
    accessToken = data.accessToken;
    window.dispatchEvent(new CustomEvent('nirvana:renewed', { detail: data }));
    return data.accessToken as string;
  }).catch((error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401 && activeMarker() === marker) {
      forgetSession();
      window.dispatchEvent(new Event('nirvana:expired'));
    }
    throw error;
  }).finally(() => { renewal = undefined; });
  return renewal;
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config as (InternalAxiosRequestConfig & { renewed?: boolean }) | undefined;
    if (error.response?.status !== 401 || !request || request.renewed ||
      !activeMarker() || /\/auth\/(?!me(?:$|\?))/.test(request.url || ''))
      throw error;
    request.renewed = true;
    const current = accessToken;
    request.headers.set('Authorization', 'Bearer ' +
      (current && request.headers.Authorization !== 'Bearer ' + current ? current : await renewSession()));
    return client.request(request);
  }
);

export const errorMessage = (error: unknown): string => {
  if (!axios.isAxiosError(error)) return error instanceof Error ? error.message : 'Something went wrong.';
  const response = error.response;
  if (!response) return 'Connection interrupted. Check your connection and retry.';
  if (response.status >= 500) return 'The service is temporarily unavailable. Please try again.';
  if (response.status === 403) return 'Your account does not have access to this action.';
  const fields = response.data?.fieldErrors;
  if (Array.isArray(fields)) {
    const messages = fields.filter(field => typeof field?.field === 'string' && typeof field?.message === 'string')
      .map(field => field.field + ': ' + field.message);
    if (messages.length) return messages.join('; ');
  }
  if (typeof response.data?.message === 'string' && response.data.message.trim()) return response.data.message;
  return response.status === 401 ? 'Please sign in again.' : 'Unable to complete this request. Please retry.';
};
export default client;
