import client from './client';
import { AuthResponse, LoginPayload, RegisterPayload } from '../types/auth';
export interface AuthWire {
  accessToken?: string;
  refreshToken?: string;
  userId: number;
  username: string;
  mfaRequired?: boolean;
  mfaVerificationId?: string;
}
export const authView = (r: AuthWire): AuthResponse => {
  if (r.mfaRequired) {
    sessionStorage.setItem('nirvana_mfa', r.mfaVerificationId || '');
    throw new Error('Enter the verification code sent to you using the OTP sign-in page.');
  }
  if (!r.accessToken) throw new Error('Authentication did not return a session.');
  if (r.refreshToken) localStorage.setItem('nirvana_refresh', r.refreshToken);
  return {
    token: r.accessToken,
    user: { id: String(r.userId), email: r.username, firstName: r.username, lastName: '' }
  };
};
export const login = async (p: LoginPayload) =>
  authView(
    (await client.post('/api/auth/login', { username: p.email, password: p.password })).data
  );
export const register = async (p: RegisterPayload) =>
  authView(
    (
      await client.post('/api/auth/register', {
        username: p.email,
        password: p.password,
        name: p.firstName,
        lastName: p.lastName,
        roleType: 'CUSTOMER',
        acceptPrivacyPolicy: true,
        marketingOptIn: false,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
      })
    ).data
  );
export const requestOtp = async (email: string): Promise<{ verificationId: string }> =>
  (await client.post('/api/auth/login/otp/request', { email })).data;
export const verifyOtp = async (verificationId: string, otp: string): Promise<AuthResponse> => {
  if (sessionStorage.getItem('nirvana_mfa')) {
    const r = await client.post('/api/auth/login/mfa/verify', { verificationId, otp });
    sessionStorage.removeItem('nirvana_mfa');
    return authView(r.data);
  }
  const { data } = await client.post('/api/auth/login/otp/verify', { verificationId, otp });
  return authView(
    (await client.post('/api/auth/login/otp/complete', { loginToken: data.loginToken })).data
  );
};
