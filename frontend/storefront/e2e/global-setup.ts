import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

/**
 * Checks the compose stack before any test runs, so a service that died (say, killed for its
 * memory cap) is named up front rather than surfacing as connection errors in every test. Skipped
 * when `STOREFRONT_URL` aims the suite at a stack elsewhere.
 */
export default function globalSetup() {
  if (process.env.STOREFRONT_URL) return
  const root = fileURLToPath(new URL('../../..', import.meta.url))
  execFileSync('make', ['-s', 'status'], { cwd: root, stdio: 'inherit' })
}
