import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import { type Product, defaultVariant } from '../src/domain/catalog'

/**
 * Every page fails gracefully: unknown addresses show the Not Found page, a failed request shows the
 * error panel with its support reference, and an expired sign-in goes to Keycloak and comes back.
 */

const demoCustomer = { email: 'demo@ecomm.local', password: 'demo' }

test('unknown routes, Products and Orders show the Not Found page', async ({ page }) => {
  await page.goto('/no-such-page')
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible()

  await page.goto('/products/NO-SUCH-SKU')
  await expect(page.getByRole('heading', { name: 'Product not found' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Back to products' })).toBeVisible()

  await page.goto('/orders/00000000-0000-0000-0000-000000000000')
  await signInOnKeycloak(page)
  await expect(page.getByRole('heading', { name: 'Order not found' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'All my orders' })).toBeVisible()
  // Order Management answers an ID that isn't a UUID the same way.
  await page.goto('/orders/not-an-order')
  await expect(page.getByRole('heading', { name: 'Order not found' })).toBeVisible()
})

test('a failed request shows the error panel with its reference, and "Try again" reloads it', async ({ page }) => {
  await page.route('**/api/catalog/products', (route) =>
    route.fulfill({
      status: 500,
      contentType: 'application/problem+json',
      headers: { 'X-Correlation-Id': 'e2e-reference-1' },
      body: JSON.stringify({ title: 'Internal Server Error', status: 500, correlationId: 'e2e-reference-1' }),
    }),
  )
  await page.goto('/')
  const panel = page.getByRole('alert').filter({ hasText: "We couldn't load the products" })
  await expect(panel).toContainText('Something went wrong on our side')
  await expect(panel).toContainText('Reference: e2e-reference-1')

  await page.unroute('**/api/catalog/products')
  await panel.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByRole('list', { name: 'Products' }).getByRole('listitem').first()).toBeVisible()
  await expect(panel).toBeHidden()
})

test('a lookup beside the main content that fails says so, with its reference', async ({ page, request }) => {
  const product = await aProductInStock(request)
  await page.route('**/api/inventory/stock/*', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'Service Unavailable', status: 503, correlationId: 'e2e-stock-1' }),
    }),
  )
  await page.goto(`/products/${encodeURIComponent(product.sku)}`)
  await expect(page.getByRole('heading', { name: product.name })).toBeVisible()
  const alert = page.getByRole('alert').filter({ hasText: "We couldn't check the stock" })
  await expect(alert).toContainText('Reference: e2e-stock-1')

  await page.unroute('**/api/inventory/stock/*')
  await alert.getByRole('button', { name: 'Try again' }).click()
  await expect(alert).toBeHidden()
  await expect(page.locator('.stock-indicator')).not.toHaveText('Stock unknown')
})

test("a 401 on a page's data sends the Customer to sign in and back to the same page", async ({ page }) => {
  await page.goto('/orders')
  await signInOnKeycloak(page)
  await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible()

  // The token is refused once, as when the Keycloak session behind it has ended.
  await page.route('**/api/order-management/orders?*', (route) =>
    route.fulfill({ status: 401, contentType: 'application/problem+json', body: '{"title":"Unauthorized"}' }),
    { times: 1 },
  )
  // A reload drops the tokens, and the silent restore (prompt=none) gets new ones in an iframe first.
  const toKeycloak = page.waitForRequest(
    (request) => request.url().includes('/protocol/openid-connect/auth') && !request.url().includes('prompt=none'),
  )
  await page.reload()
  await toKeycloak
  // Keycloak still has a session here, so it sends the Customer straight back.
  await expect(page).toHaveURL(/\/orders$/)
  await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible()
})

test('a 401 on an action sends the Customer to sign in and back to the same page', async ({ page, request }) => {
  const product = await aProductInStock(request)
  const path = `/products/${encodeURIComponent(product.sku)}`
  await page.goto('/')
  await page.getByRole('button', { name: 'Log in' }).click()
  await signInOnKeycloak(page)
  await page.goto(path)
  await expect(page.getByRole('heading', { name: product.name })).toBeVisible()

  // Cart refuses the token on the write. It is answered here, so the demo Customer's Cart, which
  // the checkout tests use, never changes.
  await page.route('**/api/cart/cart/items/*', (route) =>
    route.fulfill({ status: 401, contentType: 'application/problem+json', body: '{"title":"Unauthorized"}' }),
  )
  const toKeycloak = page.waitForRequest(
    (request) => request.url().includes('/protocol/openid-connect/auth') && !request.url().includes('prompt=none'),
  )
  await page.getByRole('button', { name: 'Add to cart' }).click()
  await toKeycloak
  await expect(page).toHaveURL(new RegExp(`${path}$`))
  await expect(page.getByRole('heading', { name: product.name })).toBeVisible()
})

/** The first seeded Product whose first Variant has Stock left, so "Add to cart" is enabled. */
async function aProductInStock(request: APIRequestContext): Promise<Product> {
  const response = await request.get('/api/catalog/products')
  expect(response.ok()).toBeTruthy()
  for (const product of (await response.json()) as Product[]) {
    const stock = await request.get(`/api/inventory/stock/${encodeURIComponent(defaultVariant(product).id)}`)
    if (stock.ok() && ((await stock.json()) as { quantity: number }).quantity > 0) return product
  }
  throw new Error('No seeded Product has Stock left; run `make seed-reset`')
}

async function signInOnKeycloak(page: Page) {
  await page.locator('#username').fill(demoCustomer.email)
  await page.locator('#password').fill(demoCustomer.password)
  await page.locator('#kc-login').click()
}
