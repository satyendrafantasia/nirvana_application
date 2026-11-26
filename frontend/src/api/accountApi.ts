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

/** Downloads everything Nirvana holds about you as a JSON file (DPDP Act 2023). */
export const downloadMyData = async (): Promise<void> => {
  const response = await client.get<Blob>('/api/me/account/export', { responseType: 'blob' });
  const header = String(response.headers['content-disposition'] ?? '');
  const name = /filename="([^"]+)"/.exec(header)?.[1] ?? 'nirvana-my-data.json';
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
};

export interface DeletionStatus {
  pending: boolean;
  requestedAt?: string | null;
  eraseAfter?: string | null;
  graceDays: number;
  /** What stops deletion right now, such as an upcoming booking or an open spa. */
  blockers: string[];
}

/** A password, or the login token from a sign-in code sent to the account's contact. */
export type DeletionProof = { password: string } | { loginToken: string };

export const getDeletionStatus = async (): Promise<DeletionStatus> =>
  (await client.get<DeletionStatus>('/api/me/account/deletion')).data;

/** Schedules deletion and signs you out everywhere; signing in again before the date cancels it. */
export const requestDeletion = async (proof: DeletionProof): Promise<DeletionStatus> =>
  (await client.post<DeletionStatus>('/api/me/account/deletion', proof)).data;

export const deletionDate = (iso: string | null | undefined): string =>
  iso
    ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })
    : '';
