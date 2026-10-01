import { type Page, expect, test } from '@playwright/test'

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

test('a 401 mid-flow sends the Customer to sign in and back to the same page', async ({ page }) => {
  await page.goto('/orders')
  await signInOnKeycloak(page)
  await expect(page.getByRole('heading', { name: 'My orders' })).toBeVisible()

  // The token is refused once, as when the Keycloak session behind it has ended.
  await page.route('**/api/order-management/orders', (route) =>
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

async function signInOnKeycloak(page: Page) {
  await page.locator('#username').fill(demoCustomer.email)
  await page.locator('#password').fill(demoCustomer.password)
  await page.locator('#kc-login').click()
}
