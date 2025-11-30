import client from './client';
import { AuthResponse, LoginPayload, RegisterPayload } from '../types/auth';

export const login = async (payload: LoginPayload) => {
  const { data } = await client.post<AuthResponse>('/api/auth/login', payload);
  return data;
};

export const register = async (payload: RegisterPayload) => {
  const { data } = await client.post<AuthResponse>('/api/auth/register', payload);
  return data;
};

export const requestOtp = async (email: string) => {
  const { data } = await client.post('/api/auth/otp/request', { email });
  return data;
};

export const verifyOtp = async (email: string, code: string) => {
  const { data } = await client.post<AuthResponse>('/api/auth/otp/verify', { email, code });
  return data;
};

export const loginWithGoogle = async (idToken: string) => {
  const { data } = await client.post<AuthResponse>('/api/auth/google', { idToken });
  return data;
};
