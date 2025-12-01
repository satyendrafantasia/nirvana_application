import client from './client';
import { AuthResponse } from '../types/auth';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterPayload {
  name: string;
  email: string;
  password: string;
  phone?: string;
}

export const login = (payload: LoginPayload) =>
  client.post<AuthResponse>('/auth/login', payload).then(res => res.data);

export const register = (payload: RegisterPayload) =>
  client.post<AuthResponse>('/auth/register', payload).then(res => res.data);

export const requestOtp = (email: string) =>
  client.post<void>('/auth/request-otp', { email }).then(res => res.data);

export const verifyOtp = (email: string, code: string) =>
  client
    .post<AuthResponse>('/auth/verify-otp', { email, code })
    .then(res => res.data);

export const getMe = () => client.get<AuthResponse>('/auth/me').then(res => res.data);

export const loginWithGoogleIdToken = (idToken: string) =>
  client
    .post<AuthResponse>('/auth/google', { idToken })
    .then(res => res.data);
