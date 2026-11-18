import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

/**
 * In development `/api` goes through this proxy to the API gateway on its compose host port, as
 * nginx does in compose; the gateway routes `/api/<service>` to each service. It sends no CORS
 * headers, so the browser must reach it on the Storefront's own origin.
 */
const gateway = 'http://localhost:8000'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': gateway },
  },
  // e2e/ holds the Playwright tests, run with `npm run test:e2e`.
  test: {
    projects: [
      // Domain logic, the API's helpers and the scripts, in Node.
      {
        extends: true,
        test: { name: 'unit', environment: 'node', include: ['src/**/*.test.ts', 'scripts/**/*.test.ts'] },
      },
      // Components and pages rendered in jsdom, with `fetch` answering for the services (src/test/).
      { extends: true, test: { name: 'component', environment: 'jsdom', include: ['src/**/*.test.tsx'] } },
    ],
  },
})
