import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import { type Product, defaultVariant } from '../src/domain/catalog'
import type { Order } from '../src/domain/order'

/**
 * The Sprint 1 definition of done, end to end: browse seeded Products, add them to the Cart, check
 * out with the mock payment, and see the Order confirmation and its Order Status.
 */

// The demo checkout compares Stock before and after, so nothing else may check out meanwhile.
test.describe.configure({ mode: 'serial' })

interface Credentials {
  email: string
  password: string
}

interface ProductInStock {
  product: Product
  variantId: string
  stock: number
}

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
const demoCustomer: Credentials = { email: 'demo@ecomm.local', password: 'demo' }

test('the demo Customer checks out two Products and Stock goes down', async ({ page, request }) => {
  const token = await tokenFor(request, demoCustomer)
  await emptyCart(request, token)
  const bought = await twoProductsInStock(request)

  await page.goto('/')
  await page.getByRole('button', { name: 'Log in' }).click()
  await signInOnKeycloak(page, demoCustomer)
  await expectSignedIn(page, demoCustomer.email)

  for (const [i, { product }] of bought.entries()) {
    await page.getByRole('navigation', { name: 'Main', exact: true }).getByRole('link', { name: 'Products' }).click()
    await page.locator(`a[href="/products/${encodeURIComponent(product.sku)}"]`).click()
    await expect(page.getByRole('heading', { name: product.name })).toBeVisible()
    await expect(page.getByText(/^(In stock|Only \d+ left)$/)).toBeVisible()
    await page.getByRole('button', { name: 'Add to cart' }).click()
    const toast = page.locator('.toast').filter({ hasText: 'Added to cart' })
    await expect(toast.getByRole('link', { name: 'View cart' })).toHaveAttribute('href', '/cart')
    await expect(page.getByText('1 in your cart')).toBeVisible()
    await expect(page.getByRole('link', { name: `Cart (${i + 1})` })).toBeVisible()
    await toast.getByRole('button', { name: 'Dismiss' }).click()
    await expect(toast).toHaveCount(0)
  }

  await page.getByRole('link', { name: /^Cart/ }).click()
  const cartLines = page.getByRole('list', { name: 'Cart lines' })
  for (const { product } of bought) {
    await expect(cartLines.getByRole('link', { name: product.name })).toBeVisible()
  }
  await expect(page.getByRole('region', { name: 'Order summary' })).toContainText('2 items')
  await page.getByRole('link', { name: 'Go to checkout' }).click()
  const steps = page.getByRole('navigation', { name: 'Checkout steps' })
  const currentStep = steps.locator('[aria-current=step]')
  // Screen readers hear "(done)" after each step that is done.
  const doneSteps = steps.getByRole('listitem').filter({ hasText: '(done)' })
  await expect(currentStep).toContainText('Payment')
  await expect(doneSteps).toHaveText([/^Cart/])
  await page.getByRole('button', { name: /^Pay/ }).click()

  await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
  await expect(page.getByText('Thank you! Your order is confirmed.')).toBeVisible()
  await expect(currentStep).toContainText('Done')
  await expect(doneSteps).toHaveText([/^Cart/, /^Payment/, /^Done/])
  // The badge is how the page renders Order Status PAID; the Order itself must say PAID too.
  await expect(page.locator('.status-paid')).toHaveText('Paid')
  const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
  const order = await orderOf(request, token, orderId)
  expect(order.status).toBe('PAID')
  for (const { variantId } of bought) {
    await expect(page.getByRole('link', { name: variantId })).toBeVisible()
    expect(order.lines.find((line) => line.variantId === variantId)?.quantity).toBe(1)
  }

  for (const { variantId, stock } of bought) {
    expect(await stockOf(request, variantId)).toBe(stock - 1)
  }
})

