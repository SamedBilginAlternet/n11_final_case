import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { api, tokenStore } from '../api/client.js';

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => tokenStore.getUser());
  const [loading, setLoading] = useState(false);

  const login = useCallback(async (email, password) => {
    setLoading(true);
    try {
      const { data } = await api.post('/api/auth/login', { email, password });
      // The backend embeds role in both the JWT and the user payload.
      // Reject non-admin logins client-side so we never even store the
      // tokens — the backend's @PreAuthorize would reject every call
      // anyway, but failing here gives a clear "yetkin yok" toast
      // instead of a stream of 403s.
      if (data.user?.role !== 'ADMIN') {
        toast.error('Bu hesap admin yetkisine sahip değil.');
        throw new Error('not-admin');
      }
      tokenStore.set(data);
      setUser(data.user);
      return data.user;
    } catch (err) {
      if (err.message !== 'not-admin') {
        const msg = err.response?.data?.message || 'Giriş başarısız.';
        toast.error(msg);
      }
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefresh();
    try {
      if (refreshToken) await api.post('/api/auth/logout', { refreshToken });
    } catch {
      // best-effort; we're clearing local state regardless
    }
    tokenStore.clear();
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout]);
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
