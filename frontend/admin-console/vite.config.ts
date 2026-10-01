import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

/**
 * In development `/api` goes through this proxy to the API gateway on its compose host port, as
 * nginx does in compose; the gateway routes `/api/<service>` to each service. It sends no CORS
 * headers, so the browser must reach it on the Admin Console's own origin.
 */
const gateway = 'http://localhost:8000'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    strictPort: true,
    proxy: { '/api': gateway },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
