import axios from 'axios';

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

export const errorMessage = (error: unknown): string =>
  axios.isAxiosError(error)
    ? error.response?.data?.message ||
      (error.response?.status === 401
        ? 'Please sign in again.'
        : 'Unable to reach the spa service. Please retry.')
    : error instanceof Error
      ? error.message
      : 'Something went wrong.';
export default client;
