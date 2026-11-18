import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { type Product, defaultVariant } from '../src/domain/catalog'

/**
 * Checks the compose stack before any test runs, so a service that died (say, killed for its
 * memory cap) is named up front rather than surfacing as connection errors in every test. Skipped
 * when `STOREFRONT_URL` aims the suite at a stack elsewhere.
 *
 * It checks that the Support Assistant runs its mock provider, whose replies Help's tests read: a
 * stack with an Anthropic API key mounted answers through Claude, in other words.
 *
 * Then checks that every seeded Product's default Variant has Stock left. Each run sells some, and
 * the tests take the first seeded Product in Stock: once one sells out, they reach a later one,
 * maybe an audio Product that Audio week discounts, and fail on a total or a Discount they don't
 * expect rather than saying why.
 */
export default async function globalSetup() {
  const baseUrl = process.env.STOREFRONT_URL ?? 'http://localhost:8080'
  if (!process.env.STOREFRONT_URL) {
    const root = fileURLToPath(new URL('../../..', import.meta.url))
    execFileSync('make', ['-s', 'status'], { cwd: root, stdio: 'inherit' })
    if (!assistantRunsMock(root)) {
      throw new Error(
        "The Support Assistant has an Anthropic API key, and Help's tests read its mock provider's replies. " +
          'Run it on the mock, from the repo root:\n' +
          '  : > /tmp/no-anthropic-api-key\n' +
          '  ANTHROPIC_API_KEY_FILE=/tmp/no-anthropic-api-key docker compose up -d --force-recreate --wait ai-support-assistant\n' +
          'and `docker compose up -d --force-recreate ai-support-assistant` after, for the key again.',
      )
    }
  }
  const soldOut = await soldOutSeededProducts(baseUrl)
  if (soldOut.length > 0) {
    throw new Error(
      `Earlier runs sold out the default Variant of ${soldOut.join(', ')}, so the tests would reach ` +
        'other Products than they expect. Run `make seed-reset` from the repo root first.',
    )
  }
}

/** Whether the Support Assistant's mounted key is empty, which runs the mock provider. */
function assistantRunsMock(root: string): boolean {
  const key = '/run/secrets/ecomm.support-assistant.anthropic-api-key'
  try {
    execFileSync('docker', ['compose', 'exec', '-T', 'ai-support-assistant', 'sh', '-c', `test ! -s ${key}`], {
      cwd: root,
      stdio: 'ignore',
    })
    return true
  } catch {
    return false
  }
}

async function soldOutSeededProducts(baseUrl: string): Promise<string[]> {
  const products = (await json(`${baseUrl}/api/catalog/products`)) as Product[]
  const soldOut: string[] = []
  for (const product of products) {
    // A test's own Products start with `E2E-`; one a test failed to delete may have no Stock.
    if (product.sku.startsWith('E2E-')) continue
    const variantId = defaultVariant(product).id
    const { quantity } = (await json(`${baseUrl}/api/inventory/stock/${encodeURIComponent(variantId)}`)) as {
      quantity: number
    }
    if (quantity <= 0) soldOut.push(product.sku)
  }
  return soldOut
}

async function json(url: string): Promise<unknown> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`GET ${url} answered ${response.status}`)
  return response.json()
}
