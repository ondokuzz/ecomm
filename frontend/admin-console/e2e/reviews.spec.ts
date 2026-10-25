import { type APIRequestContext, expect, test } from '@playwright/test'
import type { Page as ListPage } from '../src/domain/paging'
import { type ModeratedReview, moderationPageSize } from '../src/domain/review'
import {
  type Customer,
  aPhoneInStock,
  bearer,
  buy,
  createCustomer,
  deleteCustomer,
  password,
  signInOnKeycloak,
  staffToken,
  tokenOf,
} from './stack'

/**
 * Staff work the Review moderation queue. Two Customers of the test's own buy the same phone and
 * review it through the gateway: the first's review trips the word screen and is held, the
 * second's is published, and the first reports it. Staff publish the held review, which then shows
 * on the Product, and remove the reported one with a reason, which its author then sees. Both
 * reviews are deleted by their authors afterwards, and the Customers with them; the Orders stay.
 */

interface Review {
  id: string
  sku: string
  status: string
  removalReason: string | null
}

test('Staff publish a held Review and remove a reported one', async ({ page, request }) => {
  // Two checkouts, and Reviews learning of each paid Order from its event.
  test.setTimeout(180_000)
  const held = await createCustomer(request, 'held-review')
  const reported = await createCustomer(request, 'reported-review')
  const posted: { customer: Customer; review: Review }[] = []
  try {
    const { variantId } = await aPhoneInStock(request)
    await buy(request, held, variantId, 'tok_approve')
    await buy(request, reported, variantId, 'tok_approve')
    const sku = await skuOf(request, variantId)

    const heldReview = await postReview(request, held, sku, 'Not a scam after all', 'It does what it says.')
    posted.push({ customer: held, review: heldReview })
    expect(heldReview.status).toBe('HELD')
    const reportedReview = await postReview(request, reported, sku, null, 'The seller ignored every email I sent.')
    posted.push({ customer: reported, review: reportedReview })
    expect(reportedReview.status).toBe('PUBLISHED')
    const report = await request.post(`/api/reviews-ratings/reviews/${reportedReview.id}/reports`, {
      headers: bearer(await tokenOf(request, password(held))),
      data: { reason: 'OFF_TOPIC', note: 'About the seller, not the phone.' },
    })
    expect(report.status()).toBe(201)

    // The held review is in the queue, on the page its age puts it.
    await page.goto(`/reviews${await queuePageOf(request, heldReview.id)}`)
    await signInOnKeycloak(page)
    const queue = page.getByRole('table', { name: 'Reviews to moderate' })
    const heldRow = queue.getByRole('row').filter({ hasText: 'Not a scam after all' })
    await expect(heldRow).toContainText('Held')
    await expect(heldRow).toContainText('None')
    await heldRow.getByRole('link', { name: 'Not a scam after all' }).click()

    await expect(page.getByRole('heading', { name: 'Review by E2E C.' })).toBeVisible()
    await expect(page.locator('.customer-reason')).toHaveText('It does what it says.')
    await page.getByRole('button', { name: 'Publish' }).click()
    const history = page.getByRole('table', { name: 'Moderation history' })
    await expect(history.getByRole('row').nth(1)).toContainText('Published')
    await expect(page.locator('.page-heading')).toContainText('Published')
    await expect(page.getByRole('button', { name: 'Publish' })).toHaveCount(0)
    await expect.poll(() => publishedIds(request, sku)).toContain(heldReview.id)

    // The reported review is in the queue with its report.
    await page.goto(`/reviews${await queuePageOf(request, reportedReview.id)}`)
    const reportedRow = queue.getByRole('row').filter({ hasText: 'The seller ignored every email' })
    await expect(reportedRow).toContainText('Published')
    await expect(reportedRow).toContainText('1 off-topic')
    await reportedRow.getByRole('link').click()

    const reports = page.getByRole('table', { name: 'Reports' })
    await expect(reports).toContainText('Off-topic')
    await expect(reports).toContainText('About the seller, not the phone.')
    await page.getByRole('button', { name: 'Remove' }).click()
    await expect(page.getByText('Say why it is removed: its author is told.')).toBeVisible()
    await page.getByLabel('Reason').fill('Reviews are of the product, not of the seller.')
    await page.getByRole('button', { name: 'Remove' }).click()
    await expect(page.locator('.page-heading')).toContainText('Removed')
    await expect(history.getByRole('row').nth(1)).toContainText('Reviews are of the product, not of the seller.')
    await expect(reports).toContainText('Resolved')
    await expect(page.getByRole('heading', { name: 'Moderate' })).toHaveCount(0)

    expect(await publishedIds(request, sku)).not.toContain(reportedReview.id)
    const own = await eligibilityOf(request, reported, sku)
    expect(own.review.status).toBe('REMOVED')
    expect(own.review.removalReason).toBe('Reviews are of the product, not of the seller.')
  } finally {
    // Everything is deleted before any of it is checked, so one failure leaves nothing else behind.
    const deleted: number[] = []
    for (const { customer, review } of posted) {
      const response = await request.delete(`/api/reviews-ratings/reviews/${review.id}`, {
        headers: bearer(await tokenOf(request, password(customer))),
      })
      deleted.push(response.status())
    }
    await deleteCustomer(request, held)
    await deleteCustomer(request, reported)
    expect(deleted).toEqual(posted.map(() => 204))
  }
})

