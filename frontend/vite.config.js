import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { sentryVitePlugin } from '@sentry/vite-plugin';

// Source-map upload runs only when SENTRY_AUTH_TOKEN is present at build
// time — local dev builds skip it (no token, no upload, no error) and
// the CI build path (deploy.yml passes the token from repo secrets) gets
// readable stack traces in Sentry's Issues view instead of minified
// `e.t.handleClick` frames.
const sentryAuthToken = process.env.SENTRY_AUTH_TOKEN;

export default defineConfig({
  // Sourcemaps must be emitted for the plugin to upload them.  After
  // upload the plugin deletes the .map files from dist/ so end users
  // never download them — only Sentry has the symbol info.
  build: { sourcemap: true },
  plugins: [
    react(),
    ...(sentryAuthToken
      ? [
          sentryVitePlugin({
            authToken: sentryAuthToken,
            org: process.env.SENTRY_ORG || 'samo-1v2',
            project: process.env.SENTRY_PROJECT || 'n11-frontend',
            release: { name: process.env.VITE_SENTRY_RELEASE },
            sourcemaps: { filesToDeleteAfterUpload: ['./dist/**/*.map'] },
          }),
        ]
      : []),
  ],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
  },
});
