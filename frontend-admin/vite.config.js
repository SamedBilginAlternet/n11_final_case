import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Admin panel runs on a separate port (3001) so it's clearly distinct
// from the storefront and can be deployed independently.  Dev proxy
// forwards /api/* to the api-gateway on 8080 — same convention as the
// public frontend, so the same axios baseURL works in both.
export default defineConfig({
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
