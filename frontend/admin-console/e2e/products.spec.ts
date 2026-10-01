import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import { formatMoney } from '../src/domain/money'
import type { Product, Stock } from '../src/domain/product'

/**
 * Staff create Products with two Variants in the Admin Console and set their Stock, and Customers
 * can then pick either Variant on the Storefront. Each test's Product has its own SKU, and is
 * deleted again with its Stock: through the console when the test gets that far, and through
 * Catalog and Inventory otherwise.
 */

interface Credentials {
  email: string
  password: string
}

/** A Product in `phones` for one test, with two Variants that differ only in colour. */
interface Phone {
  sku: string
  name: string
  variants: readonly { id: string; color: string; storage: string; price: string; onHand: number }[]
}

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
const storefrontUrl = process.env.STOREFRONT_URL ?? 'http://localhost:8080'
const staff: Credentials = { email: 'staff@ecomm.local', password: 'staff' }

let phone: Phone

test.beforeEach(() => {
  const run = `${Date.now().toString(36)}${test.info().workerIndex}`.toUpperCase()
  const sku = `E2E-PHONE-${run}`
  phone = {
    sku,
    name: `E2E Phone ${run}`,
    variants: [
      { id: sku, color: 'Graphite', storage: '128 GB', price: '799.00', onHand: 5 },
      // Only the colour differs, so the Storefront's picker reaches either Variant from the other.
      { id: `${sku}-COBALT`, color: 'Cobalt', storage: '128 GB', price: '899.50', onHand: 3 },
    ],
  }
})

test.afterEach(async ({ request }) => {
  const headers = { Authorization: `Bearer ${await tokenFor(request, staff)}` }
  const product = await request.delete(`/api/catalog/products/${encodeURIComponent(phone.sku)}`, { headers })
  expect([204, 404]).toContain(product.status())
  for (const { id } of phone.variants) {
    const stock = await request.delete(`/api/inventory/stock/${encodeURIComponent(id)}`, { headers })
    expect([204, 404]).toContain(stock.status())
  }
})

