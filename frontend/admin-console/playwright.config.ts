import { defineConfig, devices } from '@playwright/test'

// Each local run keeps its own results, so a flaky failure's trace survives the next run. Workers
// load this file again, so the name is set once, in the environment they inherit.
process.env.E2E_RUN ??= new Date().toISOString().replace(/[:.]/g, '-')

/**
 * End-to-end tests against the full compose stack (`make up` from the repo root), which must
 * already be running: the Admin Console on :8090, the Storefront on :8080 and Keycloak on :8180.
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  forbidOnly: !!process.env.CI,
  outputDir: process.env.CI ? 'test-results' : `test-results/${process.env.E2E_RUN}`,
  reporter: [[process.env.CI ? 'list' : 'line'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.ADMIN_CONSOLE_URL ?? 'http://localhost:8090',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
