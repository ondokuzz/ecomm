import { type APIRequestContext, type Page, expect, test } from '@playwright/test'
import { type Product, type Variant, defaultVariant } from '../src/domain/catalog'
import { type Currencies, type Currency, currenciesOf, formatMoney } from '../src/domain/money'
import { type Order, ordersPageSize } from '../src/domain/order'
import type { Page as ListPage } from '../src/domain/paging'

/**
 * The Sprint 1 definition of done, end to end: browse seeded Products, pick a Variant, add them to
 * the Cart, check out with the mock payment, and see the Order confirmation and its Order Status.
 * Checkout holds the Cart in a Checkout Session first, and paying takes the held Stock off on-hand.
 * A declined test card says so, and the held session can then be paid with one that approves.
 */

// The demo checkout compares Stock before and after, so nothing else may check out meanwhile.
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

interface ProductInStock {
  product: Product
  variant: Variant
  variantId: string
  stock: Stock
  /** The axis value to pick on the Product page to reach `variant`; none for the default Variant. */
  pick?: { axis: string; value: string }
}

const keycloakUrl = process.env.KEYCLOAK_URL ?? 'http://localhost:8180'
const demoCustomer: Credentials = { email: 'demo@ecomm.local', password: 'demo' }

test('the demo Customer checks out a picked Variant and another Product, and on-hand Stock goes down', async ({ page, request }) => {
  const token = await tokenFor(request, demoCustomer)
  await emptyCart(request, token)
  const picked = await aNonDefaultVariantInStock(request)
  const bought = [picked, await aProductInStock(request, picked.product.sku)]
  const currencies = await currenciesOfCatalog(request)

  await page.goto('/')
  await page.getByRole('button', { name: 'Log in' }).click()
  await signInOnKeycloak(page, demoCustomer)
  await expectSignedIn(page, demoCustomer.email)

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
  await expect(page.getByRole('list', { name: 'Order progress' }).locator('[aria-current=step] .timeline-status')).toHaveText('Paid')
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
  // The list is paged, newest first: the new Order is on the first page, and older ones further on.
  const { total } = await ordersPageOf(request, token)
  const pager = page.getByRole('navigation', { name: 'Order pages' })
  if (total > ordersPageSize) {
    await expect(pager).toContainText(`Page 1 of ${Math.ceil(total / ordersPageSize)}`)
    await pager.getByRole('link', { name: 'Older orders' }).click()
    await expect(page).toHaveURL(/\/orders\?page=2$/)
    await expect(pager).toContainText('Page 2 of')
    await expect(card).toBeHidden()
    await page.goBack()
  } else {
    await expect(pager).toBeHidden()
  }

  // Paying committed the Reservation: the units are off on-hand for good.
  for (const { variantId, stock } of bought) {
    expect(await stockOf(request, variantId)).toEqual({ quantity: stock.quantity - 1, onHand: stock.onHand - 1 })
  }
})

test('a declined card shows why, and the held session can then be paid with one that approves', async ({ page, request }) => {
  const token = await tokenFor(request, demoCustomer)
  await emptyCart(request, token)
  const { variantId, stock } = await aProductInStock(request)
  const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { quantity: 1 },
  })
  expect(put.ok()).toBeTruthy()

  await page.goto('/checkout')
  await signInOnKeycloak(page, demoCustomer)
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
  const token = await tokenFor(request, demoCustomer)
  await emptyCart(request, token)
  const { variantId, variant } = await aProductInStock(request)
  const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { quantity: 1 },
  })
  expect(put.ok()).toBeTruthy()
  const subtotal = variant.price
  const currencies = await currenciesOfCatalog(request)
  // 10%, rounded down to the minor unit.
  const discount = { ...subtotal, amountMinor: Math.floor(subtotal.amountMinor / 10) }
  const discounted = { ...subtotal, amountMinor: subtotal.amountMinor - discount.amountMinor }

  await page.goto('/checkout')
  await signInOnKeycloak(page, demoCustomer)
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
  await expect(summary.getByText('Discount (WELCOME10)')).toBeVisible()
  await expect(summary).toContainText(formatMoney({ ...discount, amountMinor: -discount.amountMinor }, currencies, 'en-US'))
  await expect(page.getByRole('button', { name: `Pay ${formatMoney(discounted, currencies, 'en-US')}` })).toBeVisible()

  // Removing it puts the total back; applying it again takes it off again.
  await summary.getByRole('button', { name: 'Remove coupon WELCOME10' }).click()
  await expect(summary.getByText('Discount (WELCOME10)')).toBeHidden()
  await expect(page.getByRole('button', { name: `Pay ${formatMoney(subtotal, currencies, 'en-US')}` })).toBeVisible()
  await couponField.fill('WELCOME10')
  await summary.getByRole('button', { name: 'Apply' }).click()
  await expect(summary.getByText('Discount (WELCOME10)')).toBeVisible()

  await testCard(page, 'Approve').check()
  await page.getByRole('button', { name: `Pay ${formatMoney(discounted, currencies, 'en-US')}` }).click()

  await expect(page).toHaveURL(/\/orders\/[^/?]+\?placed$/)
  await expect(page.locator('.status-paid')).toHaveText('Paid')
  await expect(page.getByText('Discount (WELCOME10)')).toBeVisible()
  const orderId = decodeURIComponent(new URL(page.url()).pathname.split('/').pop()!)
  const order = await orderOf(request, token, orderId)
  expect(order.status).toBe('PAID')
  expect(order.discount).toEqual({ couponCode: 'WELCOME10', amount: discount })
  expect(order.total).toEqual({ ...discounted, amountMinor: discounted.amountMinor + order.tax.amountMinor })
})

test('the demo Customer empties their Cart after confirming', async ({ page, request }) => {
  const token = await tokenFor(request, demoCustomer)
  await emptyCart(request, token)
  const { variantId } = await aProductInStock(request)
  const put = await request.put(`/api/cart/cart/items/${encodeURIComponent(variantId)}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { quantity: 2 },
  })
  expect(put.ok()).toBeTruthy()

  await page.goto('/cart')
  await signInOnKeycloak(page, demoCustomer)
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

test('a new Customer registers on Keycloak and comes back signed in', async ({ page, baseURL }) => {
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
})

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

/** A leftover Cart would change what gets checked out, so start from an empty one. */
async function emptyCart(request: APIRequestContext, token: string) {
  const response = await request.delete('/api/cart/cart', { headers: { Authorization: `Bearer ${token}` } })
  expect(response.status()).toBe(204)
}

/** The first page of the Customer's Orders, with how many they have in all. */
async function ordersPageOf(request: APIRequestContext, token: string) {
  const response = await request.get(`/api/order-management/orders?size=${ordersPageSize}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
  return (await response.json()) as ListPage<Order>
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
      const differing = Object.keys(variant.axisValues).filter((axis) => variant.axisValues[axis] !== first.axisValues[axis])
      if (differing.length !== 1 || variant.price.amountMinor === first.price.amountMinor) continue
      const stock = await stockOf(request, variant.id)
      const [axis] = differing
      if (stock.quantity > 0) return { product, variant, variantId: variant.id, stock, pick: { axis, value: variant.axisValues[axis] } }
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

/** How a Cart or Order line names a Variant: its Product, then its axis values. */
function lineName({ product, variant }: ProductInStock): string {
  return [product.name, ...Object.values(variant.axisValues)].join(' · ')
}
