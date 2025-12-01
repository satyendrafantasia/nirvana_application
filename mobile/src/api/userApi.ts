import client from './client';
import { UpdateProfilePayload, UserProfile } from '../types/user';

export const updateProfile = (payload: UpdateProfilePayload) =>
  client.patch<UserProfile>('/users/me', payload).then(res => res.data);
