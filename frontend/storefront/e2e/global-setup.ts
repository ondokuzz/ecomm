import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { type Product, defaultVariant } from '../src/domain/catalog'

/**
 * Checks the compose stack before any test runs, so a service that died (say, killed for its
 * memory cap) is named up front rather than surfacing as connection errors in every test. Skipped
 * when `STOREFRONT_URL` aims the suite at a stack elsewhere.
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
  }
  const soldOut = await soldOutSeededProducts(baseUrl)
  if (soldOut.length > 0) {
    throw new Error(
      `Earlier runs sold out the default Variant of ${soldOut.join(', ')}, so the tests would reach ` +
        'other Products than they expect. Run `make seed-reset` from the repo root first.',
    )
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
