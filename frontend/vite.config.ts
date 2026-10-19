import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      ['/api', '/oauth2', '/login/oauth2'].map((path) => [
        path,
        { target: process.env.API_PROXY_TARGET || 'http://localhost:8081', changeOrigin: false }
      ])
    )
  }
});
