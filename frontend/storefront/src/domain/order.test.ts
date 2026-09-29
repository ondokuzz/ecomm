import { describe, expect, it } from 'vitest'
import type { Product } from './catalog'
import {
  type Order,
  type OrderStatus,
  nameOrderLines,
  orderItemCountLabel,
  orderReference,
  orderStatusLabel,
  orderSummaryRows,
  orderTimeline,
} from './order'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

function product(sku: string): Product {
  return { sku, name: `Product ${sku}`, category: 'phones', attributes: {}, price: eur(1000), images: [], variants: [{ id: sku, price: eur(1000) }] }
}

function order(overrides: Partial<Order> = {}): Order {
  return {
    id: '3f2a9c1b-7d4e-4a1f-9b2c-0e5d6f7a8b9c',
    status: 'PAID',
    lines: [
      { variantId: 'A', quantity: 2, unitPrice: eur(1000) },
      { variantId: 'B', quantity: 1, unitPrice: eur(250) },
    ],
    subtotal: eur(2250),
    discount: null,
    tax: eur(0),
    total: eur(2250),
    placedAt: '2026-09-29T10:00:00Z',
    ...overrides,
  }
}

describe('orderReference', () => {
  it('is the start of the Order ID, upper-cased', () => {
    expect(orderReference(order())).toBe('3F2A9C1B')
  })
})

describe('orderItemCountLabel', () => {
  it('counts the units, not the lines', () => {
    expect(orderItemCountLabel(order())).toBe('3 items')
  })

  it('is singular for one unit', () => {
    expect(orderItemCountLabel(order({ lines: [{ variantId: 'A', quantity: 1, unitPrice: eur(1000) }] }))).toBe('1 item')
  })
})

describe('nameOrderLines', () => {
  it('names each line by its Product and totals it at the captured price', () => {
    const a = product('A')
    const lines = nameOrderLines(order(), { A: a })

    expect(lines[0]).toEqual({ variantId: 'A', quantity: 2, unitPrice: eur(1000), lineTotal: eur(2000), product: a, name: 'Product A' })
  })

  it('leaves a line bare of Product and name when Catalog has none', () => {
    const lines = nameOrderLines(order(), { A: product('A') })

    expect(lines[1]).toEqual({ variantId: 'B', quantity: 1, unitPrice: eur(250), lineTotal: eur(250) })
  })
})

describe('orderSummaryRows', () => {
  const rows = (o: Order) => orderSummaryRows(o).map((row) => `${row.label}:${row.amount.amountMinor}`)

  it('shows the subtotal, the tax and the total', () => {
    expect(rows(order({ tax: eur(450), total: eur(2700) }))).toEqual(['Subtotal:2250', 'Tax:450', 'Total:2700'])
  })

  it('shows a zero tax too', () => {
    expect(rows(order())).toEqual(['Subtotal:2250', 'Tax:0', 'Total:2250'])
  })

  it('takes the discount off after the subtotal, naming its coupon', () => {
    const discounted = order({ discount: { couponCode: 'WELCOME10', amount: eur(225) }, tax: eur(405), total: eur(2430) })

    expect(rows(discounted)).toEqual(['Subtotal:2250', 'Discount (WELCOME10):-225', 'Tax:405', 'Total:2430'])
  })

  it('marks only the total as the total', () => {
    expect(orderSummaryRows(order()).map((row) => row.isTotal ?? false)).toEqual([false, false, true])
  })
})

describe('orderTimeline', () => {
  const steps = (status: OrderStatus) => orderTimeline(status).map((step) => `${step.status}:${step.state}`)

  it('marks the steps before the current Order Status done, and the rest upcoming', () => {
    expect(steps('PAID')).toEqual(['PLACED:done', 'PAID:current', 'FULFILLED:upcoming', 'SHIPPED:upcoming', 'DELIVERED:upcoming'])
  })

  it('starts at Placed', () => {
    expect(steps('PLACED')[0]).toBe('PLACED:current')
  })

  it('ends at Delivered', () => {
    expect(steps('DELIVERED')).toEqual(['PLACED:done', 'PAID:done', 'FULFILLED:done', 'SHIPPED:done', 'DELIVERED:current'])
  })

  it('ends a cancelled Order at Cancelled, after Placed', () => {
    expect(steps('CANCELLED')).toEqual(['PLACED:done', 'CANCELLED:current'])
  })

  it('ends a returned Order at Returned, after Delivered', () => {
    expect(steps('RETURNED')).toEqual([
      'PLACED:done',
      'PAID:done',
      'FULFILLED:done',
      'SHIPPED:done',
      'DELIVERED:done',
      'RETURNED:current',
    ])
  })
})

describe('orderStatusLabel', () => {
  it('reads as a word', () => {
    expect(orderStatusLabel('CANCELLED')).toBe('Cancelled')
  })
})
