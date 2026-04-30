import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';
// `apiRoot` is the origin without the `/api` suffix — axios prepends `/api/...`
// itself in every call, so the instance baseURL must NOT include `/api` or
// requests would land on `/api/api/...` (real bug we hit before).  Exported so
// non-axios callers (OAuth redirect, file downloads, server-rendered links)
// can build URLs the same way without re-deriving the strip rule.
export const apiRoot = baseURL.endsWith('/api') ? baseURL.slice(0, -4) : baseURL;
const root = apiRoot;

// withCredentials: required so the browser attaches the HttpOnly refresh
// cookie on /api/auth/refresh and /api/auth/logout. The cookie is scoped to
// /api/auth so it doesn't leak onto every other request.
export const api = axios.create({ baseURL: root, withCredentials: true });

// Separate axios instance for the /refresh call so a 401 from /refresh itself
// can't recursively re-enter the response interceptor and stack overflow.
const refreshClient = axios.create({ baseURL: root, withCredentials: true });

const ACCESS_KEY = 'n11.token';
const USER_KEY = 'n11.user';

export const AUTH_EVENT = 'n11:auth-change';

// Access token lives in module memory only — page reload means we ask the
// /refresh endpoint for a new one (the HttpOnly cookie is still there). This
// is the half of the OWASP recommendation that pairs with the cookie: even if
// XSS executes, it can't read the access token off localStorage and use it
// to impersonate the user from the attacker's machine.
let accessTokenInMemory = null;

export const tokenStore = {
  getAccess: () => accessTokenInMemory,
  getUser: () => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },
  set: ({ accessToken, user }) => {
    if (accessToken !== undefined) {
      accessTokenInMemory = accessToken;
      // Mirror to sessionStorage only so a tab-internal route change can
      // pick the token back up without going through /refresh again.
      // sessionStorage is cleared when the tab closes; not shared across tabs.
      if (accessToken) sessionStorage.setItem(ACCESS_KEY, accessToken);
      else sessionStorage.removeItem(ACCESS_KEY);
    }
    if (user !== undefined) {
      if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
      else localStorage.removeItem(USER_KEY);
    }
    window.dispatchEvent(new CustomEvent(AUTH_EVENT));
  },
  clear: () => {
    accessTokenInMemory = null;
    sessionStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(USER_KEY);
    window.dispatchEvent(new CustomEvent(AUTH_EVENT));
  },
};

// On boot, if the same tab still has an access token in sessionStorage (set
// during this session), restore it. This avoids an extra /refresh on every
// in-tab navigation while still losing the token when the tab is closed.
const cachedAccess = sessionStorage.getItem(ACCESS_KEY);
if (cachedAccess) accessTokenInMemory = cachedAccess;

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

export async function performRefresh() {
  if (refreshPromise) return refreshPromise;

  refreshPromise = refreshClient
    .post('/api/auth/refresh')
    .then(({ data }) => {
      tokenStore.set({ accessToken: data.accessToken, user: data.user });
      return data.accessToken;
    })
    .finally(() => {
      refreshPromise = null;
    });

  return refreshPromise;
}

// Backend returns RFC 9457 Problem Details (Content-Type: application/problem+json)
// with `detail` as the human-readable message. Existing UI code reads
// `err.response?.data?.message`, so we normalize the shape on the way in:
// callers don't need to know whether the body is RFC 9457 or a legacy
// envelope. Idempotent — running twice is a no-op.
function normalizeErrorBody(data) {
  if (!data || typeof data !== 'object') return;
  if (!data.message && (data.detail || data.title)) {
    data.message = data.detail || data.title;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    normalizeErrorBody(error.response?.data);
    const original = error.config;
    const status = error.response?.status;
    const url = original?.url || '';

    // Skip refresh logic for the auth endpoints themselves and for already-retried requests
    const isAuthEndpoint = url.includes('/api/auth/login')
      || url.includes('/api/auth/register')
      || url.includes('/api/auth/refresh')
      || url.includes('/api/auth/logout');

    // Backend returns 401 when no auth header reaches the filter, but 403
    // when an *expired* JWT is presented (the anonymous-auth filter promotes
    // the request to anonymous before authorization runs).  Both cases mean
    // "token is dead, try to refresh" — discriminating is the gateway's job,
    // not ours.
    if ((status === 401 || status === 403) && original && !original._retry && !isAuthEndpoint) {
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

    if (status === 401 && url.includes('/api/auth/refresh')) {
      // Refresh itself failed → cookie is dead, clear local state.
      tokenStore.clear();
    }

    return Promise.reject(error);
  },
);
