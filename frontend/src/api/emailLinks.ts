import client from './client';
import { loadFirebase, PhoneCodeConfig } from './phoneCodes';

export type EmailFlow = 'login' | 'register' | 'owner-register';

export async function sendEmailLink(email: string, flow: EmailFlow, redirect: string) {
  const address = email.trim();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address)) throw new Error('Enter a valid email address.');
  const { data } = await client.get<NonNullable<PhoneCodeConfig['firebase']>>(
    '/api/auth/firebase/config'
  );
  if (!data.apiKey) throw new Error('Email links are not configured.');
  const { auth, sdk } = await loadFirebase(data);
  const url = new URL('/auth/email-link', window.location.origin);
  const request = crypto.randomUUID();
  url.search = new URLSearchParams({ flow, request, redirect }).toString();
  try {
    await sdk.sendSignInLinkToEmail(auth, address, { url: url.href, handleCodeInApp: true });
  } catch (e) {
    const code = (e as { code?: string }).code;
    if (code === 'auth/operation-not-allowed')
      throw new Error('Email link sign-in must be enabled in Firebase Authentication.');
    if (code === 'auth/unauthorized-continue-uri')
      throw new Error('Add this site to the Firebase Authentication authorized domains.');
    if (code === 'auth/too-many-requests' || code === 'auth/quota-exceeded')
      throw new Error('Email sending is temporarily limited. Please try again later.');
    throw e;
  }
  try {
    localStorage.setItem('nirvana-email-link:' + request, address);
  } catch {
    /* The recipient can re-enter it. */
  }
}

export function cachedEmail(request: string) {
  try {
    return localStorage.getItem('nirvana-email-link:' + request) ?? '';
  } catch {
    return '';
  }
}
