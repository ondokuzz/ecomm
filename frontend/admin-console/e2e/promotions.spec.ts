import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import type { Campaign } from '../src/domain/campaign'
import type { Coupon } from '../src/domain/coupon'
import { type Currencies, type Currency, type Money, currenciesOf, formatMoney } from '../src/domain/money'
import type { Order } from '../src/domain/order'

/**
 * Staff list, create, edit, switch off and delete Coupons and Campaigns in the Admin Console, and
 * see Promotions' refusals beside the field at fault. A Campaign Staff create applies itself to a
 * Customer's next checkout on the Storefront, and Staff then find the Order it gave. Each test's Coupon or Campaign is its own, and is
 * deleted again: through the console when the test gets that far, and through Promotions
 * otherwise. So is the Customer a test checks out as.
 */

interface Credentials {
  email: string
  password: string
}

/** A Customer made in Keycloak for one test. */
interface Customer extends Credentials {
  id: string
}

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
const storefrontUrl = process.env.STOREFRONT_URL ?? 'http://localhost:8080'
const staff: Credentials = { email: 'staff@ecomm.local', password: 'staff' }

let run: string

test.beforeEach(() => {
  run = `${Date.now().toString(36)}${test.info().workerIndex}`.toUpperCase()
})

test.describe('Coupons', () => {
  let code: string

  test.beforeEach(() => {
    code = `E2E-${run}`
  })

  test.afterEach(async ({ request }) => {
    const response = await request.delete(`/api/promotions/coupons/${code}`, { headers: await staffHeaders(request) })
    expect([204, 404]).toContain(response.status())
  })

  test('Staff create, edit, switch off and delete a Coupon', async ({ page, request }) => {
    await page.goto('/promotions/coupons/new')
    await signInOnKeycloak(page, staff)
    await expect(page.getByRole('heading', { name: 'New Coupon' })).toBeVisible()

    await page.getByLabel('Code').fill(code.toLowerCase())
    await page.getByLabel('Fixed amount off').check()
    await page.getByLabel('Amount off', { exact: true }).fill('5')
    await page.getByLabel('Minimum subtotal', { exact: true }).fill('20.00')
    await page.getByLabel('Valid from').fill('2026-01-01T00:00')
    await page.getByLabel('Valid until').fill('2030-01-01T00:00')
    await page.getByRole('button', { name: 'Create Coupon' }).click()

    await expect(page).toHaveURL(/\/promotions\/coupons$/)
    await expect(page.getByRole('status')).toHaveText(`Saved ${code}.`)
    const row = page.getByRole('row').filter({ hasText: code })
    await expect(row).toContainText('off')
    await expect(row).toContainText('On')
    expect(await couponOf(request, code)).toMatchObject({
      code,
      discount: { type: 'AMOUNT_OFF', amountOff: { amountMinor: 500, currency: 'EUR' } },
      minimumSubtotal: { amountMinor: 2000, currency: 'EUR' },
      active: true,
    })

    // Edited, it becomes a percentage with no minimum; the code stays.
    await row.getByRole('link', { name: code, exact: true }).click()
    await expect(page.getByLabel('Amount off', { exact: true })).toHaveValue('5.00')
    await page.getByLabel('Percentage off').check()
    await percentage(page).fill('15')
    await page.getByLabel('Minimum subtotal', { exact: true }).fill('')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('status')).toHaveText(`Saved ${code}.`)
    expect(await couponOf(request, code)).toMatchObject({
      discount: { type: 'PERCENT_OFF', percentOff: 15, amountOff: null },
      minimumSubtotal: null,
    })

    await row.getByRole('button', { name: 'Switch off' }).click()
    await expect(page.getByRole('status')).toHaveText(`Switched ${code} off.`)
    await expect(row).toContainText('Off')
    expect((await couponOf(request, code)).active).toBe(false)

    await row.getByRole('button', { name: `Delete ${code}` }).click()
    const dialog = page.getByRole('dialog', { name: `Delete ${code}?` })
    await dialog.getByRole('button', { name: 'Delete Coupon' }).click()
    await expect(page.getByRole('status')).toHaveText(`Deleted ${code}.`)
    await expect(page.getByRole('row').filter({ hasText: code })).toHaveCount(0)
    const gone = await request.get(`/api/promotions/coupons/${code}`, { headers: await staffHeaders(request) })
    expect(gone.status()).toBe(404)
  })

  test('a window that ends before it starts is refused beside Valid until', async ({ page }) => {
    await page.goto('/promotions/coupons/new')
    await signInOnKeycloak(page, staff)

    await page.getByLabel('Code').fill(code)
    await percentage(page).fill('10')
    await page.getByLabel('Valid from').fill('2030-01-01T00:00')
    await page.getByLabel('Valid until').fill('2029-01-01T00:00')
    await page.getByRole('button', { name: 'Create Coupon' }).click()

    const until = page.getByLabel('Valid until')
    await expect(until).toHaveAttribute('aria-invalid', 'true')
    await expect(until).toHaveAccessibleDescription('must be after validFrom')
    await expect(page.getByRole('alert')).toContainText("Couldn't save the Coupon")
  })
})

