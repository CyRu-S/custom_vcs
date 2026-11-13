import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'CVCS_')
  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      // Same-origin requests let the browser use HttpOnly cookies without permissive CORS.
      proxy: {
        '/api': {
          target: process.env.CVCS_API_TARGET ?? env.CVCS_API_TARGET ?? 'http://127.0.0.1:8080',
        },
      },
    },
  }
})
