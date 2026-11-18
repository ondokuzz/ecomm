import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { CheckoutSession } from '../domain/checkout'
import type { Order } from '../domain/order'
import type { ShippingAddress } from '../domain/shipping'
import { type Endpoints, renderSignedIn, stubServices } from '../test/app'
import { CheckoutPage } from './CheckoutPage'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })
const minutes = (n: number) => n * 60_000

const berlin: ShippingAddress = {
  recipientName: 'Erika Mustermann',
  line1: 'Hauptstraße 5',
  postcode: '10115',
  city: 'Berlin',
  country: 'DE',
}
const standard = { id: 'STANDARD', carrier: 'Mock Carrier', cost: eur(499), estimatedDays: 3 }
const welcome10 = {
  source: 'COUPON' as const,
  couponCode: 'WELCOME10',
  campaignId: null,
  campaignName: null,
  amount: eur(7990),
}

/** A Checkout Session holding the Cart, expiring `expiresIn` milliseconds from now. */
function session(overrides: Partial<CheckoutSession> = {}, expiresIn = minutes(14)): CheckoutSession {
  return {
    id: 'session-1',
    lines: [{ variantId: 'PHN-PIXEL-9', quantity: 1, unitPrice: eur(79900), lineTotal: eur(79900) }],
    subtotal: eur(79900),
    discounts: [],
    tax: eur(0),
    shippingAddress: null,
    shippingOptions: [],
    shippingOption: null,
    shippingCost: eur(0),
    total: eur(79900),
    expiresAt: new Date(Date.now() + expiresIn).toISOString(),
    ...overrides,
  }
}

/** The same session once Checkout has validated `address` and quoted Standard for it. */
const addressed = (from: CheckoutSession, address = berlin): CheckoutSession => ({
  ...from,
  shippingAddress: address,
  shippingOptions: [standard],
})

function order(shippingAddress?: ShippingAddress): Order {
  return {
    id: '3f2a9c1b-7d4e-4a1f-9b2c-0e5d6f7a8b9c',
    status: 'PAID',
    lines: [{ variantId: 'PHN-PIXEL-9', quantity: 1, unitPrice: eur(79900) }],
    subtotal: eur(79900),
    discounts: [],
    tax: eur(0),
    total: eur(79900),
    placedAt: '2026-11-01T10:00:00Z',
    statusHistory: [],
    ...(shippingAddress ? { shippingAddress } : {}),
  }
}

const orders = (...items: Order[]) => ({ body: { items, page: 0, size: 12, total: items.length } })

/** The services as a checkout of one Pixel 9 finds them, with `current` the Customer's session. */
function checkout(current: CheckoutSession, endpoints: Endpoints = {}): Endpoints {
  return {
    'GET /api/cart/cart': { body: { items: [{ variantId: 'PHN-PIXEL-9', quantity: 1 }] } },
    'GET /api/catalog/currencies': { body: [{ code: 'EUR', minorDigits: 2 }] },
    'GET /api/checkout-pricing/checkout/sessions/current': { body: current },
    'GET /api/order-management/orders?page=0&size=12': orders(),
    ...endpoints,
  }
}

const sessionPath = '/api/checkout-pricing/checkout/sessions/session-1'

/** The address the session ships to, as the Shipping card shows it once Checkout has it. */
const shippingTo = () => screen.findByLabelText('Shipping address', { selector: 'address' })