test.describe('Campaigns', () => {
  let name: string
  // Far from the seeded Audio week's 10, and each test's own.
  let priority: number
  // The Customer a test checks out as, if it makes one.
  let customer: Customer | undefined

  test.beforeEach(() => {
    name = `E2E Campaign ${run}`
    priority = 100_000 + Math.floor(Math.random() * 1_000_000)
  })

  test.afterEach(async ({ request }) => {
    if (customer) await deleteCustomer(request, customer)
    customer = undefined
    const headers = await staffHeaders(request)
    const response = await request.get('/api/promotions/campaigns', { headers })
    for (const campaign of ((await response.json()) as Campaign[]).filter((c) => c.name.startsWith(name))) {
      const deleted = await request.delete(`/api/promotions/campaigns/${campaign.id}`, { headers })
      expect([204, 404]).toContain(deleted.status())
    }
  })

  test('Staff create, edit, switch off and delete a Campaign, seeing its state', async ({ page, request }) => {
    await page.goto('/promotions/campaigns')
    await signInOnKeycloak(page, staff)
    await expect(page.getByRole('row').filter({ hasText: 'Audio week' })).toContainText('Running')

    await page.getByRole('link', { name: 'New Campaign' }).click()
    await page.getByLabel('Name').fill(name)
    // Audio week has priority 10: Promotions refuses it beside the field.
    await page.getByLabel('Priority').fill('10')
    await page.getByLabel('Audio').check()
    await percentage(page).fill('20')
    await page.getByLabel('Valid from').fill('2026-01-01T00:00')
    await page.getByLabel('Valid until').fill('2030-01-01T00:00')
    await page.getByRole('button', { name: 'Create Campaign' }).click()

    const priorityField = page.getByLabel('Priority')
    await expect(priorityField).toHaveAttribute('aria-invalid', 'true')
    await expect(priorityField).toHaveAccessibleDescription(/is taken/)
    await priorityField.fill(String(priority))
    await expect(priorityField).not.toHaveAttribute('aria-invalid')
    await page.getByRole('button', { name: 'Create Campaign' }).click()

    await expect(page).toHaveURL(/\/promotions\/campaigns$/)
    await expect(page.getByRole('status')).toHaveText(`Saved ${name}.`)
    const row = page.getByRole('row').filter({ hasText: name })
    await expect(row).toContainText('20% off')
    await expect(row).toContainText('Audio')
    await expect(row).toContainText('Running')
    const created = await campaignNamed(request, name)
    expect(created).toMatchObject({
      discount: { type: 'PERCENT_OFF', percentOff: 20 },
      categories: ['audio'],
      priority,
      validFrom: new Date('2026-01-01T00:00').toISOString().replace('.000Z', 'Z'),
      state: 'running',
    })

    // Moved into the future, it is scheduled.
    await row.getByRole('link', { name, exact: true }).click()
    await page.getByLabel('Name').fill(`${name} later`)
    await page.getByLabel('Valid from').fill('2029-01-01T00:00')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('status')).toHaveText(`Saved ${name} later.`)
    const later = page.getByRole('row').filter({ hasText: `${name} later` })
    await expect(later).toContainText('Scheduled')

    await later.getByRole('button', { name: 'Switch off' }).click()
    await expect(later).toContainText('Off')
    expect((await campaignNamed(request, `${name} later`)).state).toBe('off')

    await later.getByRole('button', { name: `Delete ${name} later` }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Delete Campaign' }).click()
    await expect(page.getByRole('status')).toHaveText(`Deleted ${name} later.`)
    await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(0)
    const gone = await request.get(`/api/promotions/campaigns/${created.id}`, { headers: await staffHeaders(request) })
    expect(gone.status()).toBe(404)
  })

  test('a Campaign Staff create applies itself to a Customer’s next checkout, and Staff find the Order', async ({
    page,
    browser,
    request,
  }) => {
    const shopper = await createCustomer(request)
    customer = shopper
    // A laptop: no seeded Campaign covers laptops, so this Campaign's is the only Discount.
    const { variantId, price } = await aLaptopInStock(request)
    const currencies = await currenciesOfCatalog(request)
    const discount = { ...price, amountMinor: Math.floor((price.amountMinor * 25) / 100) }

    await page.goto('/promotions/campaigns/new')
    await signInOnKeycloak(page, staff)
    await page.getByLabel('Name').fill(name)
    await page.getByLabel('Priority').fill(String(priority))
    await page.getByLabel('Laptops').check()
    await percentage(page).fill('25')
    await page.getByLabel('Valid from').fill('2026-01-01T00:00')
    await page.getByLabel('Valid until').fill('2030-01-01T00:00')
    await page.getByRole('button', { name: 'Create Campaign' }).click()
    await expect(page.getByRole('status')).toHaveText(`Saved ${name}.`)
    await expect(page.getByRole('row').filter({ hasText: name })).toContainText('Running')

    // The Customer starts checkout with a laptop in their Cart, and enters nothing.
    const token = await tokenFor(request, shopper)
    const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { quantity: 1 },
    })
    expect(put.ok()).toBeTruthy()
    // A browser of the Customer's own: this one is signed in to Keycloak as Staff.
    const shopperBrowser = await browser.newContext()
    const storefront = await shopperBrowser.newPage()
    let orderId: string
    try {
      await storefront.goto(`${storefrontUrl}/checkout`)
      await signInOnKeycloak(storefront, shopper)
      const summary = storefront.getByRole('region', { name: 'Order summary' })
      await expect(summary.getByText(`Campaign: ${name}`)).toBeVisible()
      await expect(summary).toContainText(
        formatMoney({ ...discount, amountMinor: -discount.amountMinor }, currencies, 'en-US'),
      )
      const total = { ...price, amountMinor: price.amountMinor - discount.amountMinor }

      // Paying ends the Checkout Session rather than leaving its Stock held, and the Order keeps the Discount.
      await storefront
        .getByRole('group', { name: 'Test card' })
        .getByRole('radio', { name: 'Approve', exact: true })
        .check()
      await storefront.getByRole('button', { name: `Pay ${formatMoney(total, currencies, 'en-US')}` }).click()
      await expect(storefront).toHaveURL(/\/orders\/[^/?]+\?placed$/)
      await expect(storefront.getByText(`Campaign: ${name}`)).toBeVisible()
      orderId = decodeURIComponent(new URL(storefront.url()).pathname.split('/').pop()!)
      const order = await request.get(`/api/order-management/orders/${encodeURIComponent(orderId)}`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      expect(((await order.json()) as Order).discounts).toEqual([
        expect.objectContaining({ source: 'CAMPAIGN', campaignName: name, amount: discount }),
      ])
    } finally {
      await shopperBrowser.close()
    }

    // Staff find that Order by the reference the Customer would quote, with its Discount and history.
    const reference = orderId.slice(0, 8).toUpperCase()
    await page.goto('/orders')
    await page.getByLabel('Order reference').fill(`#${reference}`)
    await page.getByRole('button', { name: 'Filter' }).click()
    await page.getByRole('link', { name: `Order #${reference}` }).click()
    await expect(page.getByRole('heading', { name: `Order #${reference}` })).toBeVisible()
    await expect(page.getByRole('main')).toContainText(name)
    const history = page.getByRole('table', { name: 'Status history' }).getByRole('row')
    await expect(history.nth(1)).toContainText('Paid')
    await expect(history.nth(1)).toContainText('(current)')
    await expect(history.nth(2)).toContainText('Placed')
  })
})

