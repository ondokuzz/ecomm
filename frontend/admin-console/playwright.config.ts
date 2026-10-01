import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against the full compose stack (`make up` from the repo root), which must
 * already be running: the Admin Console on :8090, the Storefront on :8080 and Keycloak on :8180.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.ADMIN_CONSOLE_URL ?? 'http://localhost:8090',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
