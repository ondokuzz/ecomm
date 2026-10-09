import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import type { Order } from '../src/domain/order'

/**
 * Staff find a Customer's Order in the Admin Console by its Order reference and by its Customer and
 * Status, open it and read its Status history with each change's time and caller.
 *
 * The test places its own Order for a Customer of its own, straight at Order Management as the
 * checkout Saga does, since the gateway never routes placement. Orders are never deleted, so it ends the Order
 * as Cancelled: the history the test reads.
 */

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
/** Order Management's compose host port, which bypasses the gateway, for the checkout Saga's commands. */
const orderManagementUrl = process.env.ORDER_MANAGEMENT_URL ?? 'http://localhost:8085'
const staff = { email: 'staff@ecomm.local', password: 'staff' }

test('Staff find a Customer’s Order and see its Status history', async ({ page, request }) => {
  const customerId = `e2e-customer-${Date.now().toString(36)}${test.info().workerIndex}`
  const order = await placePaidThenCancelledOrder(request, customerId)
  const reference = order.id.slice(0, 8).toUpperCase()

  await page.goto('/orders')
  await signInOnKeycloak(page)
  await expect(page.getByRole('heading', { name: 'Orders' })).toBeVisible()

  // By the reference the Customer quotes, # and all.
  await page.getByLabel('Order reference').fill(`#${reference}`)
  await page.getByRole('button', { name: 'Filter' }).click()
  await expect(page).toHaveURL(new RegExp(`reference=%23${reference}`))
  await expect(page.getByRole('navigation', { name: 'Pages' })).toContainText('Page 1 of 1 · 1 Order')
  const rows = page.getByRole('table').getByRole('row')
  await expect(rows).toHaveCount(2)
  await expect(rows.nth(1)).toContainText(customerId)
  await expect(rows.nth(1)).toContainText('Cancelled')

  // By the Customer and a Status: the Order is Cancelled, so it is no longer among the Paid ones.
  // The form is remade from the URL once Clear lands; typing before then would be lost.
  await page.getByRole('link', { name: 'Clear' }).click()
  await expect(page).toHaveURL(/\/orders$/)
  await expect(page.getByLabel('Order reference')).toHaveValue('')
  await page.getByLabel('Customer ID').fill(customerId)
  await page.getByLabel('Status').selectOption('PAID')
  await page.getByRole('button', { name: 'Filter' }).click()
  await expect(page.getByRole('table')).toContainText('No Orders match these filters.')
  await page.getByLabel('Status').selectOption('CANCELLED')
  await page.getByRole('button', { name: 'Filter' }).click()
  await page.getByRole('link', { name: `Order #${reference}` }).click()

  await expect(page).toHaveURL(new RegExp(`/orders/${order.id}$`))
  await expect(page.getByRole('heading', { name: `Order #${reference}` })).toBeVisible()
  await expect(page.getByText(customerId)).toBeVisible()
  const history = page.getByRole('table', { name: 'Status history' }).getByRole('row')
  await expect(history).toHaveCount(4)
  for (const [i, entry] of [...order.statusHistory].reverse().entries()) {
    const row = history.nth(i + 1)
    await expect(row).toContainText(entry.status.charAt(0) + entry.status.slice(1).toLowerCase())
    await expect(row).toContainText('Checkout Saga')
    await expect(row.locator('time')).toHaveAttribute('datetime', entry.at)
  }
  await expect(history.nth(1)).toContainText('(current)')
  // Read only: nothing on the page changes the Order.
  await expect(page.getByRole('main').getByRole('button')).toHaveCount(0)

  // Back to the list as it was filtered.
  await page.getByRole('main').getByRole('link', { name: 'Orders' }).click()
  await expect(page).toHaveURL(/\/orders\?/)
  const filters = new URL(page.url()).searchParams
  expect(filters.get('customer')).toBe(customerId)
  expect(filters.get('status')).toBe('CANCELLED')
})

async function signInOnKeycloak(page: Page) {
  await page.locator('#username').fill(staff.email)
  await page.locator('#password').fill(staff.password)
  await page.locator('#kc-login').click()
}

/**
 * Places an Order for `customerId`, pays it and cancels it, as the checkout Saga does with
 * Orchestration's token and a key per command, and returns it as Staff
 * read it through the gateway, with its times as Order Management stores them.
 */
async function placePaidThenCancelledOrder(request: APIRequestContext, customerId: string): Promise<Order> {
  const headers = await tokenHeaders(request, {
    grant_type: 'client_credentials',
    client_id: 'orchestration',
    client_secret: 'orchestration-dev-secret',
  })
  const key = (step: string) => `e2e-${customerId}:run-1:${step}`
  const placed = await request.post(`${orderManagementUrl}/orders`, {
    headers: { ...headers, 'Idempotency-Key': key('placeOrder') },
    data: {
      customerId,
      lines: [{ variantId: 'PHN-PIXEL-9', quantity: 1, unitPrice: { amountMinor: 79900, currency: 'EUR' } }],
      tax: { amountMinor: 0, currency: 'EUR' },
    },
  })
  expect(placed.status()).toBe(201)
  const { id } = (await placed.json()) as Order
  for (const status of ['PAID', 'CANCELLED']) {
    const changed = await request.patch(`${orderManagementUrl}/orders/${id}/status`, {
      headers: { ...headers, 'Idempotency-Key': key(status) },
      data: { customerId, status },
    })
    expect(changed.ok()).toBeTruthy()
  }
  const staffHeaders = await tokenHeaders(request, {
    grant_type: 'password',
    client_id: 'dev-cli',
    username: staff.email,
    password: staff.password,
  })
  const read = await request.get(`/api/order-management/staff/orders/${id}`, { headers: staffHeaders })
  expect(read.ok()).toBeTruthy()
  return (await read.json()) as Order
}

async function tokenHeaders(request: APIRequestContext, form: Record<string, string>) {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, { form })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${((await response.json()) as { access_token: string }).access_token}` }
}
