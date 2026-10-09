import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import { type Product, type Variant, defaultVariant } from '../src/domain/catalog'
import { type Currencies, type Currency, type Money, currenciesOf, formatMoney } from '../src/domain/money'
import { type Order, ordersPageSize } from '../src/domain/order'
import type { Page as ListPage } from '../src/domain/paging'
import type { Eligibility, RatingSummary } from '../src/domain/reviews'
import type { SearchResults } from '../src/domain/search'

/**
 * The smoke test: the definitions of done of Sprints 1 to 4, end to end, each test as a Customer of
 * its own, made in Keycloak for the test and deleted afterwards with their reviews. The Orders stay,
 * as Orders are never deleted.
 *
 * - Browse seeded Products, pick a Variant, add them to the Cart, check out with the mock payment,
 *   and see the Order confirmation and its Order Status. Checkout holds the Cart in a Checkout
 *   Session first, and paying takes the held Stock off on-hand. A declined test card says so, and
 *   the held session can then be paid with one that approves.
 * - Find an audio Product by searching and filtering, check out with the seeded Audio week Campaign
 *   applied by itself and WELCOME10 on top, pay, follow the Order's Status history, and review the
 *   Product, whose rating then shows on its card and its page. A Customer who never bought a
 *   Product is told they can't review it.
 * - Pay with a card the bank confirms later: the page says the payment is being confirmed until the
 *   gateway's webhook settles it, then shows the paid Order; or, declined, shows why, and the held
 *   session is then paid with a card that approves.
 */

// The first checkout compares Stock before and after, so nothing else may check out meanwhile:
// every test in this suite that checks out is in this file. The Admin Console's suite checks out
// too, so the two run one after the other, as in CI.
test.describe.configure({ mode: 'serial' })

/** A Variant's Stock as Inventory reports it: what's left to sell, and the units on hand. */
interface Stock {
  quantity: number
  onHand: number
}

interface Credentials {
  email: string
  password: string
}

/** A Customer made in Keycloak for one test. */
interface Customer extends Credentials {
  id: string
}

interface ProductInStock {
  product: Product
  variant: Variant
  variantId: string
  stock: Stock
  /** The axis value to pick on the Product page to reach `variant`; none for the default Variant. */
  pick?: { axis: string; value: string }
}

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'

let customer: Customer | undefined

test.beforeEach(async ({ request }) => {
  customer = await createCustomer(request)
})

test.afterEach(async ({ request }) => {
  if (customer) await deleteCustomer(request, customer)
  customer = undefined
})

/** The test's Customer, which `beforeEach` made. */
function signedUp(): Customer {
  if (!customer) throw new Error('No Customer was made for this test')
  return customer
}

