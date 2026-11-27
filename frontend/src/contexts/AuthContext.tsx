import { createContext, ReactNode, useCallback, useEffect, useState } from 'react';
import { authView, login as loginApi, register as registerApi } from '../api/authApi';
import { AuthResponse, LoginPayload, RegisterPayload, User } from '../types/auth';
import { getMe } from '../api/userApi';
import client, {
  activeMarker, beginSession, clearAccess, currentAccess, forgetSession, purgeLegacyTokens,
  renewSession, revokeBrowserSession, sessionMarker
} from '../api/client';
import { showToast } from '../components/common/Toast';
import { useQueryClient } from '@tanstack/react-query';

interface AuthContextValue {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
  hydrateUser: () => Promise<void>;
  setUser: (user: User | null) => void;
  acceptAuth: (response: AuthResponse) => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);



export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const queryClient = useQueryClient();
  const [user, setUserState] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const persistAuth = (response: AuthResponse) => {
    setToken(response.token);
    setUserState(response.user);
    beginSession(response.token);
  };

  const login = async (payload: LoginPayload) => {
    const response = await loginApi(payload);
    persistAuth(response);
  };

  const register = async (payload: RegisterPayload) => {
    const response = await registerApi(payload);
    persistAuth(response);
  };

  const logout = () => {
    const pushToken = localStorage.getItem('owner-push-token');
    const bearer = currentAccess();
    if (pushToken && bearer) {
      void client.post('/api/owner/push/unsubscribe', { token: pushToken },
        { headers: { Authorization: 'Bearer ' + bearer } }).catch(() => {});
    }
    localStorage.removeItem('owner-push-token');
    localStorage.removeItem('owner-push-user');
    const revocation = revokeBrowserSession();
    forgetSession();
    void revocation.catch(() => {
      showToast('Signed out on this device. The server could not confirm session revocation.', 'error');
    });
    queryClient.clear();
    setUserState(null);
    setToken(null);
  };

  const hydrateUser = useCallback(async () => {
    purgeLegacyTokens();
    if (!activeMarker()) {
      setIsLoading(false);
      return;
    }
    try {
      const bearer = currentAccess() || await renewSession();
      const profile = await getMe();
      if (bearer === currentAccess()) {
        setToken(bearer);
        setUserState(profile as User);
      }
    } catch {
      // The API interceptor handles a revoked cookie. A transient outage can be retried.
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const renewed = (event: Event) => {
      const response = authView((event as CustomEvent).detail);
      setToken(response.token);
      setUserState(response.user);
    };
    const expired = () => {
      clearAccess();
      queryClient.clear();
      setUserState(null);
      setToken(null);
    };
    const anotherTab = (event: StorageEvent) => {
      if (event.key !== sessionMarker) return;
      expired();
      if (event.newValue) void hydrateUser();
    };
    window.addEventListener('nirvana:renewed', renewed);
    window.addEventListener('nirvana:expired', expired);
    window.addEventListener('storage', anotherTab);
    void hydrateUser();
    return () => {
      window.removeEventListener('nirvana:renewed', renewed);
      window.removeEventListener('nirvana:expired', expired);
      window.removeEventListener('storage', anotherTab);
    };
  }, [queryClient, hydrateUser]);

  const value: AuthContextValue = {
    user,
    token,
    isLoading,
    login,
    register,
    logout,
    hydrateUser,
    setUser: setUserState,
    acceptAuth: persistAuth
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const getStoredToken = currentAccess;