describe('the Shipping card', () => {
  it("offers the latest Order's address to a session without one, and uses it as one typed in", async () => {
    const sent = stubServices(
      checkout(session(), {
        'GET /api/order-management/orders?page=0&size=12': orders(order(berlin)),
        [`PUT ${sessionPath}/shipping-address`]: { body: addressed(session()) },
      }),
    )
    renderSignedIn(<CheckoutPage />, '/checkout')

    const offer = await screen.findByRole('group', { name: 'Your last address' })
    expect(offer.textContent).toContain('10115 Berlin')
    await userEvent.click(within(offer).getByRole('button', { name: 'Use this address' }))

    expect((await shippingTo()).textContent).toContain('10115 Berlin')
    expect(sent.find((request) => request.method === 'PUT')).toEqual({
      method: 'PUT',
      path: `${sessionPath}/shipping-address`,
      body: berlin,
    })
  })

  it('opens the form filled with the offered address to edit it, or empty to enter another, and goes back to the offer', async () => {
    stubServices(checkout(session(), { 'GET /api/order-management/orders?page=0&size=12': orders(order(berlin)) }))
    renderSignedIn(<CheckoutPage />, '/checkout')
    const offer = await screen.findByRole('group', { name: 'Your last address' })

    await userEvent.click(within(offer).getByRole('button', { name: 'Edit' }))
    const form = screen.getByRole('form', { name: 'Shipping address' })
    expect(within(form).getByLabelText('Postcode')).toHaveProperty('value', '10115')
    await userEvent.click(within(form).getByRole('button', { name: 'Cancel' }))

    await userEvent.click(
      within(screen.getByRole('group', { name: 'Your last address' })).getByRole('button', {
        name: 'Enter another address',
      }),
    )
    expect(within(screen.getByRole('form', { name: 'Shipping address' })).getByLabelText('Postcode')).toHaveProperty(
      'value',
      '',
    )
  })

  it('skips an Order placed before Shipping, which has no address', async () => {
    const paris = { ...berlin, line1: '1 rue de Rivoli', postcode: '75001', city: 'Paris', country: 'FR' }
    stubServices(
      checkout(session(), { 'GET /api/order-management/orders?page=0&size=12': orders(order(), order(paris)) }),
    )
    renderSignedIn(<CheckoutPage />, '/checkout')

    expect((await screen.findByRole('group', { name: 'Your last address' })).textContent).toContain('75001 Paris')
  })

  it('asks a Customer with no Order for an address, with the form empty', async () => {
    stubServices(checkout(session()))
    renderSignedIn(<CheckoutPage />, '/checkout')

    const form = await screen.findByRole('form', { name: 'Shipping address' })
    expect(within(form).getByLabelText('Postcode')).toHaveProperty('value', '')
    expect(screen.queryByRole('group', { name: 'Your last address' })).toBeNull()
  })

  it("asks for an address when the Customer's Orders can't be read", async () => {
    stubServices(checkout(session(), { 'GET /api/order-management/orders?page=0&size=12': { status: 503, body: {} } }))
    renderSignedIn(<CheckoutPage />, '/checkout')

    expect(await screen.findByRole('form', { name: 'Shipping address' })).toBeTruthy()
  })

  it("shows a session's own address rather than offering one", async () => {
    stubServices(
      checkout(addressed(session()), { 'GET /api/order-management/orders?page=0&size=12': orders(order(berlin)) }),
    )
    renderSignedIn(<CheckoutPage />, '/checkout')

    expect((await shippingTo()).textContent).toContain('10115 Berlin')
    expect(screen.queryByRole('group', { name: 'Your last address' })).toBeNull()
  })
})

describe('the time left', () => {
  it('is not shown while five minutes or more are left, only when the session is held until', async () => {
    stubServices(checkout(session({}, minutes(6))))
    renderSignedIn(<CheckoutPage />, '/checkout')

    expect(await screen.findByText(/held until/)).toBeTruthy()
    expect(screen.queryByRole('timer', { name: 'Time left' })).toBeNull()
  })

  it('is shown once under five minutes are left', async () => {
    stubServices(checkout(session({}, minutes(4) + 30_000)))
    renderSignedIn(<CheckoutPage />, '/checkout')

    expect((await screen.findByRole('timer', { name: 'Time left' })).textContent).toMatch(/^4:(29|30)$/)
  })
})

