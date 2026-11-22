import { queryOptions, useQuery } from '@tanstack/react-query';
import client from './client';
import { loadFirebase, PhoneCodeConfig } from './phoneCodes';

export const googleAuthOptions = queryOptions({
  queryKey: ['firebase-google-config'],
  staleTime: Infinity,
  queryFn: async () => {
    const { data } = await client.get<NonNullable<PhoneCodeConfig['firebase']>>(
      '/api/auth/firebase/config'
    );
    if (!data.apiKey) return null;
    const { auth, sdk } = await loadFirebase(data);
    return async () => {
      const provider = new sdk.GoogleAuthProvider();
      provider.setCustomParameters({ prompt: 'select_account' });
      try {
        const result = await sdk.signInWithPopup(auth, provider, sdk.browserPopupRedirectResolver);
        return await result.user.getIdToken();
      } catch (error) {
        const code = (error as { code?: string }).code;
        if (code === 'auth/operation-not-allowed')
          throw new Error('Enable Google in Firebase Authentication to use this sign-in method.');
        if (code === 'auth/unauthorized-domain')
          throw new Error('This site must be added to Firebase Authentication authorized domains.');
        if (code === 'auth/popup-blocked')
          throw new Error('Allow the Google sign-in popup and try again.');
        if (code === 'auth/popup-closed-by-user') throw new Error('Google sign-in was cancelled.');
        throw error;
      } finally {
        await sdk.signOut(auth);
      }
    };
  }
});

export function useGoogleAuth() {
  return useQuery(googleAuthOptions);
}
