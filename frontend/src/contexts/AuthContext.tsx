import { createContext, ReactNode, useCallback, useEffect, useState } from 'react';
import { login as loginApi, register as registerApi } from '../api/authApi';
import { AuthResponse, LoginPayload, RegisterPayload, User } from '../types/auth';
import { getMe } from '../api/userApi';
import client from '../api/client';
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

const TOKEN_KEY = 'nirvana_token';

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const queryClient = useQueryClient();
  const [user, setUserState] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const persistAuth = (response: AuthResponse) => {
    setToken(response.token);
    setUserState(response.user);
    localStorage.setItem(TOKEN_KEY, response.token);
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
    const refreshToken = localStorage.getItem('nirvana_refresh');
    if (refreshToken) void client.post('/api/auth/logout', { refreshToken }).catch(() => {});
    localStorage.removeItem('nirvana_refresh');
    queryClient.clear();
    setUserState(null);
    setToken(null);
    localStorage.removeItem(TOKEN_KEY);
  };

  const hydrateUser = useCallback(async () => {
    const storedToken = localStorage.getItem(TOKEN_KEY);
    if (!storedToken) {
      setIsLoading(false);
      return;
    }
    setToken(storedToken);
    if (user) {
      setIsLoading(false);
      return;
    }
    try {
      const profile = await getMe();
      setUserState(profile as User);
    } catch (error) {
      console.error('Failed to hydrate user', error);
      logout();
    } finally {
      setIsLoading(false);
    }
  }, [user]);

  useEffect(() => {
    hydrateUser();
  }, [hydrateUser]);

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

export const getStoredToken = () => localStorage.getItem(TOKEN_KEY);
