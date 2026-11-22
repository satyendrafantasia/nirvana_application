import client from './client';
import { AuthResponse, LoginPayload, RegisterPayload } from '../types/auth';
import { otpBody, PhoneProof } from './phoneCodes';
export interface AuthWire {
  accessToken?: string;
  refreshToken?: string;
  roles?: string[];
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
    user: { id: String(r.userId), email: r.username, firstName: r.username, lastName: '', roles: r.roles || [] }
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
/** Where a sign-in code goes: an email address, or a mobile number in +country form. */
export type CodeContact = string | { email: string } | { phoneNumber: string };
const contactBody = (c: CodeContact) => (typeof c === 'string' ? { email: c } : c);

export const requestOtp = async (contact: CodeContact): Promise<{ verificationId: string }> =>
  (await client.post('/api/auth/login/otp/request', contactBody(contact))).data;
/** `proof` is the code typed, or Google's proof of the phone when Firebase checked the code. */
export const verifyOtp = async (
  verificationId: string,
  proof: string | PhoneProof
): Promise<AuthResponse> => {
  const body = { verificationId, ...otpBody(typeof proof === 'string' ? { code: proof } : proof) };
  if (sessionStorage.getItem('nirvana_mfa')) {
    const r = await client.post('/api/auth/login/mfa/verify', body);
    sessionStorage.removeItem('nirvana_mfa');
    return authView(r.data);
  }
  const { data } = await client.post('/api/auth/login/otp/verify', body);
  return authView(
    (await client.post('/api/auth/login/otp/complete', { loginToken: data.loginToken })).data
  );
};
