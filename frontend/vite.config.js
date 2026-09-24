import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  // Load env variables so we can use them in the config itself (e.g. proxy target).
  // loadEnv reads .env, .env.local, .env.[mode], .env.[mode].local — highest priority last.
  const env = loadEnv(mode, process.cwd(), '')

  const masterUrl = env.VITE_MASTER_URL || 'http://localhost:9090'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        // All /api/* requests in dev are forwarded to the Master backend.
        // Change VITE_MASTER_URL in .env.local to point to a different lab PC.
        '/api': {
          target: masterUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
