import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import type { Page as ListPage } from '../src/domain/paging'
import type { Shipment } from '../src/domain/shipment'
import { stackSecret } from './secrets'

/**
 * Staff work the fulfillment queue. A Customer of the test's own buys a phone, shipped to Berlin,
 * through the gateway as the Storefront would; the checkout Saga marks the Order paid, and the
 * fulfillment Saga creates its Shipment. Staff find it in the queue under the Berlin Warehouse,
 * pick it, have an invalid IMEI refused, pack it with a valid one, and see the fulfillment Saga
 * capture the Payment and dispatch it with a tracking number. A second Order, paid with
 * `tok_capture_decline`, is authorized but refused at capture, so its packed Shipment goes on hold
 * with the payment gateway's reason.
 *
 * Orders and Shipments are never deleted; each run ships IMEIs of its own. The Customer is deleted.
 */

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
const staff = { email: 'staff@ecomm.local', password: 'staff' }
const run = `${Date.now().toString(36)}${process.pid.toString(36)}`

interface Customer {
  id: string
  email: string
  password: string
}

let customer: Customer | undefined

test.describe.configure({ mode: 'serial' })

test.afterAll(async ({ request }) => {
  if (customer) await deleteCustomer(request, customer)
})

test('Staff pick and pack a Shipment from the queue, and the fulfillment Saga dispatches it', async ({
  page,
  request,
}) => {
  // The Sagas, then the Carrier booking, on a stack that may have been idle.
  test.setTimeout(180_000)
  customer = await createCustomer(request)
  const phone = await aPhoneInStock(request)
  const orderId = await buy(request, customer, phone.variantId, 'tok_approve')
  const shipment = await shipmentOf(request, orderId)
  expect(shipment.warehouseId).toBe('WH-BER')
  const reference = `#${orderId.slice(0, 8).toUpperCase()}`

  // Under Berlin, awaiting pick: oldest first, so the newest is on the last page.
  const lastPage = await lastQueuePage(request, 'AWAITING_PICK', 'WH-BER')
  await page.goto('/fulfillment')
  await signInOnKeycloak(page)
  await expect(page.getByRole('heading', { name: 'Fulfillment' })).toBeVisible()
  await page.getByLabel('Warehouse').selectOption('WH-BER')
  await page.goto(lastPage > 1 ? `/fulfillment?page=${lastPage}` : '/fulfillment')
  // The Warehouse is remembered by the browser, past a reload.
  await expect(page.getByLabel('Warehouse')).toHaveValue('WH-BER')
  const tabs = page.getByRole('navigation', { name: 'Shipment statuses' })
  await expect(tabs.getByRole('link', { name: /^Awaiting pick/ })).toHaveAttribute('aria-current', 'page')
  await expect(tabs.getByRole('link', { name: /^Awaiting pick \d+$/ })).toBeVisible()
  const row = page.getByRole('table', { name: 'Shipments' }).getByRole('row').filter({ hasText: reference })
  await expect(row).toContainText('WH-BER')
  await expect(row).toContainText('DE')
  await expect(row).toContainText(/just now|minutes? ago/)

  // What to pick and where it goes.
  await row.getByRole('link', { name: reference }).click()
  await expect(page).toHaveURL(new RegExp(`/fulfillment/${shipment.id}$`))
  await expect(page.getByRole('heading', { name: `Shipment for Order ${reference}` })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Shipping address' })).toContainText('Erika Mustermann')
  await expect(page.getByRole('table', { name: 'Lines' })).toContainText(phone.variantId)
  await page.getByRole('button', { name: 'Mark picked' }).click()
  await expect(page.getByRole('region', { name: 'Pack', exact: true })).toBeVisible()

  // An IMEI with a wrong check digit is refused as it is typed, and Pack sends nothing.
  const imeiInput = page.getByRole('form', { name: 'Pack' }).getByLabel('IMEI', { exact: true })
  const imei = newImei()
  await imeiInput.fill(imei.slice(0, 14) + String((Number(imei[14]) + 1) % 10))
  await expect(page.getByText('Not a valid IMEI: its check digit is wrong. Check for a typo.')).toBeVisible()
  await expect(imeiInput).toHaveAttribute('aria-invalid', 'true')
  await page.getByRole('button', { name: 'Pack', exact: true }).click()
  await expect(page.getByRole('table', { name: 'Status history' }).getByRole('row')).toHaveCount(3)

  // A valid one packs it, and the fulfillment Saga captures the Payment and books the Carrier.
  await imeiInput.fill(imei)
  await expect(imeiInput).not.toHaveAttribute('aria-invalid', 'true')
  await page.getByRole('button', { name: 'Pack', exact: true }).click()
  await expect(page.getByRole('table', { name: 'Lines' })).toContainText(imei)
  const tracking = page.getByRole('region', { name: 'Tracking', exact: true })
  await expect(tracking).toBeVisible({ timeout: 60_000 })
  await expect(page.locator('.page-heading')).toContainText('Shipped')
  const dispatched = await shipmentOf(request, orderId)
  expect(dispatched.trackingNumber).toBeTruthy()
  await expect(tracking).toContainText(dispatched.trackingNumber!)
  const history = page.getByRole('table', { name: 'Status history' })
  await expect(history.getByRole('row').nth(1)).toContainText('Shipped')
  await expect(history.getByRole('row').nth(1)).toContainText('Fulfillment Saga')
  await expect(history.getByRole('row').filter({ hasText: 'Packed' })).toContainText(
    subjectOf(await staffToken(request)),
  )

  // The Order page shows its Shipment's status and links to it.
  await page.getByRole('region', { name: 'About this Shipment' }).getByRole('link', { name: reference }).click()
  const onOrder = page.getByRole('region', { name: 'Shipment', exact: true })
  await expect(onOrder).toContainText(/Shipped|Delivered/)
  await expect(onOrder).toContainText(dispatched.trackingNumber!)
  await onOrder.getByRole('link', { name: 'Open the Shipment' }).click()
  await expect(page).toHaveURL(new RegExp(`/fulfillment/${shipment.id}$`))
})

test('a Shipment whose Payment is refused at capture goes on hold with the reason', async ({ page, request }) => {
  test.setTimeout(180_000)
  customer ??= await createCustomer(request)
  const phone = await aPhoneInStock(request)
  const orderId = await buy(request, customer, phone.variantId, 'tok_capture_decline')
  const shipment = await shipmentOf(request, orderId)

  // Picked and packed through the API: the queue's own steps are the test above's.
  const headers = bearer(await staffToken(request))
  const path = `/api/fulfillment-shipping/shipments/${shipment.id}`
  expect((await request.post(`${path}/pick`, { headers })).ok()).toBeTruthy()
  const packed = await request.post(`${path}/pack`, {
    headers,
    data: { serialNumbers: { [phone.variantId]: [newImei()] } },
  })
  expect(packed.ok()).toBeTruthy()

  let held: Shipment | undefined
  await expect
    .poll(
      async () => {
        held = await shipmentOf(request, orderId)
        return held.status
      },
      { timeout: 90_000, intervals: [2_000] },
    )
    .toBe('ON_HOLD')
  expect(held!.holdReason).toBe('Capture declined: authorization_expired')
  expect(held!.trackingNumber).toBeNull()

  await page.goto(`/fulfillment/${shipment.id}`)
  await signInOnKeycloak(page)
  const alert = page.getByRole('alert')
  await expect(alert).toContainText("On hold: the Payment couldn't be captured")
  await expect(alert).toContainText(held!.holdReason!)
  await expect(page.getByRole('region', { name: 'Tracking', exact: true })).toHaveCount(0)

  // Under the On hold tab, too.
  const lastPage = await lastQueuePage(request, 'ON_HOLD', undefined)
  // This browser never chose a Warehouse, so the queue shows every one.
  await page.goto(`/fulfillment?status=ON_HOLD${lastPage > 1 ? `&page=${lastPage}` : ''}`)
  await expect(page.getByLabel('Warehouse')).toHaveValue('')
  await expect(
    page.getByRole('table', { name: 'Shipments' }).getByRole('link', { name: `#${orderId.slice(0, 8).toUpperCase()}` }),
  ).toBeVisible()
})

/**
 * Buys one unit of `variantId` as the Customer, through the gateway: Cart, Checkout Session,
 * Berlin, Standard, and pay with `paymentMethod`. A cold Saga may still be processing when the pay
 * call returns, so it asks for the session's payment until it is paid. Returns the Order ID.
 */
async function buy(
  request: APIRequestContext,
  customer: Customer,
  variantId: string,
  paymentMethod: string,
): Promise<string> {
  const headers = bearer(await tokenOf(request, password(customer)))
  const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
    headers,
    data: { quantity: 1 },
  })
  expect(put.ok()).toBeTruthy()
  const started = await request.post('/api/checkout-pricing/checkout/sessions', { headers })
  expect(started.status()).toBe(201)
  const session = `/api/checkout-pricing/checkout/sessions/${((await started.json()) as { id: string }).id}`
  const addressed = await request.put(`${session}/shipping-address`, {
    headers,
    data: {
      recipientName: 'Erika Mustermann',
      line1: 'Hauptstraße 5',
      postcode: '10115',
      city: 'Berlin',
      country: 'DE',
    },
  })
  expect(addressed.ok()).toBeTruthy()
  expect(
    (await request.put(`${session}/shipping-option`, { headers, data: { optionId: 'STANDARD' } })).ok(),
  ).toBeTruthy()

  const paid = await request.post(`${session}/pay`, { headers, data: { paymentMethod }, timeout: 60_000 })
  expect([200, 202]).toContain(paid.status())
  let orderId = ((await paid.json()) as { orderId?: string }).orderId
  if (paid.status() === 202) {
    await expect
      .poll(
        async () => {
          const payment = (await (await request.get(`${session}/payment`, { headers })).json()) as {
            status: string
            orderId?: string
          }
          orderId = payment.orderId
          return payment.status
        },
        { timeout: 60_000, intervals: [2_000] },
      )
      .toBe('PAID')
  }
  expect(orderId).toBeTruthy()
  return orderId!
}

