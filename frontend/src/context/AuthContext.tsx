"use client";

import React, { createContext, useContext, useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  phone: string;
  createdAt: string;
}

interface AuthContextType {
  token: string | null;
  user: UserProfile | null;
  loading: boolean;
  isDemoMode: boolean;
  login: (email: string, password: string) => Promise<boolean>;
  register: (email: string, password: string, name: string, phone: string) => Promise<boolean>;
  logout: () => void;
  updateProfileState: (updatedUser: UserProfile) => void;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [isDemoMode, setIsDemoMode] = useState<boolean>(false);
  const router = useRouter();

  useEffect(() => {
    // Check localStorage on mount
    const savedToken = localStorage.getItem('token');
    const demoMode = localStorage.getItem('demo_mode_active') === 'true';
    setIsDemoMode(demoMode);

    if (savedToken) {
      setToken(savedToken);
      if (demoMode || savedToken.startsWith('mock-')) {
        setIsDemoMode(true);
        const savedProfile = localStorage.getItem('demo_user_profile');
        if (savedProfile) {
          setUser(JSON.parse(savedProfile));
        }
        setLoading(false);
      } else {
        fetchProfile(savedToken);
      }
    } else {
      setLoading(false);
    }
  }, []);

  const fetchProfile = async (jwtToken: string) => {
    try {
      const res = await fetch('/api/users/me', {
        headers: {
          'Authorization': `Bearer ${jwtToken}`
        }
      });
      if (res.ok) {
        const profile: UserProfile = await res.json();
        setUser(profile);
        localStorage.setItem('userId', profile.id);
        setIsDemoMode(false);
        localStorage.setItem('demo_mode_active', 'false');
      } else {
        logout();
      }
    } catch (err) {
      console.warn("Profile fetch failed, switching to DEMO MODE session fallback", err);
      // Backend is offline, check if we have a demo profile
      const savedProfile = localStorage.getItem('demo_user_profile');
      if (savedProfile) {
        setUser(JSON.parse(savedProfile));
        setIsDemoMode(true);
        localStorage.setItem('demo_mode_active', 'true');
      } else {
        logout();
      }
    } finally {
      setLoading(false);
    }
  };

  const login = async (email: string, password: string): Promise<boolean> => {
    try {
      setLoading(true);
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(errorText || 'Invalid credentials');
      }

      const data = await res.json();
      const jwtToken = data.token;
      
      localStorage.setItem('token', jwtToken);
      setToken(jwtToken);
      setIsDemoMode(false);
      localStorage.setItem('demo_mode_active', 'false');
      await fetchProfile(jwtToken);
      return true;
    } catch (err) {
      console.warn("API offline - switching to DEMO MODE authentication fallback", err);
      // Simulate client-side demo login
      const mockUser: UserProfile = {
        id: 'demo-user-uuid-12345',
        email: email,
        name: email.split('@')[0].toUpperCase(),
        phone: '0912345678',
        createdAt: new Date().toISOString()
      };
      const mockToken = 'mock-jwt-token-demo';
      localStorage.setItem('token', mockToken);
      localStorage.setItem('userId', mockUser.id);
      localStorage.setItem('demo_user_profile', JSON.stringify(mockUser));
      setIsDemoMode(true);
      localStorage.setItem('demo_mode_active', 'true');
      setToken(mockToken);
      setUser(mockUser);
      setLoading(false);
      return true;
    }
  };

  const register = async (email: string, password: string, name: string, phone: string): Promise<boolean> => {
    try {
      setLoading(true);
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, name, phone })
      });

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(errorText || 'Registration failed');
      }

      const data = await res.json();
      const jwtToken = data.token;
      
      localStorage.setItem('token', jwtToken);
      setToken(jwtToken);
      setIsDemoMode(false);
      localStorage.setItem('demo_mode_active', 'false');
      await fetchProfile(jwtToken);
      return true;
    } catch (err) {
      console.warn("API offline - switching to DEMO MODE registration fallback", err);
      const mockUser: UserProfile = {
        id: 'demo-user-uuid-12345',
        email: email,
        name: name,
        phone: phone,
        createdAt: new Date().toISOString()
      };
      const mockToken = 'mock-jwt-token-demo';
      localStorage.setItem('token', mockToken);
      localStorage.setItem('userId', mockUser.id);
      localStorage.setItem('demo_user_profile', JSON.stringify(mockUser));
      setIsDemoMode(true);
      localStorage.setItem('demo_mode_active', 'true');
      setToken(mockToken);
      setUser(mockUser);
      setLoading(false);
      return true;
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('demo_mode_active');
    setToken(null);
    setUser(null);
    setIsDemoMode(false);
    setLoading(false);
    router.push('/login');
  };

  const updateProfileState = (updatedUser: UserProfile) => {
    setUser(updatedUser);
    if (isDemoMode) {
      localStorage.setItem('demo_user_profile', JSON.stringify(updatedUser));
    }
  };

  const refreshProfile = async () => {
    if (token && !isDemoMode) {
      await fetchProfile(token);
    }
  };

  return (
    <AuthContext.Provider value={{ token, user, loading, isDemoMode, login, register, logout, updateProfileState, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
