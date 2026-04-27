import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { api, tokenStore, AUTH_EVENT } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => tokenStore.getUser());
  const [token, setToken] = useState(() => tokenStore.getAccess());
  const [loading, setLoading] = useState(false);

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

  const applyTokenResponse = useCallback((data) => {
    tokenStore.set({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: data.user,
    });
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
    const refreshToken = tokenStore.getRefresh();
    if (refreshToken) {
      try {
        await api.post('/api/auth/logout', { refreshToken });
      } catch {
        // Best-effort — even if the server is down, drop local credentials.
      }
    }
    tokenStore.clear();
    setToken(null);
    setUser(null);
    toast('Çıkış yapıldı');
  }, []);

  const hydrateFromOAuth = useCallback(async ({ accessToken, refreshToken }) => {
    setLoading(true);
    tokenStore.set({ accessToken, refreshToken });
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