/** The Order's Shipment, read as Staff, once the fulfillment Saga has created it. */
async function shipmentOf(request: APIRequestContext, orderId: string): Promise<Shipment> {
  const headers = bearer(await staffToken(request))
  let shipment: Shipment | undefined
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/fulfillment-shipping/shipments?${new URLSearchParams({ orderId })}`, {
          headers,
        })
        if (response.ok()) shipment = (await response.json()) as Shipment
        return response.status()
      },
      { timeout: 60_000, intervals: [1_000, 2_000] },
    )
    .toBe(200)
  return shipment!
}

/** The last page of a queue tab, from one Warehouse or all, counting from 1. */
async function lastQueuePage(request: APIRequestContext, status: string, warehouseId: string | undefined) {
  const query = new URLSearchParams({ status, size: '20' })
  if (warehouseId) query.set('warehouseId', warehouseId)
  const response = await request.get(`/api/fulfillment-shipping/shipments?${query}`, {
    headers: bearer(await staffToken(request)),
  })
  expect(response.ok()).toBeTruthy()
  return Math.max(1, Math.ceil(((await response.json()) as ListPage<Shipment>).total / 20))
}

/** A seeded phone, IMEI-tracked, whose first Variant has Stock left; earlier runs may have sold some out. */
async function aPhoneInStock(request: APIRequestContext): Promise<{ variantId: string }> {
  const response = await request.get('/api/catalog/products')
  expect(response.ok()).toBeTruthy()
  const phones = ((await response.json()) as { category: string; variants: { id: string }[] }[]).filter(
    (product) => product.category === 'phones',
  )
  for (const { variants } of phones) {
    for (const { id } of variants) {
      const stock = await request.get(`/api/inventory/stock/${encodeURIComponent(id)}`)
      expect(stock.ok()).toBeTruthy()
      if (((await stock.json()) as { quantity: number }).quantity > 0) return { variantId: id }
    }
  }
  throw new Error('No seeded phone has Stock left; run `make seed-reset`')
}

/** A random IMEI with a valid Luhn check digit, so no earlier run has shipped it. */
function newImei(): string {
  const body = Array.from({ length: 14 }, () => Math.floor(Math.random() * 10))
  let sum = 0
  body.forEach((digit, i) => {
    // Doubled from the rightmost of the 14, which sits next to the check digit.
    let value = (body.length - 1 - i) % 2 === 0 ? digit * 2 : digit
    if (value > 9) value -= 9
    sum += value
  })
  return body.join('') + String((10 - (sum % 10)) % 10)
}

async function signInOnKeycloak(page: Page) {
  await page.locator('#username').fill(staff.email)
  await page.locator('#password').fill(staff.password)
  await page.locator('#kc-login').click()
}

function password(user: { email: string; password: string }) {
  return { grant_type: 'password', client_id: 'dev-cli', username: user.email, password: user.password }
}

async function tokenOf(request: APIRequestContext, form: Record<string, string>): Promise<string> {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, { form })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { access_token: string }).access_token
}

function staffToken(request: APIRequestContext) {
  return tokenOf(request, password(staff))
}

function bearer(token: string) {
  return { Authorization: `Bearer ${token}` }
}

/** The token's subject: a Customer's or Staff member's ID. */
function subjectOf(token: string): string {
  return (JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()) as { sub: string }).sub
}

async function keycloakAdminHeaders(request: APIRequestContext) {
  const response = await request.post(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
    form: {
      grant_type: 'password',
      client_id: 'admin-cli',
      username: 'admin',
      password: stackSecret('keycloak-admin-password'),
    },
  })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${((await response.json()) as { access_token: string }).access_token}` }
}

/** A new Customer of the test's own, so its Cart is nobody else's. */
async function createCustomer(request: APIRequestContext): Promise<Customer> {
  const email = `e2e-fulfillment-${run}@ecomm.local`
  const secret = 'e2e-password'
  const response = await request.post(`${keycloakUrl}/admin/realms/ecomm/users`, {
    headers: await keycloakAdminHeaders(request),
    data: {
      username: email,
      email,
      firstName: 'E2E',
      lastName: 'Customer',
      enabled: true,
      emailVerified: true,
      credentials: [{ type: 'password', value: secret, temporary: false }],
    },
  })
  expect(response.status()).toBe(201)
  return { id: response.headers().location.split('/').pop()!, email, password: secret }
}

async function deleteCustomer(request: APIRequestContext, customer: Customer) {
  const response = await request.delete(`${keycloakUrl}/admin/realms/ecomm/users/${customer.id}`, {
    headers: await keycloakAdminHeaders(request),
  })
  expect(response.status()).toBe(204)
}
