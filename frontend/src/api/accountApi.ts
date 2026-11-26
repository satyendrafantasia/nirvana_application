import client from './client';
import { AuthWire } from './authApi';

export interface Account {
  id: number;
  email?: string;
  phone?: string;
  firstName?: string;
  lastName?: string;
  displayName?: string;
  timezone: string;
  locale?: string;
  marketingOptIn: boolean;
  loyaltyPoints: number;
  emailVerified: boolean;
  phoneVerified: boolean;
  /** Booking updates on WhatsApp, which needs a verified mobile. */
  whatsappUpdates?: boolean;
}

export interface ProfileInput {
  firstName: string;
  lastName?: string;
  displayName?: string;
  timezone: string;
  locale?: string;
  marketingOptIn: boolean;
  whatsappUpdates?: boolean;
}

export const getAccount = async (): Promise<Account> => (await client.get<Account>('/api/me/account')).data;

export const updateAccount = async (input: ProfileInput): Promise<Account> =>
  (await client.put<Account>('/api/me/account', input)).data;

/** Returns a fresh session; every other device is signed out. */
export const changePassword = async (currentPassword: string, newPassword: string): Promise<AuthWire> =>
  (await client.post<AuthWire>('/api/me/account/password', { currentPassword, newPassword })).data;

/** Matches the server: bcrypt only uses 72 bytes, so longer passwords are refused. */
export const passwordProblem = (current: string, next: string, confirmation: string): string | undefined => {
  if (!current) return 'Enter your current password.';
  if (next.length < 8) return 'Your new password needs at least 8 characters.';
  if (new TextEncoder().encode(next).length > 72) return 'Your new password is too long (72 characters at most).';
  if (next === current) return 'Choose a new password that is different from your current one.';
  if (next !== confirmation) return 'The new passwords do not match.';
  return undefined;
};
