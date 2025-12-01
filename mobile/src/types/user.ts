import { User } from './auth';

export interface UpdateProfilePayload {
  name?: string;
  phone?: string;
  avatarUrl?: string;
}

export interface UserProfile extends User {
  avatarUrl?: string;
  defaultPaymentMethod?: string;
}