test('the demo Customer empties their Cart after confirming', async ({ page, request }) => {
  const token = await tokenFor(request, demoCustomer)
  await emptyCart(request, token)
  const [{ variantId }] = await twoProductsInStock(request)
  const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { quantity: 2 },
  })
  expect(put.ok()).toBeTruthy()

  await page.goto('/cart')
  await signInOnKeycloak(page, demoCustomer)
  await expect(page.getByRole('link', { name: 'Cart (2)' })).toBeVisible()

  await page.getByRole('button', { name: 'Empty cart' }).click()
  const dialog = page.getByRole('dialog', { name: 'Empty your cart?' })
  await dialog.getByRole('button', { name: 'Cancel' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('link', { name: 'Cart (2)' })).toBeVisible()

  await page.getByRole('button', { name: 'Empty cart' }).click()
  await dialog.getByRole('button', { name: 'Empty cart' }).click()
  await expect(page.getByRole('heading', { name: 'Your cart is empty' })).toBeFocused()
  await expect(page.getByRole('link', { name: 'Browse products' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Cart', exact: true })).toBeVisible()
  const cart = await request.get('/api/cart/cart', { headers: { Authorization: `Bearer ${token}` } })
  expect(((await cart.json()) as { items: unknown[] }).items).toEqual([])
})

test('a new Customer registers on Keycloak and comes back signed in', async ({ page, baseURL }) => {
  const email = `smoke-${Date.now()}@ecomm.local`

  await page.goto('/')
  await page.getByRole('button', { name: 'Register' }).click()
  await page.locator('#email').fill(email)
  await page.locator('#firstName').fill('Smoke')
  await page.locator('#lastName').fill('Test')
  await page.locator('#password').fill('smoke-password')
  await page.locator('#password-confirm').fill('smoke-password')
  await page.locator('[type=submit]').click()

  await expect(page).toHaveURL((url) => url.origin === new URL(baseURL!).origin)
  await expectSignedIn(page, email)
})

/** The Customer menu, behind the Customer's avatar, names them and offers to log out. */
async function expectSignedIn(page: Page, email: string) {
  await page.getByRole('button', { name: 'Customer menu' }).click()
  await expect(page.getByText(email)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible()
  await page.keyboard.press('Escape')
}

async function signInOnKeycloak(page: Page, user: Credentials) {
  await page.locator('#username').fill(user.email)
  await page.locator('#password').fill(user.password)
  await page.locator('#kc-login').click()
}

/** A Customer's token straight from Keycloak, through the realm's dev-only password-grant client. */
async function tokenFor(request: APIRequestContext, user: Credentials) {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'dev-cli', username: user.email, password: user.password },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { access_token: string }).access_token
}

/** A leftover Cart would change what gets checked out, so start from an empty one. */
async function emptyCart(request: APIRequestContext, token: string) {
  const response = await request.delete('/api/cart/cart', { headers: { Authorization: `Bearer ${token}` } })
  expect(response.status()).toBe(204)
}

async function orderOf(request: APIRequestContext, token: string, orderId: string) {
  const response = await request.get(`/api/order-management/orders/${encodeURIComponent(orderId)}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Order
}

async function stockOf(request: APIRequestContext, variantId: string) {
  const response = await request.get(`/api/inventory/stock/${encodeURIComponent(variantId)}`)
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { quantity: number }).quantity
}

/** The first two seeded Products with Stock left; earlier runs may have sold some out. */
async function twoProductsInStock(request: APIRequestContext): Promise<ProductInStock[]> {
  const response = await request.get('/api/catalog/products')
  expect(response.ok()).toBeTruthy()
  const inStock: ProductInStock[] = []
  for (const product of (await response.json()) as Product[]) {
    const variantId = defaultVariant(product).id
    const stock = await stockOf(request, variantId)
    if (stock > 0) inStock.push({ product, variantId, stock })
    if (inStock.length === 2) return inStock
  }
  throw new Error('Fewer than two Products have Stock left; run `make seed-reset`')
}