/** The percentage field, whose label the "Percentage off" choice shares a word with. */
function percentage(page: Page) {
  return page.getByRole('textbox', { name: /^Percentage/ })
}

async function signInOnKeycloak(page: Page, user: Credentials) {
  await page.locator('#username').fill(user.email)
  await page.locator('#password').fill(user.password)
  await page.locator('#kc-login').click()
}

/** A Staff token straight from Keycloak, through the realm's dev-only password-grant client. */
async function staffHeaders(request: APIRequestContext) {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'dev-cli', username: staff.email, password: staff.password },
  })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${((await response.json()) as { access_token: string }).access_token}` }
}

async function couponOf(request: APIRequestContext, code: string): Promise<Coupon> {
  const response = await request.get(`/api/promotions/coupons/${code}`, { headers: await staffHeaders(request) })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Coupon
}

async function campaignNamed(request: APIRequestContext, name: string): Promise<Campaign> {
  const response = await request.get('/api/promotions/campaigns', { headers: await staffHeaders(request) })
  expect(response.ok()).toBeTruthy()
  const campaign = ((await response.json()) as Campaign[]).find((c) => c.name === name)
  expect(campaign).toBeDefined()
  return campaign!
}

/** A token straight from Keycloak, through the realm's dev-only password-grant client. */
async function tokenFor(request: APIRequestContext, user: Credentials) {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'dev-cli', username: user.email, password: user.password },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { access_token: string }).access_token
}

/** A token for Keycloak's own admin API, from the master realm's bootstrap admin. */
async function keycloakAdminHeaders(request: APIRequestContext) {
  const response = await request.post(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'admin-cli', username: 'admin', password: 'admin' },
  })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${((await response.json()) as { access_token: string }).access_token}` }
}

