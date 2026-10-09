import React, { createContext, useContext, useState, useEffect } from 'react';
import { User, UserRole } from '../types';
import { api } from '../api/client';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, fullName: string, role: UserRole, phone?: string) => Promise<void>;
  logout: () => void;
  switchRoleQuick: (role: UserRole) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem('resqhunt_token'));
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const initAuth = async () => {
      const storedToken = localStorage.getItem('resqhunt_token');
      if (storedToken) {
        try {
          const profile = await api.getMe();
          setUser(profile);
        } catch (err) {
          console.warn('Stored token expired or invalid:', err);
          localStorage.removeItem('resqhunt_token');
          setToken(null);
          setUser(null);
        }
      } else {
        // Auto-login as default coordinator for instant demo experience if nothing set
        try {
          const res = await api.login('coordinator@resqhunt.org', 'Password123!');
          localStorage.setItem('resqhunt_token', res.token);
          setToken(res.token);
          setUser(res.user);
        } catch {
          // If backend isn't seeded yet, leave unauthenticated
        }
      }
      setIsLoading(false);
    };

    initAuth();
  }, []);

  const login = async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const res = await api.login(email, password);
      localStorage.setItem('resqhunt_token', res.token);
      setToken(res.token);
      setUser(res.user);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (
    email: string,
    password: string,
    fullName: string,
    role: UserRole,
    phone?: string
  ) => {
    setIsLoading(true);
    try {
      const res = await api.register(email, password, fullName, role, phone);
      localStorage.setItem('resqhunt_token', res.token);
      setToken(res.token);
      setUser(res.user);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem('resqhunt_token');
    setToken(null);
    setUser(null);
  };

  const switchRoleQuick = async (role: UserRole) => {
    let email = 'coordinator@resqhunt.org';
    if (role === 'VOLUNTEER') email = 'volunteer1@resqhunt.org';
    if (role === 'CITIZEN') email = 'citizen@resqhunt.org';
    await login(email, 'Password123!');
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isLoading,
        login,
        register,
        logout,
        switchRoleQuick
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};
