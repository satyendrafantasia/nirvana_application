import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import { login as loginApi, register as registerApi, getMe } from '../api/authApi';
import { AuthResponse, User } from '../types/auth';

interface AuthContextValue {
  user?: User;
  token?: string;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string, phone?: string) => Promise<void>;
  logout: () => Promise<void>;
  setUser: (user: User) => void;
}

export const AuthContext = createContext<AuthContextValue>({
  isLoading: true,
  login: async () => undefined,
  register: async () => undefined,
  logout: async () => undefined,
  setUser: () => undefined
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUserState] = useState<User | undefined>();
  const [token, setToken] = useState<string | undefined>();
  const [isLoading, setIsLoading] = useState(true);

  const persistAuth = useCallback(async (auth: AuthResponse) => {
    setUserState(auth.user);
    setToken(auth.token);
    await AsyncStorage.setItem('auth_token', auth.token);
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const auth = await loginApi({ email, password });
      await persistAuth(auth);
    },
    [persistAuth]
  );

  const register = useCallback(
    async (name: string, email: string, password: string, phone?: string) => {
      const auth = await registerApi({ name, email, password, phone });
      await persistAuth(auth);
    },
    [persistAuth]
  );

  const logout = useCallback(async () => {
    await AsyncStorage.removeItem('auth_token');
    setUserState(undefined);
    setToken(undefined);
  }, []);

  const loadSession = useCallback(async () => {
    try {
      const storedToken = await AsyncStorage.getItem('auth_token');
      if (storedToken) {
        const profile = await getMe();
        setUserState(profile.user);
        setToken(storedToken);
      }
    } catch (error) {
      await AsyncStorage.removeItem('auth_token');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSession();
  }, [loadSession]);

  const value = useMemo(
    () => ({
      user,
      token,
      isLoading,
      login,
      register,
      logout,
      setUser: setUserState
    }),
    [isLoading, login, logout, register, token, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
