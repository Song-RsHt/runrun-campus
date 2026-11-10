import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const port = Number(loadEnv(mode, '.', 'RRC_').RRC_ADMIN_PORT ?? 5173)
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('RRC_ADMIN_PORT must be between 1 and 65535')
  return {
    plugins: [react()],
    preview: { port, strictPort: true },
    server: {
      port,
      strictPort: true,
      proxy: {
        '/api': 'http://127.0.0.1:8080',
      },
    },
  }
})