describe('Start again', () => {
  const expired = session({ shippingAddress: berlin, shippingOptions: [standard], discounts: [welcome10] }, -1_000)
  const fresh = session({ id: 'session-2' })
  const freshPath = '/api/checkout-pricing/checkout/sessions/session-2'

  it("starts a new session from the Cart, and puts the expired one's address back, then its Coupon", async () => {
    const sent = stubServices(
      checkout(expired, {
        'POST /api/checkout-pricing/checkout/sessions': { status: 201, body: fresh },
        [`PUT ${freshPath}/shipping-address`]: { body: addressed(fresh) },
        [`PUT ${freshPath}/coupon`]: { body: { ...addressed(fresh), discounts: [welcome10] } },
      }),
    )
    renderSignedIn(<CheckoutPage />, '/checkout')

    expect(await screen.findByRole('heading', { name: 'Your hold expired' })).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: 'Start again' }))

    expect((await shippingTo()).textContent).toContain('10115 Berlin')
    const summary = screen.getByRole('region', { name: 'Order summary' })
    expect(within(summary).getByText('Coupon: WELCOME10')).toBeTruthy()
    expect(
      sent
        .filter((request) => request.method !== 'GET')
        .map((request) => [request.method, request.path.replace('/api/checkout-pricing', ''), request.body]),
    ).toEqual([
      ['POST', '/checkout/sessions', undefined],
      ['PUT', '/checkout/sessions/session-2/shipping-address', berlin],
      ['PUT', '/checkout/sessions/session-2/coupon', { code: 'WELCOME10' }],
    ])
  })

  it('leaves off a Coupon that no longer applies, with its code in the field and why, as applying it would', async () => {
    stubServices(
      checkout(expired, {
        'POST /api/checkout-pricing/checkout/sessions': { status: 201, body: fresh },
        [`PUT ${freshPath}/shipping-address`]: { body: addressed(fresh) },
        [`PUT ${freshPath}/coupon`]: { status: 422, body: { status: 422, reason: 'expired' } },
      }),
    )
    renderSignedIn(<CheckoutPage />, '/checkout')
    await userEvent.click(await screen.findByRole('button', { name: 'Start again' }))

    expect((await shippingTo()).textContent).toContain('10115 Berlin')
    const summary = screen.getByRole('region', { name: 'Order summary' })
    expect(within(summary).getByRole('alert').textContent).toBe('That code has expired.')
    expect(within(summary).getByLabelText('Coupon code')).toHaveProperty('value', 'WELCOME10')
    expect(within(summary).queryByText('Coupon: WELCOME10')).toBeNull()

    // Changing the code clears why the old one didn't apply.
    await userEvent.type(within(summary).getByLabelText('Coupon code'), 'X')
    expect(within(summary).queryByRole('alert')).toBeNull()
  })

  it('leaves off an address Checkout refuses now, and asks for one, still putting the Coupon back', async () => {
    stubServices(
      checkout(expired, {
        'POST /api/checkout-pricing/checkout/sessions': { status: 201, body: fresh },
        [`PUT ${freshPath}/shipping-address`]: {
          status: 422,
          body: { status: 422, reason: 'undeliverableCountry', country: 'DE' },
        },
        [`PUT ${freshPath}/coupon`]: { body: { ...fresh, discounts: [welcome10] } },
      }),
    )
    renderSignedIn(<CheckoutPage />, '/checkout')
    await userEvent.click(await screen.findByRole('button', { name: 'Start again' }))

    expect(await screen.findByRole('form', { name: 'Shipping address' })).toBeTruthy()
    expect(within(screen.getByRole('region', { name: 'Order summary' })).getByText('Coupon: WELCOME10')).toBeTruthy()
  })

  it('says why a new session could not start, and puts nothing back', async () => {
    let starts = 0
    const sent = stubServices(
      checkout(expired, {
        'POST /api/checkout-pricing/checkout/sessions': () =>
          ++starts === 1
            ? { status: 409, body: { status: 409, outOfStock: ['PHN-PIXEL-9'] } }
            : { status: 201, body: fresh },
        [`PUT ${freshPath}/shipping-address`]: { body: addressed(fresh) },
        [`PUT ${freshPath}/coupon`]: { body: { ...addressed(fresh), discounts: [welcome10] } },
      }),
    )
    renderSignedIn(<CheckoutPage />, '/checkout')
    await userEvent.click(await screen.findByRole('button', { name: 'Start again' }))

    expect(await screen.findByText('Some items are out of stock')).toBeTruthy()
    expect(sent.filter((request) => request.method === 'PUT')).toEqual([])
  })
})
