import { type APIRequestContext, expect, test } from '@playwright/test'
import type { Page as ListPage } from '../src/domain/paging'
import type { Shipment } from '../src/domain/shipment'
import {
  type Customer,
  aPhoneInStock,
  bearer,
  buy,
  createCustomer,
  deleteCustomer,
  newImei,
  shipmentOf,
  signInOnKeycloak,
  staffToken,
  subjectOf,
  theOneEmail,
} from './stack'

/**
 * Staff work the fulfillment queue. A Customer of the test's own buys a phone, shipped to Berlin,
 * through the gateway as the Storefront would; the checkout Saga marks the Order paid, and the
 * fulfillment Saga creates its Shipment. Staff find it in the queue under the Berlin Warehouse,
 * pick it, have an invalid IMEI refused, pack it with a valid one, and see the fulfillment Saga
 * capture the Payment and dispatch it with a tracking number. Notifications emails the Customer that
 * it shipped, once, and the Order page lists what was sent. A second Order, paid with
 * `tok_capture_decline`, is authorized but refused at capture, so its packed Shipment goes on hold
 * with the payment gateway's reason.
 *
 * Orders and Shipments are never deleted; each run ships IMEIs of its own. The Customer is deleted.
 */

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
  customer = await createCustomer(request, 'fulfillment')
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

  // Notifications emailed the Customer that it shipped, with its tracking number, once; Mailpit
  // caught it, and the Order page lists it under the confirmation.
  const shipped = await theOneEmail(request, `Your Order ${reference} is on its way`)
  expect(shipped.To.map((to) => to.Address)).toEqual([customer.email])
  expect(shipped.Text).toContain(dispatched.trackingNumber!)
  const notifications = page.getByRole('table', { name: 'Notifications' })
  const shippedRow = notifications.getByRole('row').filter({ hasText: 'Order shipped' })
  await expect(shippedRow).toContainText('Email')
  await expect(shippedRow).toContainText('Sent')
  await expect(shippedRow).toContainText(`${customer.email[0]}***@ecomm.local`)
  await shippedRow.getByText('Message').click()
  await expect(shippedRow).toContainText(`Subject: Your Order ${reference} is on its way`)
  await expect(
    page.frameLocator('iframe[title="Order shipped: as HTML"]').getByText(dispatched.trackingNumber!),
  ).toBeVisible()
  await expect(notifications.getByRole('row').filter({ hasText: 'Order confirmed' })).toContainText('Sent')

  await onOrder.getByRole('link', { name: 'Open the Shipment' }).click()
  await expect(page).toHaveURL(new RegExp(`/fulfillment/${shipment.id}$`))
})

test('a Shipment whose Payment is refused at capture goes on hold with the reason', async ({ page, request }) => {
  test.setTimeout(180_000)
  customer ??= await createCustomer(request, 'fulfillment')
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
