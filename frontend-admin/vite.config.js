import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Admin panel runs on a separate port (3001) so it's clearly distinct
// from the storefront and can be deployed independently.  Dev proxy
// forwards /api/* to the api-gateway on 8080 — same convention as the
// public frontend, so the same axios baseURL works in both.
export default defineConfig({
  // Path-based deployment: production serves the panel under
  // https://<domain>/admin/.  Vite needs to know so asset URLs in the
  // built index.html ('/admin/assets/...') line up with the public path
  // Caddy strips before proxying.  Local dev (npm run dev on :3001)
  // still serves at root — Vite's base is honoured for build, not
  // dev server, so http://localhost:3001 keeps working.
  base: '/admin/',
  plugins: [react()],
  server: {
    port: 3001,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
