import axios, { InternalAxiosRequestConfig } from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || ''
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('nirvana_token');
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const sessionClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  withCredentials: true
});
let renewal: Promise<string> | undefined;
export function renewSession(): Promise<string> {
  if (renewal) return renewal;
  const refreshToken = localStorage.getItem('nirvana_refresh');
  if (!refreshToken) return Promise.reject(new Error('Please sign in again.'));
  renewal = sessionClient
    .post('/api/auth/refresh', { refreshToken })
    .then(({ data }) => {
      if (localStorage.getItem('nirvana_refresh') !== refreshToken)
        throw new Error('Session changed. Please retry.');
      if (!data.accessToken || !data.refreshToken) throw new Error('Please sign in again.');
      localStorage.setItem('nirvana_token', data.accessToken);
      localStorage.setItem('nirvana_refresh', data.refreshToken);
      window.dispatchEvent(new CustomEvent('nirvana:renewed', { detail: data }));
      return data.accessToken as string;
    })
    .catch((error) => {
      if (localStorage.getItem('nirvana_refresh') === refreshToken) {
        localStorage.removeItem('nirvana_token');
        localStorage.removeItem('nirvana_refresh');
        window.dispatchEvent(new Event('nirvana:expired'));
      }
      throw error;
    })
    .finally(() => {
      renewal = undefined;
    });
  return renewal;
}
client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as (InternalAxiosRequestConfig & { renewed?: boolean }) | undefined;
    if (
      error.response?.status !== 401 ||
      !config ||
      config.renewed ||
      /\/auth\/(?!me(?:$|\?))/.test(config.url || '')
    )
      throw error;
    config.renewed = true;
    if (!localStorage.getItem('nirvana_refresh')) {
      localStorage.removeItem('nirvana_token');
      window.dispatchEvent(new Event('nirvana:expired'));
      throw error;
    }
    const current = localStorage.getItem('nirvana_token');
    if (current && config.headers.Authorization !== `Bearer ${current}`) {
      config.headers.Authorization = `Bearer ${current}`;
      return client.request(config);
    }
    config.headers.Authorization = `Bearer ${await renewSession()}`;
    return client.request(config);
  }
);

export const errorMessage = (error: unknown): string =>
  axios.isAxiosError(error)
    ? error.response?.data?.fieldErrors
        ?.map((field: { field: string; message: string }) => `${field.field}: ${field.message}`)
        .join('; ') ||
      error.response?.data?.message ||
      (error.response?.status === 401
        ? 'Please sign in again.'
        : 'Unable to reach the spa service. Please retry.')
    : error instanceof Error
      ? error.message
      : 'Something went wrong.';
export default client;
