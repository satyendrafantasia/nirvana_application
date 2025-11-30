import client from './client';
import { UserProfile } from '../types/user';

export const getMe = async () => {
  const { data } = await client.get<UserProfile>('/api/me');
  return data;
};

export const updateProfile = async (payload: Partial<UserProfile>) => {
  const { data } = await client.put<UserProfile>('/api/me', payload);
  return data;
};
