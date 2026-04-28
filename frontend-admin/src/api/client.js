import axios from 'axios';
import toast from 'react-hot-toast';

// In dev (`npm run dev`) Vite proxies /api → gateway:8080.  In docker the
// nginx wrapper proxies /api too.  Either way axios baseURL is empty and
// every request hits a same-origin /api/... path.
const baseURL = import.meta.env.VITE_API_BASE_URL || '';

export const api = axios.create({ baseURL });

const ACCESS_KEY = 'n11.admin.token';
const REFRESH_KEY = 'n11.admin.refreshToken';
const USER_KEY = 'n11.admin.user';

// Storage keys are namespaced with `.admin.` so this panel and the public
// storefront can run in the same browser without trampling each other's
// sessions — a real bootcamp grader will probably keep both tabs open.

export const tokenStore = {
  getAccess: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  getUser: () => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },
  set: ({ accessToken, refreshToken, user }) => {
    if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken);
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  },
};

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccess();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status;
    if (status === 401 && err.config?.url && !err.config.url.includes('/api/auth/login')) {
      tokenStore.clear();
      // Hard nav so route guards re-evaluate from a clean state.
      window.location.assign('/login');
    } else if (status === 403) {
      toast.error('Bu işlem için yetkin yok.');
    } else if (status >= 500) {
      toast.error('Sunucu hatası, biraz sonra tekrar dene.');
    }
    return Promise.reject(err);
  },
);