async function skuOf(request: APIRequestContext, variantId: string): Promise<string> {
  const products = (await (await request.get('/api/catalog/products')).json()) as {
    sku: string
    variants: { id: string }[]
  }[]
  return products.find((product) => product.variants.some((v) => v.id === variantId))!.sku
}

async function eligibilityOf(
  request: APIRequestContext,
  customer: Customer,
  sku: string,
): Promise<{ eligible: boolean; review: Review }> {
  const response = await request.get(`/api/reviews-ratings/products/${encodeURIComponent(sku)}/eligibility`, {
    headers: bearer(await tokenOf(request, password(customer))),
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as { eligible: boolean; review: Review }
}

/** Posts the Customer's review, once Reviews has learnt of their paid Order. */
async function postReview(
  request: APIRequestContext,
  customer: Customer,
  sku: string,
  title: string | null,
  body: string,
): Promise<Review> {
  await expect.poll(async () => (await eligibilityOf(request, customer, sku)).eligible, { timeout: 60_000 }).toBe(true)
  const response = await request.post(`/api/reviews-ratings/products/${encodeURIComponent(sku)}/reviews`, {
    headers: bearer(await tokenOf(request, { ...password(customer), scope: 'openid profile' })),
    data: { rating: 4, title, body },
  })
  expect(response.status()).toBe(201)
  return (await response.json()) as Review
}

/** The Product's published review IDs, as anyone reads them. */
async function publishedIds(request: APIRequestContext, sku: string): Promise<string[]> {
  const response = await request.get(`/api/reviews-ratings/products/${encodeURIComponent(sku)}/reviews?size=50`)
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as ListPage<Review>).items.map((review) => review.id)
}

/** The queue's `?page=` that lists the review, as the console pages it. */
async function queuePageOf(request: APIRequestContext, reviewId: string): Promise<string> {
  const headers = bearer(await staffToken(request))
  for (let page = 0; ; page++) {
    const response = await request.get(
      `/api/reviews-ratings/moderation/reviews?page=${page}&size=${moderationPageSize}`,
      {
        headers,
      },
    )
    expect(response.ok()).toBeTruthy()
    const { items } = (await response.json()) as ListPage<ModeratedReview>
    if (items.some((review) => review.id === reviewId)) return page === 0 ? '' : `?page=${page}`
    if (items.length < moderationPageSize) throw new Error(`Review ${reviewId} is not in the moderation queue`)
  }
}
