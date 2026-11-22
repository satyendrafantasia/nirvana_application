import { beforeEach, describe, expect, it, vi } from 'vitest';

let config: unknown = { mode: 'SMS' };
vi.mock('./client', () => ({ default: { get: vi.fn(async () => ({ data: config })) } }));

import { firebaseMessage, otpBody, phoneCodeSession, toE164 } from './phoneCodes';

describe('phone numbers', () => {
  it('reads Indian mobiles typed in any common way', () => {
    expect(toE164('98765 43210')).toBe('+919876543210');
    expect(toE164('098765-43210')).toBe('+919876543210');
    expect(toE164('919876543210')).toBe('+919876543210');
    expect(toE164('+91 98765 43210')).toBe('+919876543210');
    expect(toE164('+44 7700 900123')).toBe('+447700900123');
  });
  it('rejects what is not a mobile number', () => {
    expect(toE164('12345')).toBeNull();
    expect(toE164('0522 1234567')).toBeNull();
    expect(toE164('')).toBeNull();
  });
});

describe('the code or proof sent to the server', () => {
  it('names them as the sign-in endpoints expect', () => {
    expect(otpBody({ code: '123456' })).toEqual({ otp: '123456' });
    expect(otpBody({ firebaseIdToken: 't' })).toEqual({ firebaseIdToken: 't' });
  });
  it('turns Firebase errors into plain words', () => {
    expect(firebaseMessage({ code: 'auth/invalid-verification-code' }).message).toMatch(
      /not right/
    );
    expect(firebaseMessage(new Error('x')).message).toMatch(/couldn’t verify/);
  });
});

describe('a phone code session', () => {
  const slot = () =>
    ({ replaceChildren: vi.fn(), ownerDocument: { createElement: () => ({}) } }) as never;

  beforeEach(() => {
    config = { mode: 'SMS' };
  });

  it('with our own SMS, passes the typed code through and never loads Firebase', async () => {
    const firebase = vi.fn();
    const session = phoneCodeSession(slot, firebase);
    await session.start('+919876543210');
    expect(await session.proof('482913')).toEqual({ code: '482913' });
    expect(firebase).not.toHaveBeenCalled();
  });

  it('with Firebase, asks Google to text the number and returns its proof', async () => {
    config = { mode: 'FIREBASE', firebase: { apiKey: 'k', authDomain: 'd', projectId: 'p' } };
    const confirm = vi.fn(async () => ({ user: { getIdToken: async () => 'google-proof' } }));
    const signInWithPhoneNumber = vi.fn(async () => ({ confirm }));
    const signOut = vi.fn(async () => undefined);
    const clear = vi.fn();
    const sdk = {
      RecaptchaVerifier: vi.fn(() => ({ clear })),
      signInWithPhoneNumber,
      signOut
    };
    const session = phoneCodeSession(slot, (async () => ({ auth: {}, sdk })) as never);
    await session.start('+919876543210');
    expect(signInWithPhoneNumber).toHaveBeenCalledWith({}, '+919876543210', expect.anything());
    expect(await session.proof('482913')).toEqual({ firebaseIdToken: 'google-proof' });
    expect(confirm).toHaveBeenCalledWith('482913');
    expect(signOut).toHaveBeenCalled();
    expect(clear).toHaveBeenCalled();
  });

  it('explains a wrong code in plain words', async () => {
    config = { mode: 'FIREBASE', firebase: { apiKey: 'k', authDomain: 'd', projectId: 'p' } };
    const sdk = {
      RecaptchaVerifier: vi.fn(() => ({ clear: vi.fn() })),
      signInWithPhoneNumber: async () => ({
        confirm: async () => {
          throw { code: 'auth/invalid-verification-code' };
        }
      }),
      signOut: vi.fn()
    };
    const session = phoneCodeSession(slot, (async () => ({ auth: {}, sdk })) as never);
    await session.start('+919876543210');
    await expect(session.proof('000000')).rejects.toThrow(/not right/);
  });
});
