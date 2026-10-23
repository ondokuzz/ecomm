import { type APIRequestContext, expect, test } from '@playwright/test'
import type { Page as ListPage } from '../src/domain/paging'
import type { Rma } from '../src/domain/rma'
import { stackSecret } from './secrets'
import {
  type Customer,
  aPhoneInStock,
  bearer,
  buy,
  createCustomer,
  deleteCustomer,
  newImei,
  password,
  shipmentOf,
  signInOnKeycloak,
  staff,
  staffToken,
  subjectOf,
  theOneEmail,
  tokenOf,
} from './stack'

/**
 * Staff work RMAs in the RMA queue. In the first test the demo Customer returns one of two speakers
 * delivered on an Order, and claims the warranty on the other, through the API; Staff approve the
 * return with a note and reject the claim with a reason, which the demo Customer is emailed, and the
 * Order page lists both. In the second a Customer of the test's own buys a phone and returns it;
 * Staff receive it, a wrong IMEI refused, inspect it, and see the returns Saga's Refund. The
 * Customer is emailed the request, the approval with the return address and the receipt, and the
 * RMA page lists those Notifications.
 *
 * The Order and its Shipment are made as the checkout and fulfillment Sagas would, straight at
 * Order Management and Fulfillment & Shipping, since the gateway routes neither placing an Order
 * nor creating or dispatching a Shipment. The Order stays placed, never paid, so no Saga takes it
 * up. Staff pick and pack it through the gateway, and the mock carrier delivers it about 30 seconds
 * after dispatch. Orders, Shipments and RMAs are never deleted; each run ships Serial numbers of
 * its own.
 */

/** Order Management's compose host port, which bypasses the gateway, for the checkout Saga's commands. */
const orderManagementUrl = process.env.ORDER_MANAGEMENT_URL ?? 'http://localhost:8085'
/** Fulfillment & Shipping's compose host port, likewise, for the fulfillment Saga's commands. */
const fulfillmentUrl = process.env.FULFILLMENT_SHIPPING_URL ?? 'http://localhost:8099'
const demo = { email: 'demo@ecomm.local', password: 'demo' }
const orchestrationClientSecret = stackSecret('orchestration-client-secret')

/** A serial-tracked Product with 12 months of Warranty terms from JBL. */
const speaker = { variantId: 'AUD-JBL-FLIP-6', productName: 'JBL Flip 6', price: 12900 }

