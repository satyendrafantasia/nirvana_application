import client from './client';
import { UserProfile } from '../types/user';
export const getMe = async (): Promise<UserProfile> => {
  const { data } = await client.get<{ userId: number; username: string }>('/api/auth/me');
  return { id: String(data.userId), email: data.username, firstName: data.username, lastName: '' };
};