/** A new Customer of the test's own, so its Cart is nobody else's, ready to sign in. */
async function createCustomer(request: APIRequestContext): Promise<Customer> {
  const email = `e2e-${run.toLowerCase()}@ecomm.local`
  const password = 'e2e-password'
  const response = await request.post(`${keycloakUrl}/admin/realms/ecomm/users`, {
    headers: await keycloakAdminHeaders(request),
    data: {
      username: email,
      email,
      firstName: 'E2E',
      lastName: 'Customer',
      enabled: true,
      emailVerified: true,
      credentials: [{ type: 'password', value: password, temporary: false }],
    },
  })
  expect(response.status()).toBe(201)
  return { id: response.headers().location.split('/').pop()!, email, password }
}

async function deleteCustomer(request: APIRequestContext, customer: Customer) {
  const response = await request.delete(`${keycloakUrl}/admin/realms/ecomm/users/${customer.id}`, {
    headers: await keycloakAdminHeaders(request),
  })
  expect(response.status()).toBe(204)
}

/** The Currencies Catalog prices in, whose Minor units Money is shown by. */
async function currenciesOfCatalog(request: APIRequestContext): Promise<Currencies> {
  const response = await request.get('/api/catalog/currencies')
  expect(response.ok()).toBeTruthy()
  return currenciesOf((await response.json()) as Currency[])
}

/** A seeded laptop whose first Variant has Stock left; earlier runs may have sold some out. */
async function aLaptopInStock(request: APIRequestContext): Promise<{ variantId: string; price: Money }> {
  const response = await request.get('/api/catalog/products')
  expect(response.ok()).toBeTruthy()
  const laptops = ((await response.json()) as { category: string; variants: { id: string; price: Money }[] }[]).filter(
    (product) => product.category === 'laptops',
  )
  for (const { variants } of laptops) {
    const [{ id, price }] = variants
    const stock = await request.get(`/api/inventory/stock/${encodeURIComponent(id)}`)
    expect(stock.ok()).toBeTruthy()
    if (((await stock.json()) as { quantity: number }).quantity > 0) return { variantId: id, price }
  }
  throw new Error('No seeded laptop has Stock left; run `make seed-reset`')
}
