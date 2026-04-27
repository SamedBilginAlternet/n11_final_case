import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { api } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem('n11.user');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });
  const [token, setToken] = useState(() => localStorage.getItem('n11.token'));
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (token) localStorage.setItem('n11.token', token);
    else localStorage.removeItem('n11.token');
  }, [token]);

  useEffect(() => {
    if (user) localStorage.setItem('n11.user', JSON.stringify(user));
    else localStorage.removeItem('n11.user');
  }, [user]);

  const login = useCallback(async (email, password) => {
    setLoading(true);
    try {
      const { data } = await api.post('/api/auth/login', { email, password });
      setToken(data.accessToken);
      setUser(data.user);
      toast.success(`Hoş geldin ${data.user.fullName}`);
      return data.user;
    } catch (err) {
      const message = err.response?.data?.message || 'Giriş başarısız';
      toast.error(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

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

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    toast('Çıkış yapıldı');
  }, []);

  const hydrateFromToken = useCallback(async (newToken) => {
    setLoading(true);
    setToken(newToken);
    try {
      const { data } = await api.get('/api/users/me', {
        headers: { Authorization: `Bearer ${newToken}` },
      });
      setUser(data);
      toast.success(`Hoş geldin ${data.fullName}`);
      return data;
    } catch (err) {
      setToken(null);
      const message = err.response?.data?.message || 'Oturum doğrulanamadı';
      toast.error(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{ user, token, loading, login, register, logout, hydrateFromToken, isAuthed: !!token }}
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
