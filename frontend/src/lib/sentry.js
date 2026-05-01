import * as Sentry from '@sentry/react';

/**
 * Bootstraps Sentry error reporting + session replay for the storefront.
 *
 * No-ops when VITE_SENTRY_DSN is missing so dev / preview / CI builds
 * stay silent.  Tracing is intentionally not enabled here — backend
 * traces flow into Jaeger and we don't want to double-bill or fragment
 * the trace view.  Replay sample rates lean conservative to stay inside
 * the free tier (50 replays/month).
 *
 * Source maps are uploaded by sentryVitePlugin in vite.config.js during
 * the production build, so stack traces in Sentry's Issues view show
 * real file:line references instead of minified `e.t.handleClick`.
 */
export function initSentry() {
  const dsn = import.meta.env.VITE_SENTRY_DSN;
  if (!dsn) return;

  Sentry.init({
    dsn,
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || 'production',
    release: import.meta.env.VITE_SENTRY_RELEASE || 'dev',
    sendDefaultPii: false,
    integrations: [
      // Replay's masking defaults are aggressive — text blocked, inputs
      // masked.  Good for compliance-by-default; if you want to see
      // actual click targets in the replay enable maskAllText: false
      // here once you've reviewed PII implications.
      Sentry.replayIntegration({
        maskAllText: true,
        blockAllMedia: true,
      }),
    ],
    // Errors only — see lib/sentry.js header for rationale.
    tracesSampleRate: 0.0,
    // Capture replay on every error session, sample 10% of sessions
    // generally so we have context for *why* an error happened (clicks
    // leading up to the throw).  Adjust upward when more replay quota
    // becomes available.
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
    initialScope: {
      tags: { service: 'frontend' },
    },
  });
}

/**
 * Sets the active user on Sentry's scope.  Call after login (any flow —
 * email, Google, phone) so subsequent events carry the user identity.
 * Pass null on logout to clear; otherwise scope leaks across sessions.
 */
export function setSentryUser(user) {
  if (!user) {
    Sentry.setUser(null);
    return;
  }
  Sentry.setUser({
    id: String(user.id),
    email: user.email || undefined,
    username: user.fullName || user.phoneNumber || undefined,
  });
}

export { Sentry };
