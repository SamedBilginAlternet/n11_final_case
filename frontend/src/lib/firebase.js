import { initializeApp, getApps } from 'firebase/app';
import { getAuth } from 'firebase/auth';

/**
 * Lazy Firebase Auth bootstrap.  Returns null when the build wasn't given
 * VITE_FIREBASE_* values — callers fall back to email/Google login UI in
 * that case so dev/preview builds don't blow up just because Firebase
 * wasn't wired.
 *
 * The Firebase Web config is technically public (it ships in the bundle),
 * so we don't bother encrypting it — the actual auth boundary is on the
 * backend, which verifies the ID token against Google's JWKS.
 */
const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
};

export const isFirebaseConfigured = Boolean(config.apiKey && config.authDomain && config.projectId);

let cachedAuth = null;

export function getFirebaseAuth() {
  if (!isFirebaseConfigured) return null;
  if (cachedAuth) return cachedAuth;
  const app = getApps().length ? getApps()[0] : initializeApp(config);
  cachedAuth = getAuth(app);
  return cachedAuth;
}
