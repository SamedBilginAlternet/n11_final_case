import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { api, tokenStore, AUTH_EVENT, performRefresh } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => tokenStore.getUser());
  const [token, setToken] = useState(() => tokenStore.getAccess());
  const [loading, setLoading] = useState(false);
  const bootRef = useRef(false);

  // Keep React state in sync with tokenStore mutations from outside the context
  // (axios refresh interceptor, other tabs, /refresh rotation responses).
  useEffect(() => {
    const sync = () => {
      setToken(tokenStore.getAccess());
      setUser(tokenStore.getUser());
    };
    window.addEventListener(AUTH_EVENT, sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener(AUTH_EVENT, sync);
      window.removeEventListener('storage', sync);
    };
  }, []);

  // On first mount: if we don't have an in-memory access token but there's a
  // stored user (sticky UX hint that "this user was logged in"), try to
  // exchange the HttpOnly refresh cookie for a fresh access token. Silent —
  // failure just clears the user, which is what an expired session means.
  useEffect(() => {
    if (bootRef.current) return;
    bootRef.current = true;
    if (tokenStore.getAccess()) return;
    if (!tokenStore.getUser()) return;

    performRefresh().catch(() => {
      tokenStore.clear();
    });
  }, []);

  const applyTokenResponse = useCallback((data) => {
    tokenStore.set({ accessToken: data.accessToken, user: data.user });
    setToken(data.accessToken);
    setUser(data.user);
  }, []);

  const login = useCallback(async (email, password) => {
    setLoading(true);
    try {
      const { data } = await api.post('/api/auth/login', { email, password });
      applyTokenResponse(data);
      toast.success(`Hoş geldin ${data.user.fullName}`);
      return data.user;
    } catch (err) {
      const message = err.response?.data?.message || 'Giriş başarısız';
      toast.error(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, [applyTokenResponse]);

  const register = useCallback(async (payload) => {
    setLoading(true);
    try {
      await api.post('/api/auth/register', payload);
      toast.success('Hesap oluşturuldu, giriş yapabilirsin');
    } catch (err) {
      const message = err.response?.data?.message || 'Kayıt başarısız';
      toast.error(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      // Server-side: revokes the refresh row, clears the cookie. We send the
      // request even if we don't have an in-memory access token because the
      // cookie travels on its own.
      await api.post('/api/auth/logout');
    } catch {
      // Best-effort — even if the server is down, drop local credentials.
    }
    tokenStore.clear();
    setToken(null);
    setUser(null);
    toast('Çıkış yapıldı');
  }, []);

  const hydrateFromOAuth = useCallback(async ({ accessToken }) => {
    setLoading(true);
    tokenStore.set({ accessToken });
    setToken(accessToken);
    try {
      const { data } = await api.get('/api/users/me', {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      tokenStore.set({ user: data });
      setUser(data);
      toast.success(`Hoş geldin ${data.fullName}`);
      return data;
    } catch (err) {
      tokenStore.clear();
      setToken(null);
      setUser(null);
      const message = err.response?.data?.message || 'Oturum doğrulanamadı';
      toast.error(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{ user, token, loading, login, register, logout, hydrateFromOAuth, isAuthed: !!token }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
