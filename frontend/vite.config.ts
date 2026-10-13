import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server is pinned to 5173 because the backend SecurityConfig CORS bean
// allows exactly http://localhost:5173 (and http://localhost:3000).
export default defineConfig({
  plugins: [react()],
  server: {
    // Bind IPv4 loopback explicitly. The Vite default ('localhost') resolves to
    // ::1 on this machine, so the server listened on IPv6 only and
    // http://127.0.0.1:5173 was refused - browsers that try IPv4 first (or reuse
    // an IPv4 socket) failed to load the app intermittently. http://localhost:5173
    // still resolves and works; this only adds the IPv4 address.
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
  },
});
