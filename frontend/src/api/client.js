import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';
const root = baseURL.endsWith('/api') ? baseURL.slice(0, -4) : baseURL;

export const api = axios.create({ baseURL: root });

// Separate axios instance for the /refresh call so a 401 from /refresh itself
// can't recursively re-enter the response interceptor and stack overflow.
const refreshClient = axios.create({ baseURL: root });

const ACCESS_KEY = 'n11.token';
const REFRESH_KEY = 'n11.refreshToken';
const USER_KEY = 'n11.user';

export const AUTH_EVENT = 'n11:auth-change';

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
    window.dispatchEvent(new CustomEvent(AUTH_EVENT));
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    window.dispatchEvent(new CustomEvent(AUTH_EVENT));
  },
};

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccess();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Coalesces concurrent refresh attempts into a single network round-trip.
// Without this, a page that fires 5 requests at boot would burn 5 refresh
// tokens (and our reuse-detection would nuke the family).
let refreshPromise = null;

async function performRefresh() {
  if (refreshPromise) return refreshPromise;
  const refreshToken = tokenStore.getRefresh();
  if (!refreshToken) throw new Error('no_refresh_token');

  refreshPromise = refreshClient
    .post('/api/auth/refresh', { refreshToken })
    .then(({ data }) => {
      tokenStore.set({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        user: data.user,
      });
      return data.accessToken;
    })
    .finally(() => {
      refreshPromise = null;
    });

  return refreshPromise;
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const url = original?.url || '';

    // Skip refresh logic for the auth endpoints themselves and for already-retried requests
    const isAuthEndpoint = url.includes('/api/auth/login')
      || url.includes('/api/auth/register')
      || url.includes('/api/auth/refresh')
      || url.includes('/api/auth/logout');

    if (status === 401 && original && !original._retry && !isAuthEndpoint && tokenStore.getRefresh()) {
      original._retry = true;
      try {
        const newAccess = await performRefresh();
        original.headers = original.headers || {};
        original.headers.Authorization = `Bearer ${newAccess}`;
        return api(original);
      } catch {
        tokenStore.clear();
        return Promise.reject(error);
      }
    }

    if (status === 401 && isAuthEndpoint && url.includes('/api/auth/refresh')) {
      // Refresh itself failed → session is dead, clear everything.
      tokenStore.clear();
    }

    return Promise.reject(error);
  },
);