test('Staff create a Product with two Variants and their Stock, and Customers can pick either on the Storefront', async ({ page, request }) => {
  const { sku, name, variants } = phone
  await createInConsole(page)

  await expect(page).toHaveURL(/\/products$/)
  await expect(page.getByRole('status')).toHaveText(`Saved ${name}.`)
  await page.getByLabel('Search').fill(sku)
  const listed = page.getByRole('row').filter({ hasText: sku })
  await expect(listed).toContainText(name)
  await expect(listed).toContainText(formatMoney({ amountMinor: 79900, currency: 'EUR' }))

  // Catalog has the Product as typed, its Prices as Money, and Inventory their On-hand counts.
  const product = await productOf(request, sku)
  expect(product.variants.map(({ id, axisValues, price }) => ({ id, axisValues, price }))).toEqual([
    { id: variants[0]!.id, axisValues: { color: 'Graphite', storage: '128 GB' }, price: { amountMinor: 79900, currency: 'EUR' } },
    { id: variants[1]!.id, axisValues: { color: 'Cobalt', storage: '128 GB' }, price: { amountMinor: 89950, currency: 'EUR' } },
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
  await expect(storefront).toHaveURL(new RegExp(`[?&]variant=${encodeURIComponent(variants[1]!.id)}$`))
  await expect(storefront.getByRole('group', { name: 'storage' }).getByRole('radio', { name: '128 GB' })).toBeChecked()
  await expect(storefront.locator('.purchase-panel .price')).toHaveText(formatMoney({ amountMinor: 89950, currency: 'EUR' }, 'en-US'))
  await expect(storefront.getByText('Only 3 left')).toBeVisible()
  await storefront.close()

  // Deleting goes through a ConfirmDialog, and takes the Variants' Stock with the Product.
  await page.getByRole('link', { name: 'Products' }).first().click()
  await page.getByLabel('Search').fill(sku)
  await page.getByRole('button', { name: `Delete ${name}` }).click()
  const dialog = page.getByRole('dialog', { name: `Delete ${name}?` })
  await dialog.getByRole('button', { name: 'Delete Product' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('status')).toHaveText(`Deleted ${name}.`)
  await expect(page.getByRole('row').filter({ hasText: sku })).toHaveCount(0)
  expect((await request.get(`/api/catalog/products/${encodeURIComponent(sku)}`)).status()).toBe(404)
  for (const { id } of variants) {
    expect((await request.get(`/api/inventory/stock/${encodeURIComponent(id)}`)).status()).toBe(404)
  }
})

test('a new Product whose Stock Inventory refuses is saved, opens as itself, and saving again sets the Stock', async ({ page, request }) => {
  const { sku, name, variants } = phone
  const refused = variants[1]!
  const refusedStock = `**/api/inventory/stock/${encodeURIComponent(refused.id)}`
  // Only Reservations make Inventory refuse a count, and a new Variant has none; so it is stood in for.
  await page.route(refusedStock, (route) =>
    route.request().method() === 'PUT'
      ? route.fulfill({
          status: 409,
          contentType: 'application/problem+json',
          body: JSON.stringify({ status: 409, detail: 'Reservations hold 9 of it, more than that new on-hand count', reserved: 9 }),
        })
      : route.fallback(),
  )
  await createInConsole(page)

  await expect(page).toHaveURL(new RegExp(`/products/${encodeURIComponent(sku)}$`))
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText(`Saved ${name}, but not all of its Stock`)
  const onHand = page.getByLabel('Variant 2 on hand')
  await expect(onHand).toHaveValue(String(refused.onHand))
  await expect(onHand).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByText('Reservations hold 9 of it, more than that new on-hand count')).toBeVisible()
  await expect(page.getByLabel('Variant 1 on hand')).toHaveValue(String(variants[0]!.onHand))
  expect((await request.get(`/api/inventory/stock/${encodeURIComponent(refused.id)}`)).status()).toBe(404)

  // A reload shows the Product as Catalog and Inventory have it.
  await page.reload()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(onHand).toHaveValue('')

  await page.unroute(refusedStock)
  await onHand.fill(String(refused.onHand))
  await page.getByRole('button', { name: 'Save changes' }).click()
  await expect(page).toHaveURL(/\/products$/)
  await expect(page.getByRole('status')).toHaveText(`Saved ${name}.`)
  expect(await stockOf(request, refused.id)).toMatchObject({ onHand: refused.onHand })
})

/** Signs in as Staff on a new Product's page and creates `phone` with its Variants and Stock. */
async function createInConsole(page: Page) {
  await page.goto('/products/new')
  await signInOnKeycloak(page, staff)
  await expect(page.getByRole('heading', { name: 'New Product' })).toBeVisible()

  // The Category comes first: it decides the attribute fields and the Variant axes.
  await page.getByLabel('Category').selectOption({ label: 'Phones' })
  await page.getByLabel('SKU').fill(phone.sku)
  await page.getByLabel('Name').fill(phone.name)
  await page.getByLabel('brand').fill('Ecomm')
  await page.getByLabel('screen').fill('6.1 in')

  await page.getByRole('button', { name: 'Add Variant' }).click()
  for (const [i, variant] of phone.variants.entries()) {
    const row = `Variant ${i + 1}`
    await page.getByLabel(`${row} ID`).fill(variant.id)
    await page.getByLabel(`${row} color`).fill(variant.color)
    await page.getByLabel(`${row} storage`).selectOption(variant.storage)
    await page.getByLabel(`${row} price in EUR`).fill(variant.price)
    await page.getByLabel(`${row} on hand`).fill(String(variant.onHand))
  }
  await page.getByRole('button', { name: 'Create Product' }).click()
}

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

async function productOf(request: APIRequestContext, sku: string): Promise<Product> {
  const response = await request.get(`/api/catalog/products/${encodeURIComponent(sku)}`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Product
}

async function stockOf(request: APIRequestContext, variantId: string): Promise<Stock> {
  const response = await request.get(`/api/inventory/stock/${encodeURIComponent(variantId)}`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Stock
}
