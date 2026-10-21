import { type APIRequestContext, type Page, expect } from '@playwright/test'
import type { Shipment } from '../src/domain/shipment'
import { stackSecret } from './secrets'

/** What the suites share: signing in, tokens, Customers of their own, and buying a phone through the gateway. */

export const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
export const staff = { email: 'staff@ecomm.local', password: 'staff' }
const run = `${Date.now().toString(36)}${process.pid.toString(36)}`

export interface Customer {
  id: string
  email: string
  password: string
}

/**
 * Buys one unit of `variantId` as the Customer, through the gateway: Cart, Checkout Session,
 * Berlin, Standard, and pay with `paymentMethod`. A cold Saga may still be processing when the pay
 * call returns, so it asks for the session's payment until it is paid. Returns the Order ID.
 */
export async function buy(
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
export async function shipmentOf(request: APIRequestContext, orderId: string): Promise<Shipment> {
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

/** A seeded phone, IMEI-tracked, whose first Variant has Stock left; earlier runs may have sold some out. */
export async function aPhoneInStock(request: APIRequestContext): Promise<{ variantId: string }> {
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
export function newImei(): string {
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

export async function signInOnKeycloak(page: Page) {
  await page.locator('#username').fill(staff.email)
  await page.locator('#password').fill(staff.password)
  await page.locator('#kc-login').click()
}

export function password(user: { email: string; password: string }) {
  return { grant_type: 'password', client_id: 'dev-cli', username: user.email, password: user.password }
}

export async function tokenOf(request: APIRequestContext, form: Record<string, string>): Promise<string> {
  const response = await request.post(`${keycloakUrl}/realms/ecomm/protocol/openid-connect/token`, { form })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { access_token: string }).access_token
}

export function staffToken(request: APIRequestContext) {
  return tokenOf(request, password(staff))
}

export function bearer(token: string) {
  return { Authorization: `Bearer ${token}` }
}

/** The token's subject: a Customer's or Staff member's ID. */
export function subjectOf(token: string): string {
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

/** A new Customer of the test's own, `e2e-<prefix>-…`, so its Cart is nobody else's. */
export async function createCustomer(request: APIRequestContext, prefix: string): Promise<Customer> {
  const email = `e2e-${prefix}-${run}@ecomm.local`
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

export async function deleteCustomer(request: APIRequestContext, customer: Customer) {
  const response = await request.delete(`${keycloakUrl}/admin/realms/ecomm/users/${customer.id}`, {
    headers: await keycloakAdminHeaders(request),
  })
  expect(response.status()).toBe(204)
}