test('Staff approve a return and reject a warranty claim from the RMA queue', async ({ page, request }) => {
  // Waiting for the mock carrier's delivery and Returns & Warranty's projection of it.
  test.setTimeout(180_000)
  const run = `${Date.now().toString(36)}${test.info().workerIndex}`.toUpperCase()
  const serials = [`E2E-${run}-1`, `E2E-${run}-2`]
  const { orderId } = await deliverTwoSpeakers(request, run, serials)

  const customer = await tokenOf(request, password(demo))
  const returned = await requestRma(request, customer, {
    orderId,
    variantId: speaker.variantId,
    quantity: 1,
    serialNumbers: [serials[0]],
    kind: 'RETURN',
    reason: 'Not loud enough',
  })
  const claimed = await requestRma(request, customer, {
    orderId,
    variantId: speaker.variantId,
    quantity: 1,
    serialNumbers: [serials[1]],
    kind: 'WARRANTY_CLAIM',
    reason: 'It stopped charging',
  })
  const orderReference = orderId.slice(0, 8).toUpperCase()

  // The queue is oldest first, so the newest request is on its last page.
  const staffToken = await tokenOf(request, password(staff))
  const queue = await request.get('/api/returns-warranty/rmas?status=REQUESTED&size=20', {
    headers: bearer(staffToken),
  })
  expect(queue.ok()).toBeTruthy()
  const lastPage = Math.max(1, Math.ceil(((await queue.json()) as ListPage<Rma>).total / 20))
  await page.goto(lastPage > 1 ? `/rmas?page=${lastPage}` : '/rmas')
  await signInOnKeycloak(page)
  await expect(page.getByRole('heading', { name: 'RMAs' })).toBeVisible()
  const tabs = page.getByRole('navigation', { name: 'RMA stages' })
  await expect(tabs.getByRole('link', { name: /^Requested/ })).toHaveAttribute('aria-current', 'page')
  for (const tab of ['Requested', 'Awaiting units', 'Received', 'On hold', 'Closed']) {
    await expect(tabs.getByRole('link', { name: new RegExp(`^${tab} \\d+$`) })).toBeVisible()
  }
  const row = page.getByRole('row').filter({ hasText: returned.reference })
  await expect(row).toContainText('Return')
  await expect(row).toContainText(`#${orderReference}`)
  await expect(row).toContainText(speaker.productName)
  await expect(row).toContainText(/just now|minutes? ago/)

  // The return: what it claims and the window it was checked against, approved with a note.
  await row.getByRole('link', { name: returned.reference }).click()
  await expect(page).toHaveURL(new RegExp(`/rmas/${returned.id}$`))
  await expect(page.getByRole('heading', { name: `Return ${returned.reference}` })).toBeVisible()
  const facts = page.getByRole('region', { name: 'About this RMA' })
  await expect(facts.getByRole('link', { name: `#${orderReference}` })).toBeVisible()
  await expect(facts).toContainText(returned.customerId)
  await expect(facts).toContainText(speaker.productName)
  await expect(facts).toContainText(serials[0])
  await expect(facts).toContainText('Not loud enough')
  const window = page.getByRole('region', { name: 'Checked against' })
  await expect(window).toContainText('Return Period')
  await expect(window.locator('time').last()).toHaveAttribute('datetime', returned.window.endsAt)
  const requested = page.getByRole('table', { name: 'RMA history' }).getByRole('row')
  await expect(requested).toHaveCount(2)
  await expect(requested.nth(1)).toContainText('Requested')
  await expect(requested.nth(1)).toContainText('Customer')

  await page.getByLabel('Note').fill('Send it to the Berlin Warehouse')
  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(page.getByRole('main').getByText('Approved', { exact: true }).first()).toBeVisible()
  const approval = page.getByRole('table', { name: 'RMA history' }).getByRole('row')
  await expect(approval).toHaveCount(3)
  await expect(approval.nth(1)).toContainText('Approved')
  await expect(approval.nth(1)).toContainText('Send it to the Berlin Warehouse')
  await expect(approval.nth(1)).toContainText(subjectOf(staffToken))
  // Decided once: nothing on the page decides it again.
  await expect(page.getByRole('button', { name: 'Approve' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Reject' })).toHaveCount(0)

  // The warranty claim: its Warranty Window, refused without a reason, then rejected with one.
  await page.goto(`/rmas/${claimed.id}`)
  await expect(page.getByRole('heading', { name: `Warranty claim ${claimed.reference}` })).toBeVisible()
  const warranty = page.getByRole('region', { name: 'Checked against' })
  await expect(warranty).toContainText('Warranty Window')
  await expect(warranty).toContainText('12 months, from JBL')
  await page.getByRole('button', { name: 'Reject' }).click()
  await expect(page.getByText('Say why it is rejected: the Customer is told.')).toBeVisible()
  await expect(page.getByLabel('Reason')).toHaveAttribute('aria-invalid', 'true')
  await page.getByLabel('Reason').fill('Water damage is not covered')
  await page.getByRole('button', { name: 'Reject' }).click()
  const rejection = page.getByRole('table', { name: 'RMA history' }).getByRole('row')
  await expect(rejection).toHaveCount(3)
  await expect(rejection.nth(1)).toContainText('Rejected')
  await expect(rejection.nth(1)).toContainText('Water damage is not covered')
  await expect(page.getByRole('button', { name: 'Reject' })).toHaveCount(0)

  // Notifications emailed the demo Customer the rejection, with its reason, once.
  const rejected = await theOneEmail(request, `Your warranty claim ${claimed.reference} was not approved`)
  expect(rejected.To.map((to) => to.Address)).toEqual([demo.email])
  expect(rejected.Text).toContain('Water damage is not covered')

  // The rejected one is closed.
  await page.getByRole('main').getByRole('link', { name: 'RMAs' }).click()
  await page
    .getByRole('navigation', { name: 'RMA stages' })
    .getByRole('link', { name: /^Closed/ })
    .click()
  await expect(page).toHaveURL(/\/rmas\?tab=closed$/)
  await expect(page.getByText('oldest request first')).toBeVisible()

  // The Order lists both, with their statuses.
  await page.goto(`/orders/${orderId}`)
  const rmas = page.getByRole('table', { name: 'RMAs' })
  await expect(rmas.getByRole('row').filter({ hasText: returned.reference })).toContainText('Approved')
  await expect(rmas.getByRole('row').filter({ hasText: claimed.reference })).toContainText('Rejected')
  await rmas.getByRole('link', { name: claimed.reference }).click()
  await expect(page).toHaveURL(new RegExp(`/rmas/${claimed.id}$`))
})

let customer: Customer | undefined

test.afterAll(async ({ request }) => {
  if (customer) await deleteCustomer(request, customer)
})

test('Staff receive a returned phone, refused a wrong IMEI, inspect it, and see the returns Saga refund it', async ({
  page,
  request,
}) => {
  // Checkout, the fulfillment Saga and the mock carrier's delivery, then the returns Saga's Refund.
  test.setTimeout(300_000)
  customer = await createCustomer(request, 'rmas')
  const phone = await aPhoneInStock(request)
  const orderId = await buy(request, customer, phone.variantId, 'tok_approve')
  const imei = newImei()
  await shipAndDeliver(request, orderId, phone.variantId, imei)

  const returned = await requestRma(request, await tokenOf(request, password(customer)), {
    orderId,
    variantId: phone.variantId,
    quantity: 1,
    serialNumbers: [imei],
    kind: 'RETURN',
    reason: 'Changed my mind',
  })
  // Notifications emails the Customer each step, once; Mailpit catches each.
  const requestedEmail = await theOneEmail(request, `We've received your return request ${returned.reference}`)
  expect(requestedEmail.To.map((to) => to.Address)).toEqual([customer.email])
  const staffHeaders = bearer(await staffToken(request))
  const approved = await request.post(`/api/returns-warranty/rmas/${returned.id}/approve`, {
    headers: staffHeaders,
    data: {},
  })
  expect(approved.ok()).toBeTruthy()
  const rma = (await approved.json()) as Rma
  const sendBackBy = new Date(rma.sendBackBy!).toLocaleDateString('en-US', { dateStyle: 'long', timeZone: 'UTC' })
  const approvedEmail = await theOneEmail(
    request,
    `Your return ${rma.reference} is approved: send it back by ${sendBackBy}`,
  )
  // WH-BER's Return address, which Notifications read from Fulfillment & Shipping.
  expect(approvedEmail.Text).toContain('Ecomm Returns Berlin\nLagerstraße 3\n10115 Berlin\nGermany')
  expect(approvedEmail.Text).toContain(`Write ${rma.reference} on the parcel`)

  // Awaiting its units: the Send-back deadline, the Warehouse they go back to and the Refund amount.
  await page.goto(`/rmas/${rma.id}`)
  await signInOnKeycloak(page)
  await expect(page.getByRole('heading', { name: `Return ${rma.reference}` })).toBeVisible()
  const facts = page.getByRole('region', { name: 'About this RMA' })
  // To the second: the approval's answer has more of its fraction than Postgres keeps.
  await expect(facts.locator(`time[datetime^="${rma.sendBackBy!.slice(0, 19)}"]`)).toBeVisible()
  await expect(facts).toContainText('WH-BER')
  await expect(facts).toContainText(euros(rma.refundAmount!.amountMinor))

  // A unit that isn't the RMA's is refused as it is typed, and Receive sends nothing.
  const receive = page.getByRole('form', { name: 'Receive' })
  const serialInput = receive.getByLabel('Serial number', { exact: true })
  await serialInput.fill(newImei())
  await expect(receive.getByText('Not one of this RMA’s Serial numbers.')).toBeVisible()
  await expect(serialInput).toHaveAttribute('aria-invalid', 'true')
  await receive.getByRole('button', { name: 'Receive' }).click()
  await expect(page.getByRole('table', { name: 'RMA history' }).getByRole('row')).toHaveCount(3)

  // The right one receives it.
  await serialInput.fill(imei)
  await expect(serialInput).not.toHaveAttribute('aria-invalid', 'true')
  await receive.getByRole('button', { name: 'Receive' }).click()
  const history = page.getByRole('table', { name: 'RMA history' }).getByRole('row')
  await expect(history).toHaveCount(4)
  await expect(history.nth(1)).toContainText('Received')
  await expect(history.nth(1)).toContainText(subjectOf(await staffToken(request)))
  await expect(page.getByRole('form', { name: 'Receive' })).toHaveCount(0)
  await theOneEmail(request, `We've received your return ${rma.reference}`)

  // Inspected as accepted, the returns Saga refunds it.
  const inspect = page.getByRole('form', { name: 'Inspect' })
  await inspect.getByLabel('Accepted').check()
  await inspect.getByRole('button', { name: 'Record the Inspection' }).click()
  await expect(page.locator('.page-heading')).toContainText('Refunded', { timeout: 90_000 })
  const refunded = (await (
    await request.get(`/api/returns-warranty/rmas/${rma.id}`, { headers: bearer(await staffToken(request)) })
  ).json()) as Rma
  const reference = refunded.resolution!.paymentTransactionReference!
  const resolution = page.getByRole('region', { name: 'Resolution' })
  await expect(resolution).toContainText(euros(rma.refundAmount!.amountMinor))
  await expect(resolution).toContainText(reference)
  await expect(history.nth(1)).toContainText('Returns Saga')

  // The RMA page lists what its Customer was emailed about it, newest first; the Refund is told
  // about the Order, by PAYMENT_REFUNDED, not about the RMA.
  await page.reload()
  const notifications = page.getByRole('table', { name: 'Notifications' })
  await expect(notifications.getByRole('row')).toHaveCount(4)
  for (const [i, type] of ['RMA received', 'RMA approved', 'RMA requested'].entries()) {
    const row = notifications.getByRole('row').nth(i + 1)
    await expect(row).toContainText(type)
    await expect(row).toContainText('Email')
    await expect(row).toContainText('Sent')
    await expect(row).toContainText(`${customer.email[0]}***@ecomm.local`)
  }
  const approvedRow = notifications.getByRole('row').nth(2)
  await approvedRow.getByText('Message').click()
  await expect(
    page.frameLocator('iframe[title="RMA approved: as HTML"]').getByText('Ecomm Returns Berlin'),
  ).toBeVisible()

  // The Order lists the RMA as refunded, and the Refund it was given.
  await facts.getByRole('link', { name: `#${orderId.slice(0, 8).toUpperCase()}` }).click()
  await expect(
    page.getByRole('table', { name: 'RMAs' }).getByRole('row').filter({ hasText: rma.reference }),
  ).toContainText('Refunded')
  const refund = page.getByRole('table', { name: 'Refunds' }).getByRole('row').filter({ hasText: rma.reference })
  await expect(refund).toContainText('Approved')
  await expect(refund).toContainText(reference)
  await expect(refund).toContainText(euros(rma.refundAmount!.amountMinor))
})

/** An amount in euros as the console shows it in the browser's locale, en-US: `€799.00`. */
function euros(amountMinor: number): string {
  return (amountMinor / 100).toLocaleString('en-US', { style: 'currency', currency: 'EUR' })
}

/**
 * Picks and packs the Order's Shipment as Staff, with `imei`, and waits while the fulfillment Saga
 * captures the Payment and dispatches it, and the mock carrier delivers it.
 */
async function shipAndDeliver(request: APIRequestContext, orderId: string, variantId: string, imei: string) {
  const shipment = await shipmentOf(request, orderId)
  const headers = bearer(await staffToken(request))
  const path = `/api/fulfillment-shipping/shipments/${shipment.id}`
  expect((await request.post(`${path}/pick`, { headers })).ok()).toBeTruthy()
  const packed = await request.post(`${path}/pack`, { headers, data: { serialNumbers: { [variantId]: [imei] } } })
  expect(packed.ok()).toBeTruthy()
  await expect
    .poll(async () => (await shipmentOf(request, orderId)).status, { timeout: 150_000, intervals: [2_000] })
    .toBe('DELIVERED')
}

/**
 * Places an Order of two speakers for the demo Customer, then creates its Shipment, picks it, packs
 * it with `serials` and dispatches it, as the Sagas and Staff would, and waits until it is delivered.
 */
async function deliverTwoSpeakers(
  request: APIRequestContext,
  run: string,
  serials: string[],
): Promise<{ orderId: string }> {
  const customerId = subjectOf(await tokenOf(request, password(demo)))
  const orchestration = bearer(
    await tokenOf(request, {
      grant_type: 'client_credentials',
      client_id: 'orchestration',
      client_secret: orchestrationClientSecret,
    }),
  )
  const staffHeaders = bearer(await tokenOf(request, password(staff)))
  const key = (step: string) => `e2e-rma-${run}:${step}`
  const lines = [{ variantId: speaker.variantId, quantity: 2 }]

  const placed = await request.post(`${orderManagementUrl}/orders`, {
    headers: { ...orchestration, 'Idempotency-Key': key('placeOrder') },
    data: {
      customerId,
      lines: lines.map((line) => ({ ...line, unitPrice: { amountMinor: speaker.price, currency: 'EUR' } })),
      tax: { amountMinor: 0, currency: 'EUR' },
      ...shipping,
    },
  })
  expect(placed.status()).toBe(201)
  const { id: orderId } = (await placed.json()) as { id: string }

  // 503 while Fulfillment & Shipping's Product projection catches up, as the Saga retries.
  let shipmentId = ''
  await expect
    .poll(
      async () => {
        const created = await request.post(`${fulfillmentUrl}/shipments`, {
          headers: { ...orchestration, 'Idempotency-Key': key('createShipment') },
          data: { orderId, customerId, lines, ...shipping },
        })
        if (created.status() === 201) shipmentId = ((await created.json()) as { id: string }).id
        return created.status()
      },
      { timeout: 30_000 },
    )
    .toBe(201)

  const shipment = `/api/fulfillment-shipping/shipments/${shipmentId}`
  expect((await request.post(`${shipment}/pick`, { headers: staffHeaders })).ok()).toBeTruthy()
  const packed = await request.post(`${shipment}/pack`, {
    headers: staffHeaders,
    data: { serialNumbers: { [speaker.variantId]: serials } },
  })
  expect(packed.ok()).toBeTruthy()
  const dispatched = await request.post(`${fulfillmentUrl}/shipments/${shipmentId}/dispatch`, {
    headers: { ...orchestration, 'Idempotency-Key': key('dispatch') },
  })
  expect(dispatched.ok()).toBeTruthy()

  await expect
    .poll(
      async () => {
        const read = await request.get(shipment, { headers: staffHeaders })
        return ((await read.json()) as { status: string }).status
      },
      { timeout: 90_000, intervals: [2_000] },
    )
    .toBe('DELIVERED')
  return { orderId }
}

/**
 * Requests an RMA as the Customer whose token is given, through the gateway. A 404 means Returns &
 * Warranty hasn't projected the delivery yet, so it asks again until it has.
 */
async function requestRma(request: APIRequestContext, token: string, rma: object): Promise<Rma> {
  let requested: Rma | undefined
  await expect
    .poll(
      async () => {
        const response = await request.post('/api/returns-warranty/rmas', {
          headers: { ...bearer(token), 'Idempotency-Key': crypto.randomUUID() },
          data: rma,
        })
        if (response.status() === 201) requested = (await response.json()) as Rma
        return response.status()
      },
      { timeout: 30_000 },
    )
    .toBe(201)
  return requested!
}

/** The Shipping every Order is placed with since Sprint 5: Berlin, Standard at 4.99. */
const shipping = {
  shippingAddress: {
    recipientName: 'Erika Mustermann',
    line1: 'Hauptstraße 5',
    postcode: '10115',
    city: 'Berlin',
    country: 'DE',
  },
  shippingOption: { id: 'STANDARD', carrier: 'Mock Carrier', cost: { amountMinor: 499, currency: 'EUR' } },
}
