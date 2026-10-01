import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import { formatMoney } from '../src/domain/money'
import type { Product, Stock } from '../src/domain/product'

/**
 * Staff create a Product with two Variants in the Admin Console and set their Stock, and Customers
 * can then pick either Variant on the Storefront. The Product is deleted again, through the
 * console when the test gets that far, and through Catalog otherwise. Inventory has no way to
 * remove Stock, so each run's Variants keep a Stock row; their IDs are unique to the run.
 */

interface Credentials {
  email: string
  password: string
}

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
const storefrontUrl = process.env.STOREFRONT_URL ?? 'http://localhost:8080'
const staff: Credentials = { email: 'staff@ecomm.local', password: 'staff' }

const run = Date.now().toString(36).toUpperCase()
const sku = `E2E-PHONE-${run}`
const name = `E2E Phone ${run}`
const variants = [
  { id: sku, color: 'Graphite', storage: '128 GB', price: '799.00', onHand: 5 },
  // Only the colour differs, so the Storefront's picker reaches either Variant from the other.
  { id: `${sku}-COBALT`, color: 'Cobalt', storage: '128 GB', price: '899.50', onHand: 3 },
] as const

test.afterEach(async ({ request }) => {
  const response = await request.delete(`/api/catalog/products/${encodeURIComponent(sku)}`, {
    headers: { Authorization: `Bearer ${await tokenFor(request, staff)}` },
  })
  expect([204, 404]).toContain(response.status())
})

test('Staff create a Product with two Variants and their Stock, and Customers can pick either on the Storefront', async ({ page, request }) => {
  await page.goto('/products/new')
  await signInOnKeycloak(page, staff)
  await expect(page.getByRole('heading', { name: 'New Product' })).toBeVisible()

  // The Category comes first: it decides the attribute fields and the Variant axes.
  await page.getByLabel('Category').selectOption({ label: 'Phones' })
  await page.getByLabel('SKU').fill(sku)
  await page.getByLabel('Name').fill(name)
  await page.getByLabel('brand').fill('Ecomm')
  await page.getByLabel('screen').fill('6.1 in')

  await page.getByRole('button', { name: 'Add Variant' }).click()
  for (const [i, variant] of variants.entries()) {
    const row = `Variant ${i + 1}`
    await page.getByLabel(`${row} ID`).fill(variant.id)
    await page.getByLabel(`${row} color`).fill(variant.color)
    await page.getByLabel(`${row} storage`).selectOption(variant.storage)
    await page.getByLabel(`${row} price in EUR`).fill(variant.price)
    await page.getByLabel(`${row} on hand`).fill(String(variant.onHand))
  }
  await page.getByRole('button', { name: 'Create Product' }).click()

  await expect(page).toHaveURL(/\/products$/)
  await expect(page.getByRole('status')).toHaveText(`Saved ${name}.`)
  await page.getByLabel('Search').fill(run)
  const listed = page.getByRole('row').filter({ hasText: sku })
  await expect(listed).toContainText(name)
  await expect(listed).toContainText(formatMoney({ amountMinor: 79900, currency: 'EUR' }))

  // Catalog has the Product as typed, its Prices as Money, and Inventory their On-hand counts.
  const product = await productOf(request)
  expect(product.variants.map(({ id, axisValues, price }) => ({ id, axisValues, price }))).toEqual([
    { id: variants[0].id, axisValues: { color: 'Graphite', storage: '128 GB' }, price: { amountMinor: 79900, currency: 'EUR' } },
    { id: variants[1].id, axisValues: { color: 'Cobalt', storage: '128 GB' }, price: { amountMinor: 89950, currency: 'EUR' } },
  ])
  for (const variant of variants) {
    expect(await stockOf(request, variant.id)).toMatchObject({ onHand: variant.onHand, reserved: 0, quantity: variant.onHand })
  }

  // Reopened, the editor shows the saved Variant IDs read-only, with their Stock.
  await listed.getByRole('link', { name, exact: true }).click()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
  await expect(page.getByLabel('Variant 1 ID')).toHaveCount(0)
  await expect(page.getByLabel('Variant 2 price in EUR')).toHaveValue('899.50')
  await expect(page.getByLabel('Variant 2 on hand')).toHaveValue('3')

  // On the Storefront, a Customer picks the second Variant and sees its Price and Stock.
  const storefront = await page.context().newPage()
  await storefront.goto(`${storefrontUrl}/products/${encodeURIComponent(sku)}`)
  await expect(storefront.getByRole('heading', { name, exact: true })).toBeVisible()
  await expect(storefront.locator('.purchase-panel .price')).toHaveText(formatMoney({ amountMinor: 79900, currency: 'EUR' }, 'en-US'))
  const cobalt = storefront.getByRole('group', { name: 'color' }).getByRole('radio', { name: 'Cobalt' })
  await cobalt.click()
  await expect(cobalt).toBeChecked()
  await expect(storefront).toHaveURL(new RegExp(`[?&]variant=${encodeURIComponent(variants[1].id)}$`))
  await expect(storefront.getByRole('group', { name: 'storage' }).getByRole('radio', { name: '128 GB' })).toBeChecked()
  await expect(storefront.locator('.purchase-panel .price')).toHaveText(formatMoney({ amountMinor: 89950, currency: 'EUR' }, 'en-US'))
  await expect(storefront.getByText('Only 3 left')).toBeVisible()
  await storefront.close()

  // Deleting goes through a ConfirmDialog.
  await page.getByRole('link', { name: 'Products' }).first().click()
  await page.getByLabel('Search').fill(run)
  await page.getByRole('button', { name: `Delete ${name}` }).click()
  const dialog = page.getByRole('dialog', { name: `Delete ${name}?` })
  await dialog.getByRole('button', { name: 'Delete Product' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('status')).toHaveText(`Deleted ${name}.`)
  await expect(page.getByRole('row').filter({ hasText: sku })).toHaveCount(0)
  expect((await request.get(`/api/catalog/products/${encodeURIComponent(sku)}`)).status()).toBe(404)
})

async function signInOnKeycloak(page: Page, user: Credentials) {
  await page.locator('#username').fill(user.email)
  await page.locator('#password').fill(user.password)
  await page.locator('#kc-login').click()
}

/** A token straight from Keycloak, through the realm's dev-only password-grant client. */
async function tokenFor(request: APIRequestContext, user: Credentials) {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'dev-cli', username: user.email, password: user.password },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { access_token: string }).access_token
}

async function productOf(request: APIRequestContext): Promise<Product> {
  const response = await request.get(`/api/catalog/products/${encodeURIComponent(sku)}`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Product
}

async function stockOf(request: APIRequestContext, variantId: string): Promise<Stock> {
  const response = await request.get(`/api/inventory/stock/${encodeURIComponent(variantId)}`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Stock
}
