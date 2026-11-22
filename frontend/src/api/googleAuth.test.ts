import { beforeEach, it, expect, vi } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { googleAuthOptions } from './googleAuth';

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  resolver: class BrowserResolver {},
  popup: vi.fn(),
  signOut: vi.fn(),
  load: vi.fn(),
  select: vi.fn(),
  token: vi.fn()
}));
vi.mock('./client', () => ({ default: { get: mocks.get } }));
vi.mock('./phoneCodes', () => ({ loadFirebase: mocks.load }));

beforeEach(() => {
  vi.resetAllMocks();
  mocks.get.mockResolvedValue({ data: { apiKey: 'test', projectId: 'test' } });
  mocks.load.mockResolvedValue({
    auth: {},
    sdk: {
      GoogleAuthProvider: class {
        setCustomParameters = mocks.select;
      },
      browserPopupRedirectResolver: mocks.resolver,
      signInWithPopup: mocks.popup,
      signOut: mocks.signOut
    }
  });
});

it('uses the selected Google account and returns the Firebase proof', async () => {
  mocks.popup.mockResolvedValue({ user: { getIdToken: mocks.token } });
  mocks.token.mockResolvedValue('firebase-proof');
  const choose = await new QueryClient().fetchQuery(googleAuthOptions);
  expect(await choose!()).toBe('firebase-proof');
  expect(mocks.popup).toHaveBeenCalledWith({}, expect.anything(), mocks.resolver);
  expect(mocks.select).toHaveBeenCalledWith({ prompt: 'select_account' });
  expect(mocks.signOut).toHaveBeenCalledOnce();
});

it('cancelling the popup leaves no new Firebase session', async () => {
  mocks.popup.mockRejectedValue({ code: 'auth/popup-closed-by-user' });
  const choose = await new QueryClient().fetchQuery(googleAuthOptions);
  await expect(choose!()).rejects.toThrow('cancelled');
  expect(mocks.token).not.toHaveBeenCalled();
  expect(mocks.signOut).toHaveBeenCalledOnce();
});

it('does not offer the popup without Firebase configuration', async () => {
  mocks.get.mockResolvedValue({ data: {} });
  expect(await new QueryClient().fetchQuery(googleAuthOptions)).toBeNull();
  expect(mocks.load).not.toHaveBeenCalled();
});
