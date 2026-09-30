import { describe, expect, it } from 'vitest'
import type { CartLine } from './cart'
import { type CheckoutSession, checkoutProblem, sessionCountdown, sessionHoldsCart, sessionSummaryRows } from './checkout'

const lines: CartLine[] = [
  { variantId: 'PIXEL-9', quantity: 1, name: 'Google Pixel 9' },
  { variantId: 'GONE', quantity: 1 },
]

describe('checkoutProblem', () => {
  it('names the out-of-stock Products', () => {
    expect(checkoutProblem({ outOfStock: ['PIXEL-9'] }, lines)).toEqual({
      reason: 'outOfStock',
      products: [{ variantId: 'PIXEL-9', name: 'Google Pixel 9' }],
    })
  })

  it('names the unknown Variants by ID when Catalog has no name for them', () => {
    expect(checkoutProblem({ unknownVariants: ['GONE'] }, lines)).toEqual({
      reason: 'unknownVariants',
      products: [{ variantId: 'GONE', name: 'GONE' }],
    })
  })

  it('names a Variant no longer in the Cart by its ID', () => {
    expect(checkoutProblem({ outOfStock: ['OTHER'] }, lines)?.products).toEqual([{ variantId: 'OTHER', name: 'OTHER' }])
  })

  it('skips anything in the list that is not a Variant ID', () => {
    expect(checkoutProblem({ outOfStock: ['PIXEL-9', 7, null] }, lines)?.products).toEqual([
      { variantId: 'PIXEL-9', name: 'Google Pixel 9' },
    ])
  })

  it('is undefined for any other problem', () => {
    expect(checkoutProblem({ detail: 'Your cart is empty' }, lines)).toBeUndefined()
    expect(checkoutProblem({ outOfStock: 'PIXEL-9' }, lines)).toBeUndefined()
  })
})

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

const session: CheckoutSession = {
  id: 's-1',
  lines: [
    { variantId: 'PIXEL-9', quantity: 2, unitPrice: eur(79900), lineTotal: eur(159800) },
    { variantId: 'FLIP-6', quantity: 1, unitPrice: eur(12900), lineTotal: eur(12900) },
  ],
  subtotal: eur(172700),
  tax: eur(0),
  total: eur(172700),
  expiresAt: '2026-09-30T10:15:00Z',
}

describe('sessionCountdown', () => {
  const expiresAt = Date.parse(session.expiresAt)

  it('shows the minutes and seconds left', () => {
    expect(sessionCountdown(session.expiresAt, expiresAt - 14 * 60_000 - 5_000)).toEqual({ expired: false, label: '14:05' })
  })

  it('shows a whole 15 minutes at the start', () => {
    expect(sessionCountdown(session.expiresAt, expiresAt - 15 * 60_000)).toEqual({ expired: false, label: '15:00' })
  })

  it('rounds a part second up, so it never shows 0:00 while the session lasts', () => {
    expect(sessionCountdown(session.expiresAt, expiresAt - 300)).toEqual({ expired: false, label: '0:01' })
  })

  it('is expired from the moment the session ends', () => {
    expect(sessionCountdown(session.expiresAt, expiresAt)).toEqual({ expired: true, label: '0:00' })
    expect(sessionCountdown(session.expiresAt, expiresAt + 60_000)).toEqual({ expired: true, label: '0:00' })
  })
})

describe('sessionSummaryRows', () => {
  it('breaks the price down into subtotal, tax and total', () => {
    expect(sessionSummaryRows({ ...session, tax: eur(34540), total: eur(207240) })).toEqual([
      { label: 'Subtotal', amount: eur(172700) },
      { label: 'Tax', amount: eur(34540) },
      { label: 'Total', amount: eur(207240), isTotal: true },
    ])
  })

  it('shows the tax even when it is zero', () => {
    expect(sessionSummaryRows(session).map((row) => row.label)).toEqual(['Subtotal', 'Tax', 'Total'])
  })
})

describe('sessionHoldsCart', () => {
  it('holds a Cart with the same Variants and quantities, in any order', () => {
    const cart = { items: [{ variantId: 'FLIP-6', quantity: 1 }, { variantId: 'PIXEL-9', quantity: 2 }] }
    expect(sessionHoldsCart(session, cart)).toBe(true)
  })

  it('does not hold a Cart whose quantity changed', () => {
    const cart = { items: [{ variantId: 'PIXEL-9', quantity: 3 }, { variantId: 'FLIP-6', quantity: 1 }] }
    expect(sessionHoldsCart(session, cart)).toBe(false)
  })

  it('does not hold a Cart with a Variant added or removed', () => {
    expect(sessionHoldsCart(session, { items: [{ variantId: 'PIXEL-9', quantity: 2 }] })).toBe(false)
    const more = { items: [...session.lines, { variantId: 'IPHONE', quantity: 1 }] }
    expect(sessionHoldsCart(session, more)).toBe(false)
  })
})
