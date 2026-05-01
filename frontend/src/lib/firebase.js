/**
 * Lazy Firebase Auth bootstrap.
 *
 * The Firebase JS SDK is ~250 kB minified — pulling it into every page
 * load wrecks LCP for users who never touch the login flow.  We import
 * it dynamically inside {@link getFirebaseAuth} so Vite emits a separate
 * chunk that only downloads when something actually calls into Firebase.
 *
 * The {@link isFirebaseConfigured} flag stays synchronous (just env var
 * checks) so callers can decide whether to render phone-login UI without
 * paying for the SDK download up front.
 */
const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
};

export const isFirebaseConfigured = Boolean(config.apiKey && config.authDomain && config.projectId);

let cachedAuth = null;
let initPromise = null;

export async function getFirebaseAuth() {
  if (!isFirebaseConfigured) return null;
  if (cachedAuth) return cachedAuth;
  if (!initPromise) {
    initPromise = (async () => {
      const [{ initializeApp, getApps }, { getAuth }] = await Promise.all([
        import('firebase/app'),
        import('firebase/auth'),
      ]);
      const app = getApps().length ? getApps()[0] : initializeApp(config);
      cachedAuth = getAuth(app);
      return cachedAuth;
    })();
  }
  return initPromise;
}

// Convenience re-export for the few call sites that need RecaptchaVerifier
// / signInWithPhoneNumber — keeps the dynamic-import boilerplate in one
// place and ensures these symbols ride the same chunk as firebase/auth.
export async function loadFirebaseAuthFns() {
  const mod = await import('firebase/auth');
  return { RecaptchaVerifier: mod.RecaptchaVerifier, signInWithPhoneNumber: mod.signInWithPhoneNumber };
}
