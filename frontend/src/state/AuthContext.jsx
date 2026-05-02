import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { api, tokenStore, AUTH_EVENT, performRefresh } from '../api/client.js';
import { setSentryUser } from '../lib/sentry.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => tokenStore.getUser());
  const [token, setToken] = useState(() => tokenStore.getAccess());
  const [loading, setLoading] = useState(false);
  // True while the silent /refresh exchange is in flight on first mount.
  // Email links land in a fresh tab where sessionStorage is empty, so the
  // access token is null until /refresh resolves; without gating, every
  // ProtectedRoute would redirect to /login before the cookie-backed
  // session is restored.  Initial value mirrors the boot-effect guard:
  // we only need to wait if there's a sticky user but no access token yet.
  const [booting, setBooting] = useState(
    () => !tokenStore.getAccess() && !!tokenStore.getUser(),
  );
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

  // Mirror auth state into Sentry's scope so any captured event going
  // forward carries the user identity.  Cleared on logout — otherwise
  // a fresh anonymous session would inherit the previous user's id.
  useEffect(() => {
    setSentryUser(user);
  }, [user]);

  // On first mount: if we don't have an in-memory access token but there's a
  // stored user (sticky UX hint that "this user was logged in"), try to
  // exchange the HttpOnly refresh cookie for a fresh access token. Silent —
  // failure just clears the user, which is what an expired session means.
  useEffect(() => {
    if (bootRef.current) return;
    bootRef.current = true;
    if (tokenStore.getAccess()) return;
    if (!tokenStore.getUser()) {
      setBooting(false);
      return;
    }

    performRefresh()
      .catch(() => {
        tokenStore.clear();
      })
      .finally(() => {
        setBooting(false);
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

  // Called after the Firebase Phone Auth flow has handed us a verified ID
  // token; the backend trades it for our own JWT pair.  Identical session
  // shape to email login so the rest of the app doesn't care which channel
  // produced the token.
  const loginWithPhone = useCallback(async (idToken) => {
    setLoading(true);
    try {
      const { data } = await api.post('/api/auth/login/phone', { idToken });
      applyTokenResponse(data);
      const greeting = data.user.fullName || data.user.phoneNumber || 'tekrar';
      toast.success(`Hoş geldin ${greeting}`);
      return data.user;
    } catch (err) {
      const message = err.response?.data?.message || 'Telefonla giriş başarısız';
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
      value={{ user, token, loading, booting, login, loginWithPhone, register, logout, hydrateFromOAuth, isAuthed: !!token }}
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
