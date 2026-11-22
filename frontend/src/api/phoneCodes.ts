import { useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ConfirmationResult, RecaptchaVerifier, Auth } from 'firebase/auth';
import client from './client';

/**
 * How phone codes work on this site, from the server:
 * - FIREBASE: the browser asks Google to text the code and check it, then sends the server Google's proof.
 * - SMS: the server texts the code and checks what the person types.
 * - OFF: no phone codes; use email.
 */
export type PhoneCodeMode = 'FIREBASE' | 'SMS' | 'OFF';
export interface PhoneCodeConfig {
  mode: PhoneCodeMode;
  firebase?: { apiKey: string; authDomain: string; projectId: string };
}
/** What a verify endpoint gets: the code typed, or Google's signed proof of the phone. */
export type PhoneProof = { code: string } | { firebaseIdToken: string };

export const phoneCodeConfig = async (): Promise<PhoneCodeConfig> =>
  (await client.get<PhoneCodeConfig>('/api/auth/phone-codes')).data;

export const usePhoneCodeMode = (): PhoneCodeMode | undefined =>
  useQuery({ queryKey: ['phone-codes'], queryFn: phoneCodeConfig, staleTime: 10 * 60_000 }).data
    ?.mode;

/** "98765 43210", "098765 43210" or "+91 98765 43210" → "+919876543210"; null if it isn't a number we can text. */
export function toE164(input: string): string | null {
  const trimmed = input.trim();
  const digits = trimmed.replace(/\D/g, '');
  if (trimmed.startsWith('+'))
    return digits.length >= 8 && digits.length <= 15 ? '+' + digits : null;
  const local = digits.length === 11 && digits.startsWith('0') ? digits.slice(1) : digits;
  if (/^[6-9]\d{9}$/.test(local)) return '+91' + local;
  if (/^91[6-9]\d{9}$/.test(digits)) return '+' + digits;
  return null;
}

const FIREBASE_ERRORS: Record<string, string> = {
  'auth/invalid-verification-code': 'That code is not right. Check the text and try again.',
  'auth/code-expired': 'That code has expired. Ask for a new one.',
  'auth/too-many-requests': 'Too many attempts. Please wait a few minutes and try again.',
  'auth/quota-exceeded': 'We can’t send texts right now. Please try again later or use email.',
  'auth/invalid-phone-number': 'That phone number doesn’t look right.',
  'auth/captcha-check-failed': 'The security check didn’t pass. Reload the page and try again.',
  'auth/network-request-failed': 'No connection to Google. Check your internet and try again.',
  'auth/operation-not-allowed': 'Phone sign-in isn’t switched on yet. Please use email.'
};
export const firebaseMessage = (e: unknown): Error => {
  const code = (e as { code?: string })?.code;
  return new Error(
    (code && FIREBASE_ERRORS[code]) || 'We couldn’t verify that phone number. Please try again.'
  );
};

let firebaseAuth: Promise<{ auth: Auth; sdk: typeof import('firebase/auth') }> | undefined;
/** Loads Firebase only when a phone code is needed, and keeps no Firebase session in the browser. */
export const loadFirebase = (config: NonNullable<PhoneCodeConfig['firebase']>) =>
  (firebaseAuth ??= Promise.all([import('firebase/app'), import('firebase/auth')])
    .then(([app, sdk]) => {
      const instance =
        app.getApps().find((a) => a.name === 'nirvana-phone') ??
        app.initializeApp(config, 'nirvana-phone');
      return { auth: sdk.initializeAuth(instance, { persistence: sdk.inMemoryPersistence }), sdk };
    })
    .catch((e) => {
      firebaseAuth = undefined;
      throw e;
    }));

type Slot = Pick<HTMLElement, 'replaceChildren' | 'ownerDocument'>;

/**
 * Sending and checking a code for one phone number. Call `start` after the server has opened the
 * verification; with SMS the server has already texted the code, with Firebase this asks Google to.
 * `slot` is an empty element on the page where Google's invisible security check lives.
 */
export function phoneCodeSession(slot: () => Slot | null, firebase = loadFirebase) {
  let pending:
    | {
        result: ConfirmationResult;
        verifier: RecaptchaVerifier;
        auth: Auth;
        sdk: typeof import('firebase/auth');
      }
    | undefined;
  const done = () => {
    pending?.verifier.clear();
    pending = undefined;
  };
  return {
    async start(phoneE164: string) {
      done();
      const config = await phoneCodeConfig();
      if (config.mode !== 'FIREBASE' || !config.firebase) return;
      const el = slot();
      if (!el) throw new Error('The page is not ready yet. Try again.');
      const { auth, sdk } = await firebase(config.firebase);
      // A fresh element each time: a cleared verifier can't render into the same one again.
      const box = el.ownerDocument.createElement('div');
      el.replaceChildren(box);
      const verifier = new sdk.RecaptchaVerifier(auth, box, { size: 'invisible' });
      try {
        const result = await sdk.signInWithPhoneNumber(auth, phoneE164, verifier);
        pending = { result, verifier, auth, sdk };
      } catch (e) {
        verifier.clear();
        throw firebaseMessage(e);
      }
    },
    /** Turns the code typed into what the server's verify endpoint expects. */
    async proof(code: string): Promise<PhoneProof> {
      const p = pending;
      if (!p) return { code };
      try {
        const credential = await p.result.confirm(code);
        const firebaseIdToken = await credential.user.getIdToken();
        await p.sdk.signOut(p.auth);
        done();
        return { firebaseIdToken };
      } catch (e) {
        throw firebaseMessage(e);
      }
    },
    reset: done
  };
}

/** `phoneCodeSession` for a page: render `slot` as an empty `<div ref={slot} />`. */
export function usePhoneCode() {
  const slot = useRef<HTMLDivElement>(null);
  const session = useRef<ReturnType<typeof phoneCodeSession>>();
  session.current ??= phoneCodeSession(() => slot.current);
  return { slot, ...session.current };
}

/** The proof as the sign-in, sign-up and password reset endpoints name it. */
export const otpBody = (proof: PhoneProof) =>
  'code' in proof ? { otp: proof.code } : { firebaseIdToken: proof.firebaseIdToken };