test('a Customer checks out a picked Variant and another Product, and on-hand Stock goes down', async ({
  page,
  request,
}) => {
  const customer = signedUp()
  const token = await tokenFor(request, customer)
  const picked = await aNonDefaultVariantInStock(request)
  const bought = [picked, await aProductInStock(request, picked.product.sku)]
  const currencies = await currenciesOfCatalog(request)

  await page.goto('/')
  await page.getByRole('button', { name: 'Log in' }).click()
  await signInOnKeycloak(page, customer)
  await expectSignedIn(page, customer.email)

  // Variant Prices differ, so the card shows the lowest, "from".
  await expect(page.locator(`a[href="/products/${encodeURIComponent(picked.product.sku)}"]`)).toContainText('From')

  for (const [i, { product, variant, pick }] of bought.entries()) {
    await page.getByRole('navigation', { name: 'Main', exact: true }).getByRole('link', { name: 'Products' }).click()
    await page.locator(`a[href="/products/${encodeURIComponent(product.sku)}"]`).click()
    await expect(page.getByRole('heading', { name: product.name })).toBeVisible()
    if (pick) {
      // The choice lives in the URL, so the radio shows checked once the navigation lands, not on the click itself.
      const option = page.getByRole('group', { name: pick.axis }).getByRole('radio', { name: pick.value })
      await option.click()
      await expect(option).toBeChecked()
      await expect(page).toHaveURL(new RegExp(`[?&]variant=${encodeURIComponent(variant.id)}$`))
      await expect(page.locator('.purchase-panel .price')).toHaveText(formatMoney(variant.price, currencies, 'en-US'))
    }
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
  // Each line names its Product and the Variant's axis values.
  const cartLines = page.getByRole('list', { name: 'Cart lines' })
  for (const line of bought) {
    await expect(cartLines.getByRole('link', { name: lineName(line), exact: true })).toBeVisible()
  }
  await expect(page.getByRole('region', { name: 'Order summary' })).toContainText('2 items')
  await page.getByRole('link', { name: 'Go to checkout' }).click()
  const steps = page.getByRole('navigation', { name: 'Checkout steps' })
  const currentStep = steps.locator('[aria-current=step]')
  // Screen readers hear "(done)" after each step that is done.
  const doneSteps = steps.getByRole('listitem').filter({ hasText: '(done)' })
  await expect(currentStep).toContainText('Payment')
  await expect(doneSteps).toHaveText([/^Cart/])
  // The Checkout Session holds the Cart for 15 minutes: its Stock is reserved, not yet taken.
  await expect(page.getByText(/held until/)).toBeVisible()
  await expect(page.getByRole('timer', { name: 'Time left' })).toHaveText(/^1[45]:\d\d$/)
  const checkoutLines = page.getByRole('list', { name: 'Checkout lines' })
  for (const line of bought) {
    await expect(checkoutLines.getByRole('link', { name: lineName(line), exact: true })).toBeVisible()
    expect(await stockOf(request, line.variantId)).toEqual({
      quantity: line.stock.quantity - 1,
      onHand: line.stock.onHand,
    })
  }
  await testCard(page, 'Approve').check()
  await page.getByRole('button', { name: /^Pay/ }).click()

  await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
  await expect(page.getByText('Thank you! Your order is confirmed.')).toBeVisible()
  await expect(currentStep).toContainText('Done')
  await expect(doneSteps).toHaveText([/^Cart/, /^Payment/, /^Done/])
  // The confetti falls, unless the Customer prefers reduced motion.
  const confetti = page.locator('.confetti')
  await expect(confetti).toBeVisible()
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(confetti).toBeHidden()
  // The badge is how the page renders Order Status PAID; the Order itself must say PAID too.
  await expect(page.locator('.status-paid')).toHaveText('Paid')
  await expect(
    page.getByRole('list', { name: 'Order progress' }).locator('[aria-current=step] .timeline-status'),
  ).toHaveText('Paid')
  const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
  const order = await orderOf(request, token, orderId)
  expect(order.status).toBe('PAID')
  // The timeline is the Order's Status history, each step with when it happened.
  expect(order.statusHistory.map((entry) => entry.status)).toEqual(['PLACED', 'PAID'])
  const progress = page.getByRole('list', { name: 'Order progress' })
  for (const entry of order.statusHistory) {
    await expect(progress.locator(`time[datetime="${entry.at}"]`)).toBeVisible()
  }
  const orderLines = page.getByRole('list', { name: 'Order lines' })
  for (const line of bought) {
    await expect(orderLines.getByRole('link', { name: lineName(line), exact: true })).toBeVisible()
    expect(order.lines.find((orderLine) => orderLine.variantId === line.variantId)?.quantity).toBe(1)
  }

  await page.getByRole('link', { name: 'View my orders' }).click()
  const card = page.locator(`a[href="/orders/${encodeURIComponent(orderId)}"]`)
  await expect(card).toContainText(`Order #${orderId.slice(0, 8).toUpperCase()}`)
  await expect(card.locator('.status-paid')).toHaveText('Paid')
  await expect(card).toContainText('2 items')
  for (const { product } of bought) {
    await expect(card.getByRole('img', { name: product.name, exact: true })).toBeVisible()
  }
  // One Order fits on a page, so there is no pager; paging has a test of its own.
  await expect(page.getByRole('navigation', { name: 'Order pages' })).toBeHidden()

  // Paying committed the Reservation: the units are off on-hand for good.
  for (const { variantId, stock } of bought) {
    expect(await stockOf(request, variantId)).toEqual({ quantity: stock.quantity - 1, onHand: stock.onHand - 1 })
  }
})

test('a declined card shows why, and the held session can then be paid with one that approves', async ({
  page,
  request,
}) => {
  const customer = signedUp()
  const token = await tokenFor(request, customer)
  const { variantId, stock } = await aProductInStock(request)
  await putInCart(request, token, variantId)

  await page.goto('/checkout')
  await signInOnKeycloak(page, customer)
  await expect(page.getByRole('timer', { name: 'Time left' })).toBeVisible()
  const sessionId = await currentSessionId(request, token)

  await testCard(page, 'Decline').check()
  await page.getByRole('button', { name: /^Pay/ }).click()

  const declined = page.getByRole('alert').filter({ hasText: 'Your card was declined' })
  await expect(declined).toContainText('Your bank declined the payment. Try another card.')
  await expect(page).toHaveURL(/\/checkout$/)
  // The decline cancelled that Order, but the session still holds the Stock, so paying again works.
  expect(await currentSessionId(request, token)).toBe(sessionId)
  expect((await stockOf(request, variantId)).quantity).toBe(stock.quantity - 1)

  await testCard(page, 'Approve').check()
  await expect(declined).toBeHidden()
  await page.getByRole('button', { name: /^Pay/ }).click()

  await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
  await expect(page.locator('.status-paid')).toHaveText('Paid')
  const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
  expect((await orderOf(request, token, orderId)).status).toBe('PAID')
  expect(await stockOf(request, variantId)).toEqual({ quantity: stock.quantity - 1, onHand: stock.onHand - 1 })
})

test('WELCOME10 takes 10% off at checkout, and the PAID Order shows the discount', async ({ page, request }) => {
  const customer = signedUp()
  const token = await tokenFor(request, customer)
  // A phone, so the seeded Audio week Campaign takes nothing off.
  const { variantId, variant } = await aProductInStockIn(request, 'phones')
  await putInCart(request, token, variantId)
  const subtotal = variant.price
  const currencies = await currenciesOfCatalog(request)
  // 10%, rounded down to the minor unit.
  const discount = { ...subtotal, amountMinor: Math.floor(subtotal.amountMinor / 10) }
  const discounted = { ...subtotal, amountMinor: subtotal.amountMinor - discount.amountMinor }

  await page.goto('/checkout')
  await signInOnKeycloak(page, customer)
  await expect(page.getByRole('timer', { name: 'Time left' })).toBeVisible()
  const summary = page.getByRole('region', { name: 'Order summary' })
  const couponField = summary.getByLabel('Coupon code')

  // A code Promotions doesn't know says so, next to the field, and changes nothing.
  await couponField.fill('NOT-A-REAL-CODE')
  await summary.getByRole('button', { name: 'Apply' }).click()
  await expect(summary.getByRole('alert')).toHaveText("We don't know that code. Check it and try again.")
  await expect(couponField).toHaveAttribute('aria-invalid', 'true')

  // Codes are matched whatever their case.
  await couponField.fill('welcome10')
  await summary.getByRole('button', { name: 'Apply' }).click()
  await expect(summary).toContainText('WELCOME10 applied')
  await expect(summary.getByText('Coupon: WELCOME10')).toBeVisible()
  await expect(summary).toContainText(
    formatMoney({ ...discount, amountMinor: -discount.amountMinor }, currencies, 'en-US'),
  )
  await expect(page.getByRole('button', { name: `Pay ${formatMoney(discounted, currencies, 'en-US')}` })).toBeVisible()

  // Removing it puts the total back; applying it again takes it off again.
  await summary.getByRole('button', { name: 'Remove coupon WELCOME10' }).click()
  await expect(summary.getByText('Coupon: WELCOME10')).toBeHidden()
  await expect(page.getByRole('button', { name: `Pay ${formatMoney(subtotal, currencies, 'en-US')}` })).toBeVisible()
  await couponField.fill('WELCOME10')
  await summary.getByRole('button', { name: 'Apply' }).click()
  await expect(summary.getByText('Coupon: WELCOME10')).toBeVisible()

  await testCard(page, 'Approve').check()
  await page.getByRole('button', { name: `Pay ${formatMoney(discounted, currencies, 'en-US')}` }).click()

  await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
  await expect(page.locator('.status-paid')).toHaveText('Paid')
  await expect(page.getByText('Coupon: WELCOME10')).toBeVisible()
  const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
  const order = await orderOf(request, token, orderId)
  expect(order.status).toBe('PAID')
  expect(order.discounts).toEqual([
    { source: 'COUPON', couponCode: 'WELCOME10', campaignId: null, campaignName: null, amount: discount },
  ])
  expect(order.total).toEqual({ ...discounted, amountMinor: discounted.amountMinor + order.tax.amountMinor })
})

test('Audio week comes off an audio Product by itself, WELCOME10 on top, and the PAID Order lists both', async ({
  page,
  request,
}) => {
  const customer = signedUp()
  const token = await tokenFor(request, customer)
  const { variantId, variant } = await aProductInStockIn(request, 'audio')
  await putInCart(request, token, variantId)
  const subtotal = variant.price
  const currencies = await currenciesOfCatalog(request)
  // 15% off the audio line, then 10% off what is left, each rounded down to the minor unit.
  const audioWeek = { ...subtotal, amountMinor: Math.floor((subtotal.amountMinor * 15) / 100) }
  const welcome10 = { ...subtotal, amountMinor: Math.floor((subtotal.amountMinor - audioWeek.amountMinor) / 10) }
  const negative = (money: typeof subtotal) => ({ ...money, amountMinor: -money.amountMinor })
  const pay = (amountMinor: number) => `Pay ${formatMoney({ ...subtotal, amountMinor }, currencies, 'en-US')}`

  await page.goto('/checkout')
  await signInOnKeycloak(page, customer)
  const summary = page.getByRole('region', { name: 'Order summary' })
  await expect(summary.getByText('Campaign: Audio week')).toBeVisible()
  await expect(summary).toContainText(formatMoney(negative(audioWeek), currencies, 'en-US'))
  await expect(page.getByRole('button', { name: pay(subtotal.amountMinor - audioWeek.amountMinor) })).toBeVisible()

  await summary.getByLabel('Coupon code').fill('WELCOME10')
  await summary.getByRole('button', { name: 'Apply' }).click()
  await expect(summary.getByText('Coupon: WELCOME10')).toBeVisible()
  await expect(summary.getByText('Campaign: Audio week')).toBeVisible()
  await expect(summary).toContainText(formatMoney(negative(welcome10), currencies, 'en-US'))
  const total = subtotal.amountMinor - audioWeek.amountMinor - welcome10.amountMinor

  await testCard(page, 'Approve').check()
  await page.getByRole('button', { name: pay(total) }).click()

  await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
  await expect(page.getByText('Campaign: Audio week')).toBeVisible()
  await expect(page.getByText('Coupon: WELCOME10')).toBeVisible()
  const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
  const order = await orderOf(request, token, orderId)
  expect(order.status).toBe('PAID')
  expect(order.discounts.map((d) => [d.source, d.campaignName ?? d.couponCode, d.amount])).toEqual([
    ['CAMPAIGN', 'Audio week', audioWeek],
    ['COUPON', 'WELCOME10', welcome10],
  ])
  expect(order.total).toEqual({ ...subtotal, amountMinor: total + order.tax.amountMinor })

  await page.goto('/orders')
  await expect(page.getByText('Saved with Audio week, WELCOME10').first()).toBeVisible()
})

test('a Customer empties their Cart after confirming', async ({ page, request }) => {
  const customer = signedUp()
  const token = await tokenFor(request, customer)
  const { variantId } = await aProductInStock(request)
  const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { quantity: 2 },
  })
  expect(put.ok()).toBeTruthy()

  await page.goto('/cart')
  await signInOnKeycloak(page, customer)
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

test('a new Customer registers on Keycloak and comes back signed in', async ({ page, request, baseURL }) => {
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

  const found = await request.get(
    `${keycloakUrl}/admin/realms/ecomm/users?${new URLSearchParams({ email, exact: 'true' })}`,
    {
      headers: await keycloakAdminHeaders(request),
    },
  )
  const [{ id }] = (await found.json()) as { id: string }[]
  await deleteCustomer(request, { id, email, password: 'smoke-password' })
})

test('a Customer’s Orders come a page at a time, newest first', async ({ page }) => {
  // A new Customer has too few Orders for a second page, so Order Management's answer is stood in for.
  const requested: string[] = []
  await page.route(/\/api\/order-management\/orders\?/, (route) => {
    requested.push(route.request().url())
    return route.fulfill({ json: aPageOfOrders(Number(new URL(route.request().url()).searchParams.get('page'))) })
  })

  await page.goto('/orders')
  await signInOnKeycloak(page, signedUp())
  const pager = page.getByRole('navigation', { name: 'Order pages' })
  await expect(pager).toContainText('Page 1 of 2')
  await expect(page.getByText(`Order #${orderIdOf(0).slice(0, 8).toUpperCase()}`)).toBeVisible()

  await pager.getByRole('link', { name: 'Older orders' }).click()
  await expect(page).toHaveURL(/\/orders\?page=2$/)
  await expect(pager).toContainText('Page 2 of 2')
  await expect(page.getByText(`Order #${orderIdOf(ordersPageSize).slice(0, 8).toUpperCase()}`)).toBeVisible()
  expect(new URL(requested.at(-1)!).searchParams.get('page')).toBe('1')
})

test.describe('Sprint 3', () => {
  test('a Customer finds an audio Product, checks out with Audio week and WELCOME10, and reviews it', async ({
    page,
    request,
  }) => {
    const customer = signedUp()
    const { product, variant } = await aProductInStockIn(request, 'audio')
    const brand = product.attributes.brand
    const type = product.attributes.type
    const currencies = await currenciesOfCatalog(request)

    await page.goto('/')
    await page.getByRole('button', { name: 'Log in' }).click()
    await signInOnKeycloak(page, customer)
    await expectSignedIn(page, customer.email)

    // Into Audio, then its type and what's in stock: each count is Search's for the view it would give.
    await page
      .getByRole('navigation', { name: 'Categories' })
      .getByRole('link', { name: /^Audio/ })
      .click()
    await expect(page).toHaveURL(/\/\?category=audio$/)
    await expectCounts(page, await searchOf(request, { category: 'audio' }), type)
    await page
      .getByRole('group', { name: 'type' })
      .getByRole('checkbox', { name: new RegExp(`^${escape(type)}`) })
      .click()
    await expect(page).toHaveURL(new RegExp(`attr\\.type=${escape(encodeURIComponent(type).replace(/%20/g, '+'))}`))
    await expectCounts(page, await searchOf(request, { category: 'audio', 'attr.type': type }), type)
    await page.getByRole('checkbox', { name: /^In stock only/ }).click()
    await expect(page.getByRole('checkbox', { name: /^In stock only/ })).toBeChecked()
    const filtered = { category: 'audio', 'attr.type': type, inStock: 'true' }
    await expectCounts(page, await searchOf(request, filtered), type)

    // Searching keeps the filters, and finds the Product by its brand.
    await page.getByRole('searchbox', { name: 'Search products' }).fill(brand)
    await page.getByRole('button', { name: 'Search' }).click()
    await expect(page).toHaveURL(/[?&]q=/)
    await expect(page.getByRole('checkbox', { name: /^In stock only/ })).toBeChecked()
    const found = await searchOf(request, { ...filtered, q: brand })
    expect(found.items.map((item) => item.sku)).toContain(product.sku)
    await expect(cards(page).locator('.product-name')).toHaveText(found.items.map((item) => item.name))
    await card(page, product.name).click()

    await expect(page.getByRole('heading', { name: product.name })).toBeVisible()
    await page.getByRole('button', { name: 'Add to cart' }).click()
    await expect(page.getByRole('link', { name: 'Cart (1)' })).toBeVisible()
    await page.getByRole('link', { name: 'Cart (1)' }).click()
    await page.getByRole('link', { name: 'Go to checkout' }).click()

    // Audio week comes off by itself; WELCOME10 then takes 10% off what is left. Each rounds down.
    const subtotal = variant.price
    const audioWeek = { ...subtotal, amountMinor: Math.floor((subtotal.amountMinor * 15) / 100) }
    const welcome10 = { ...subtotal, amountMinor: Math.floor((subtotal.amountMinor - audioWeek.amountMinor) / 10) }
    const summary = page.getByRole('region', { name: 'Order summary' })
    await expect(summary.getByText('Campaign: Audio week')).toBeVisible()
    await expect(summary).toContainText(money(negative(audioWeek), currencies))
    await summary.getByLabel('Coupon code').fill('WELCOME10')
    await summary.getByRole('button', { name: 'Apply' }).click()
    await expect(summary.getByText('Coupon: WELCOME10')).toBeVisible()
    await expect(summary.getByText('Campaign: Audio week')).toBeVisible()
    await expect(summary).toContainText(money(negative(welcome10), currencies))
    const total = { ...subtotal, amountMinor: subtotal.amountMinor - audioWeek.amountMinor - welcome10.amountMinor }

    await testCard(page, 'Approve').check()
    await page.getByRole('button', { name: `Pay ${money(total, currencies)}` }).click()

    // The Order page draws its timeline from the Status history: Placed, then Paid, each with its time.
    await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
    await expect(page.locator('.status-paid')).toHaveText('Paid')
    const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
    const token = await tokenFor(request, customer)
    const order = await orderOf(request, token, orderId)
    expect(order.statusHistory.map((entry) => entry.status)).toEqual(['PLACED', 'PAID'])
    expect(order.discounts.map((d) => d.campaignName ?? d.couponCode)).toEqual(['Audio week', 'WELCOME10'])
    const progress = page.getByRole('list', { name: 'Order progress' })
    await expect(progress.locator('.timeline-done .timeline-status')).toHaveText([/^Placed/])
    await expect(progress.locator('[aria-current=step] .timeline-status')).toHaveText('Paid')
    for (const entry of order.statusHistory) {
      await expect(progress.locator(`time[datetime="${entry.at}"]`)).toBeVisible()
    }

    // Reviews learns of the paid Order from its event, within seconds.
    await expect
      .poll(async () => (await eligibilityOf(request, token, product.sku)).eligible, { timeout: 15_000 })
      .toBe(true)
    await page.goto(`/products/${encodeURIComponent(product.sku)}`)
    const form = page.locator('.review-form-card')
    await expect(form.getByRole('heading', { name: 'Write a review' })).toBeVisible()
    await form.locator('.star-input label').filter({ hasText: '4 stars' }).click()
    await expect(form.getByRole('radio', { name: '4 stars' })).toBeChecked()
    await form.getByLabel(/^Title/).fill('Does what it says')
    await form.getByLabel('Review').fill('Bought it in Audio week. Sounds great, and the battery lasts.')
    await form.getByRole('button', { name: 'Post review' }).click()
    await expect(page.locator('.own-review')).toContainText('Does what it says')
    // Shown under the Customer's given name and family name's initial.
    await expect(page.getByRole('list', { name: 'Reviews', exact: true })).toContainText('E2E C.')

    // Its Rating summary, with this review in it, is on its page and on its card.
    const ratingSummary = await ratingSummaryOf(request, product.sku)
    expect(ratingSummary.count).toBeGreaterThan(0)
    const average = ratingSummary.average!.toFixed(1)
    const reviews = ratingSummary.count === 1 ? '1 review' : `${ratingSummary.count} reviews`
    const stars = `Rated ${average} out of 5`
    await expect(page.locator('.summary-link').getByRole('img', { name: stars })).toBeVisible()
    await expect(page.locator('.rating-summary')).toContainText(reviews)
    await page.goto(`/?${new URLSearchParams({ category: 'audio' })}`)
    await expect(card(page, product.name).getByRole('img', { name: stars })).toBeVisible()
    await expect(card(page, product.name)).toContainText(`${average} · ${reviews}`)
  })

  test('a Customer who never bought a Product is told they can’t review it', async ({ page, request }) => {
    const customer = signedUp()
    const product = (await products(request))[0]

    await page.goto(`/products/${encodeURIComponent(product.sku)}`)
    await expect(page.getByRole('heading', { name: product.name })).toBeVisible()
    // Signed out, they are asked to log in first, and come back to the same Product.
    await page.getByRole('button', { name: 'Log in to review' }).click()
    await signInOnKeycloak(page, customer)
    await expect(page).toHaveURL(new RegExp(`/products/${escape(encodeURIComponent(product.sku))}`))

    await expect(
      page.getByRole('status').filter({ hasText: 'Only Customers who have bought this product can review it.' }),
    ).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Write a review' })).toHaveCount(0)
    // Reviews refuses a post anyway, with the same reason.
    const posted = await request.post(`/api/reviews-ratings/products/${encodeURIComponent(product.sku)}/reviews`, {
      headers: { Authorization: `Bearer ${await tokenFor(request, customer)}` },
      data: { rating: 5, body: 'Never bought it.' },
    })
    expect(posted.status()).toBe(403)
    expect(((await posted.json()) as { reason: string }).reason).toBe('notPurchased')
  })
})

test.describe('Sprint 4', () => {
  test('a card the bank confirms is confirmed, then the Order is paid', async ({ page, request }) => {
    const customer = signedUp()
    const token = await tokenFor(request, customer)
    const { variantId } = await aProductInStock(request)
    await putInCart(request, token, variantId)

    await page.goto('/checkout')
    await signInOnKeycloak(page, customer)
    await expect(page.getByRole('timer', { name: 'Time left' })).toBeVisible()
    await testCard(page, 'Bank confirms, approves').check()
    await page.getByRole('button', { name: /^Pay/ }).click()

    await expect(page.getByRole('status').filter({ hasText: 'Confirming your payment…' })).toBeVisible()
    await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
    await expect(page.locator('.status-paid')).toHaveText('Paid')
    const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
    // Pending at first, then approved by the gateway's webhook.
    const [payment] = await paymentsOf(request, token, orderId)
    expect(payment.status).toBe('AUTHORIZED')
    expect(payment.transactions.map((t) => [t.kind, t.outcome])).toEqual([
      ['AUTHORIZATION', 'PENDING'],
      ['AUTHORIZATION', 'APPROVED'],
    ])
    expect(payment.transactions[1].gatewayEventId).toBeTruthy()
  })

  test('a card the bank declines says why, and the held session is then paid with one that approves', async ({
    page,
    request,
  }) => {
    const customer = signedUp()
    const token = await tokenFor(request, customer)
    const { variantId } = await aProductInStock(request)
    await putInCart(request, token, variantId)

    await page.goto('/checkout')
    await signInOnKeycloak(page, customer)
    await expect(page.getByRole('timer', { name: 'Time left' })).toBeVisible()
    const sessionId = await currentSessionId(request, token)
    await testCard(page, 'Bank confirms, declines').check()
    await page.getByRole('button', { name: /^Pay/ }).click()

    await expect(page.getByRole('status').filter({ hasText: 'Confirming your payment…' })).toBeVisible()
    const declined = page.getByRole('alert').filter({ hasText: 'Your card was declined' })
    await expect(declined).toContainText('Your bank declined the payment. Try another card.')
    expect(await currentSessionId(request, token)).toBe(sessionId)

    await testCard(page, 'Approve').check()
    await page.getByRole('button', { name: /^Pay/ }).click()

    await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
    await expect(page.locator('.status-paid')).toHaveText('Paid')
  })
})

async function putInCart(request: APIRequestContext, token: string, variantId: string) {
  const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { quantity: 1 },
  })
  expect(put.ok()).toBeTruthy()
}

