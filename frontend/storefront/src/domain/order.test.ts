import { describe, expect, it } from 'vitest'
import type { VariantDetail } from './catalog'
import {
  type Discount,
  type Order,
  type StatusHistoryEntry,
  appliedCoupon,
  discountLabel,
  discountNames,
  nameOrderLines,
  orderItemCountLabel,
  orderReference,
  orderStatusLabel,
  orderSummaryRows,
  orderTimeline,
} from './order'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

const audioWeek = (amount = eur(225)): Discount => ({
  source: 'CAMPAIGN',
  campaignId: '0c5e0a1d-0000-4000-8000-000000000001',
  campaignName: 'Audio week',
  couponCode: null,
  amount,
})

const welcome10 = (amount = eur(202)): Discount => ({
  source: 'COUPON',
  couponCode: 'WELCOME10',
  campaignId: null,
  campaignName: null,
  amount,
})

function variant(id: string): VariantDetail {
  return {
    id,
    axisValues: { color: 'Black' },
    price: eur(1000),
    images: [],
    product: { sku: id, name: `Product ${id}`, images: [] },
  }
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
    discounts: [],
    tax: eur(0),
    total: eur(2250),
    placedAt: '2026-09-29T10:00:00Z',
    statusHistory: [
      { status: 'PLACED', at: '2026-09-29T10:00:00Z', changedBy: 'CHECKOUT', backfilled: false },
      { status: 'PAID', at: '2026-09-29T10:00:05Z', changedBy: 'CHECKOUT', backfilled: false },
    ],
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
    expect(orderItemCountLabel(order({ lines: [{ variantId: 'A', quantity: 1, unitPrice: eur(1000) }] }))).toBe(
      '1 item',
    )
  })
})

describe('nameOrderLines', () => {
  it('names each line by its Product and axis values, and totals it at the captured price', () => {
    const a = variant('A')
    const lines = nameOrderLines(order(), { A: a })

    expect(lines[0]).toEqual({
      variantId: 'A',
      quantity: 2,
      unitPrice: eur(1000),
      lineTotal: eur(2000),
      variant: a,
      name: 'Product A · Black',
    })
  })

  it('leaves a line bare of Variant and name when Catalog has none', () => {
    const lines = nameOrderLines(order(), { A: variant('A') })

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

  it('takes each Discount off after the subtotal, in order, by Campaign name or Coupon code', () => {
    const discounted = order({
      discounts: [audioWeek(eur(225)), welcome10(eur(202))],
      tax: eur(365),
      total: eur(2188),
    })

    expect(rows(discounted)).toEqual([
      'Subtotal:2250',
      'Campaign: Audio week:-225',
      'Coupon: WELCOME10:-202',
      'Tax:365',
      'Total:2188',
    ])
  })

  it('marks only the total as the total', () => {
    expect(orderSummaryRows(order()).map((row) => row.isTotal ?? false)).toEqual([false, false, true])
  })
})

describe('discountLabel', () => {
  it("names a Campaign's Discount by the Campaign and a Coupon's by its code", () => {
    expect(discountLabel(audioWeek())).toBe('Campaign: Audio week')
    expect(discountLabel(welcome10())).toBe('Coupon: WELCOME10')
  })
})

describe('discountNames', () => {
  it('names every Discount, in order, for an Order card', () => {
    expect(discountNames([audioWeek(), welcome10()])).toEqual(['Audio week', 'WELCOME10'])
    expect(discountNames([])).toEqual([])
  })
})

describe('appliedCoupon', () => {
  it("is the Coupon's Discount among them, if any", () => {
    expect(appliedCoupon([audioWeek(), welcome10()])).toEqual(welcome10())
    expect(appliedCoupon([audioWeek()])).toBeUndefined()
  })
})

describe('orderTimeline', () => {
  const entry = (status: StatusHistoryEntry['status'], at: string, backfilled = false): StatusHistoryEntry => ({
    status,
    at,
    changedBy: 'CHECKOUT',
    backfilled,
  })
  const placed = entry('PLACED', '2026-09-29T10:00:00Z')
  const paid = entry('PAID', '2026-09-29T10:00:05Z')
  const steps = (...statusHistory: StatusHistoryEntry[]) =>
    orderTimeline(order({ status: statusHistory[statusHistory.length - 1].status, statusHistory })).map(
      (step) => `${step.status}:${step.state}${step.at ? `@${step.at}` : ''}`,
    )

  it('shows each Status the Order has been in, with when, then the steps still to come', () => {
    expect(steps(placed, paid)).toEqual([
      'PLACED:done@2026-09-29T10:00:00Z',
      'PAID:current@2026-09-29T10:00:05Z',
      'FULFILLED:upcoming',
      'SHIPPED:upcoming',
      'DELIVERED:upcoming',
    ])
  })

  it('starts at Placed', () => {
    expect(steps(placed)[0]).toBe('PLACED:current@2026-09-29T10:00:00Z')
  })

  it('ends at Delivered', () => {
    const delivered = [placed, paid, entry('FULFILLED', 'f'), entry('SHIPPED', 's'), entry('DELIVERED', 'd')]
    expect(steps(...delivered)).toEqual([
      'PLACED:done@2026-09-29T10:00:00Z',
      'PAID:done@2026-09-29T10:00:05Z',
      'FULFILLED:done@f',
      'SHIPPED:done@s',
      'DELIVERED:current@d',
    ])
  })

  it('shows that a cancelled Order was Paid first', () => {
    expect(steps(placed, paid, entry('CANCELLED', 'c'))).toEqual([
      'PLACED:done@2026-09-29T10:00:00Z',
      'PAID:done@2026-09-29T10:00:05Z',
      'CANCELLED:current@c',
    ])
  })

  it('ends an Order cancelled before payment at Cancelled, after Placed', () => {
    expect(steps(placed, entry('CANCELLED', 'c'))).toEqual(['PLACED:done@2026-09-29T10:00:00Z', 'CANCELLED:current@c'])
  })

  it('ends a returned Order at Returned, after Delivered', () => {
    const returned = [
      placed,
      paid,
      entry('FULFILLED', 'f'),
      entry('SHIPPED', 's'),
      entry('DELIVERED', 'd'),
      entry('RETURNED', 'r'),
    ]
    expect(steps(...returned).map((step) => step.split('@')[0])).toEqual([
      'PLACED:done',
      'PAID:done',
      'FULFILLED:done',
      'SHIPPED:done',
      'DELIVERED:done',
      'RETURNED:current',
    ])
  })

  it('gives no time for a backfilled Status, whose real time was never recorded', () => {
    expect(steps(placed, entry('CANCELLED', '2026-09-29T10:00:00Z', true))).toEqual([
      'PLACED:done@2026-09-29T10:00:00Z',
      'CANCELLED:current',
    ])
  })
})

describe('orderStatusLabel', () => {
  it('reads as a word', () => {
    expect(orderStatusLabel('CANCELLED')).toBe('Cancelled')
  })
})