/** A Payment as the Customer reads it, with its Payment transactions oldest first. */
interface PaymentRead {
  status: string
  transactions: { kind: string; outcome: string; gatewayEventId: string | null }[]
}

async function paymentsOf(request: APIRequestContext, token: string, orderId: string): Promise<PaymentRead[]> {
  const response = await request.get(`/api/payment/payments?orderId=${encodeURIComponent(orderId)}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as PaymentRead[]
}

/** The checkout page's test card radio, named by its card. */
function testCard(page: Page, label: string) {
  return page.getByRole('group', { name: 'Test card' }).getByRole('radio', { name: label, exact: true })
}

/** The ID of the Customer's live Checkout Session. */
async function currentSessionId(request: APIRequestContext, token: string): Promise<string> {
  const response = await request.get('/api/checkout-pricing/checkout/sessions/current', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
  return ((await response.json()) as { id: string }).id
}

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

async function orderOf(request: APIRequestContext, token: string, orderId: string) {
  const response = await request.get(`/api/order-management/orders/${encodeURIComponent(orderId)}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Order
}

async function stockOf(request: APIRequestContext, variantId: string): Promise<Stock> {
  const response = await request.get(`/api/inventory/stock/${encodeURIComponent(variantId)}`)
  expect(response.ok()).toBeTruthy()
  const { quantity, onHand } = (await response.json()) as Stock
  return { quantity, onHand }
}

/** The Currencies Catalog prices in, whose Minor units the Storefront shows Money by. */
async function currenciesOfCatalog(request: APIRequestContext): Promise<Currencies> {
  const response = await request.get('/api/catalog/currencies')
  expect(response.ok()).toBeTruthy()
  return currenciesOf((await response.json()) as Currency[])
}

async function products(request: APIRequestContext): Promise<Product[]> {
  const response = await request.get('/api/catalog/products')
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Product[]
}

/**
 * A seeded Variant other than its Product's first, with Stock left, that differs from the first in
 * one axis value, so picking that value on the Product page reaches it.
 */
async function aNonDefaultVariantInStock(request: APIRequestContext): Promise<ProductInStock> {
  for (const product of await products(request)) {
    const first = defaultVariant(product)
    for (const variant of product.variants.slice(1)) {
      const differing = Object.keys(variant.axisValues).filter(
        (axis) => variant.axisValues[axis] !== first.axisValues[axis],
      )
      if (differing.length !== 1 || variant.price.amountMinor === first.price.amountMinor) continue
      const stock = await stockOf(request, variant.id)
      const [axis] = differing
      if (stock.quantity > 0)
        return { product, variant, variantId: variant.id, stock, pick: { axis, value: variant.axisValues[axis] } }
    }
  }
  throw new Error('No seeded non-default Variant has Stock left; run `make seed-reset`')
}

/** The first seeded Product (other than `exceptSku`) whose first Variant has Stock left; earlier runs may have sold some out. */
async function aProductInStock(request: APIRequestContext, exceptSku?: string): Promise<ProductInStock> {
  for (const product of await products(request)) {
    if (product.sku === exceptSku) continue
    const variant = defaultVariant(product)
    const stock = await stockOf(request, variant.id)
    if (stock.quantity > 0) return { product, variant, variantId: variant.id, stock }
  }
  throw new Error('No seeded Product has Stock left; run `make seed-reset`')
}

/** A Product in `category` with its default Variant in Stock. */
async function aProductInStockIn(request: APIRequestContext, category: string): Promise<ProductInStock> {
  for (const product of await products(request)) {
    if (product.category !== category) continue
    const variant = defaultVariant(product)
    const stock = await stockOf(request, variant.id)
    if (stock.quantity > 0) return { product, variant, variantId: variant.id, stock }
  }
  throw new Error(`No seeded ${category} Product has Stock left; run \`make seed-reset\``)
}

/** How a Cart or Order line names a Variant: its Product, then its axis values. */
function lineName({ product, variant }: ProductInStock): string {
  return [product.name, ...Object.values(variant.axisValues)].join(' · ')
}

/** The listing's counts: how many Products match, how many are in stock, and how many have each type. */
async function expectCounts(page: Page, results: SearchResults, type: string) {
  await expect(page.locator('.listing-toolbar > p')).toHaveText(
    results.total === 1 ? '1 product' : `${results.total} products`,
  )
  await expect(page.locator('.stock-toggle .filter-count')).toHaveText(String(results.facets.inStock))
  const count = results.facets.attributes.find((a) => a.name === 'type')!.values.find((v) => v.value === type)!.count
  await expect(page.getByRole('group', { name: 'type' }).locator('label').filter({ hasText: type })).toContainText(
    String(count),
  )
}

function cards(page: Page) {
  return page.getByRole('list', { name: 'Products' }).getByRole('listitem')
}

/** The card of the Product named exactly `name`. */
function card(page: Page, name: string) {
  return cards(page).filter({ has: page.locator('.product-name').getByText(name, { exact: true }) })
}

function money(amount: Money, currencies: Currencies) {
  return formatMoney(amount, currencies, 'en-US')
}

function negative(amount: Money): Money {
  return { ...amount, amountMinor: -amount.amountMinor }
}

function escape(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** A token for Keycloak's own admin API, from the master realm's bootstrap admin. */
async function keycloakAdminHeaders(request: APIRequestContext) {
  const response = await request.post(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'admin-cli', username: 'admin', password: 'admin' },
  })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${((await response.json()) as { access_token: string }).access_token}` }
}

/** A new Customer, E2E Customer, ready to sign in. */
async function createCustomer(request: APIRequestContext): Promise<Customer> {
  const email = `e2e-${Date.now().toString(36)}${test.info().workerIndex}@ecomm.local`
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

/** Deletes the Customer's reviews of the seeded Products, then the Customer. */
async function deleteCustomer(request: APIRequestContext, user: Customer) {
  const token = await tokenFor(request, user)
  const headers = { Authorization: `Bearer ${token}` }
  for (const { sku } of await products(request)) {
    const { review } = await eligibilityOf(request, token, sku)
    if (!review) continue
    const deleted = await request.delete(`/api/reviews-ratings/reviews/${review.id}`, { headers })
    expect(deleted.status()).toBe(204)
  }
  const response = await request.delete(`${keycloakUrl}/admin/realms/ecomm/users/${user.id}`, {
    headers: await keycloakAdminHeaders(request),
  })
  expect(response.status()).toBe(204)
}

async function eligibilityOf(request: APIRequestContext, token: string, sku: string): Promise<Eligibility> {
  const response = await request.get(`/api/reviews-ratings/products/${encodeURIComponent(sku)}/eligibility`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as Eligibility
}

async function ratingSummaryOf(request: APIRequestContext, sku: string): Promise<RatingSummary> {
  const response = await request.get(`/api/reviews-ratings/products/${encodeURIComponent(sku)}/rating-summary`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as RatingSummary
}

async function searchOf(request: APIRequestContext, query: Record<string, string>): Promise<SearchResults> {
  const response = await request.get(`/api/search-discovery/search?${new URLSearchParams(query)}`)
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as SearchResults
}

/** The ID of the `index`th made-up Order, newest first; its first eight characters, its reference, tell it apart. */
function orderIdOf(index: number): string {
  return `${index.toString(16).padStart(8, '0')}-0000-4000-8000-000000000000`
}

/** 20 made-up Orders of a seeded Variant, a page of `ordersPageSize` at a time. */
function aPageOfOrders(page: number): ListPage<Order> {
  const price = { amountMinor: 79900, currency: 'EUR' }
  const all = Array.from({ length: 20 }, (_, i): Order => {
    const placedAt = new Date(Date.UTC(2026, 9, 1) - i * 3_600_000).toISOString()
    return {
      id: orderIdOf(i),
      status: 'PAID',
      lines: [{ variantId: 'PHN-PIXEL-9', quantity: 1, unitPrice: price }],
      subtotal: price,
      discounts: [],
      tax: { ...price, amountMinor: 0 },
      total: price,
      placedAt,
      statusHistory: [
        { status: 'PLACED', at: placedAt, changedBy: 'CHECKOUT', backfilled: false },
        { status: 'PAID', at: placedAt, changedBy: 'CHECKOUT', backfilled: false },
      ],
    }
  })
  return {
    items: all.slice(page * ordersPageSize, (page + 1) * ordersPageSize),
    page,
    size: ordersPageSize,
    total: all.length,
  }
}
