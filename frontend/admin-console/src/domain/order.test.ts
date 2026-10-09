import { describe, expect, it } from 'vitest'
import {
  type Order,
  discountLabel,
  filtersOf,
  historyRowsOf,
  orderReference,
  ordersQueryOf,
  pageCount,
  searchOf,
} from './order'

const order: Order = {
  id: '3f2a9c1b-5d4e-4f00-8a00-0123456789ab',
  customerId: 'customer-42',
  status: 'CANCELLED',
  lines: [{ variantId: 'PHN-PIXEL-9', quantity: 2, unitPrice: { amountMinor: 79900, currency: 'EUR' } }],
  subtotal: { amountMinor: 159800, currency: 'EUR' },
  discounts: [],
  tax: { amountMinor: 0, currency: 'EUR' },
  total: { amountMinor: 159800, currency: 'EUR' },
  placedAt: '2026-10-03T12:00:00Z',
  statusHistory: [
    { status: 'PLACED', at: '2026-10-03T12:00:00Z', changedBy: 'CHECKOUT', backfilled: false },
    { status: 'PAID', at: '2026-10-03T12:00:05Z', changedBy: 'CHECKOUT', backfilled: false },
    { status: 'CANCELLED', at: '2026-10-03T12:00:00Z', changedBy: 'CHECKOUT', backfilled: true },
  ],
}

describe('discountLabel', () => {
  it("names a Campaign's Discount by the Campaign and a Coupon's by its code", () => {
    const amount = { amountMinor: 100, currency: 'EUR' }
    expect(
      discountLabel({ source: 'CAMPAIGN', campaignId: 'c-1', campaignName: 'Audio week', couponCode: null, amount }),
    ).toBe('Campaign: Audio week')
    expect(
      discountLabel({ source: 'COUPON', couponCode: 'WELCOME10', campaignId: null, campaignName: null, amount }),
    ).toBe('Coupon: WELCOME10')
  })
})

describe('orderReference', () => {
  it('is the first eight hex digits of the ID, in capitals, as the Storefront shows it', () => {
    expect(orderReference(order)).toBe('3F2A9C1B')
  })
})

describe('filtersOf', () => {
  it('reads every filter and the page, which counts from 1, from the URL', () => {
    const params = new URLSearchParams(
      'status=PAID&customer=customer-42&from=2026-10-01&to=2026-10-31&reference=%233F2A&page=3',
    )
    expect(filtersOf(params)).toEqual({
      status: 'PAID',
      customer: 'customer-42',
      from: '2026-10-01',
      to: '2026-10-31',
      reference: '#3F2A',
      page: 2,
    })
  })

  it('leaves out what the URL lacks, and ignores a Status, a day or a page that is not one', () => {
    expect(filtersOf(new URLSearchParams('status=LOST&from=junk&to=2026-02-30&page=0'))).toEqual({
      status: '',
      customer: '',
      from: '',
      to: '',
      reference: '',
      page: 0,
    })
  })
})

describe('searchOf', () => {
  it('writes the filters to the URL that filtersOf reads them back from, trimmed and without blanks', () => {
    const filters = {
      status: 'PAID',
      customer: ' customer-42 ',
      from: '2026-10-01',
      to: '',
      reference: ' #3F2A ',
      page: 2,
    } as const

    expect(searchOf(filters)).toBe('?status=PAID&customer=customer-42&from=2026-10-01&reference=%233F2A&page=3')
    expect(filtersOf(new URLSearchParams(searchOf(filters)))).toEqual({
      ...filters,
      customer: 'customer-42',
      reference: '#3F2A',
    })
  })

  it('leaves the first page and no filters out altogether', () => {
    expect(searchOf({ status: '', customer: '', from: '', to: '', reference: '', page: 0 })).toBe('')
  })
})

describe('ordersQueryOf', () => {
  const none = { status: '', customer: '', from: '', to: '', reference: '', page: 0 } as const

  it('asks for the first page of every Order when nothing is filtered', () => {
    expect(ordersQueryOf(none, 20)).toEqual({ query: '?page=0&size=20' })
  })

  it('sends each filter as Order Management names it', () => {
    expect(ordersQueryOf({ ...none, status: 'PAID', customer: ' customer-42 ', page: 1 }, 20)).toEqual({
      query: '?status=PAID&customerId=customer-42&page=1&size=20',
    })
  })

  it('sends the days as instants from the first day’s local midnight to the midnight after the last', () => {
    // Asia/Tokyo is nine hours ahead of UTC (see vite.config.ts).
    expect(ordersQueryOf({ ...none, from: '2026-10-01', to: '2026-10-31' }, 20)).toEqual({
      query: '?placedFrom=2026-09-30T15%3A00%3A00.000Z&placedTo=2026-10-31T15%3A00%3A00.000Z&page=0&size=20',
    })
  })

  it('finds an Order by its reference, with or without the #, or by its full ID', () => {
    expect(ordersQueryOf({ ...none, reference: ' #3F2A9C1B ' }, 20)).toEqual({
      query: '?idPrefix=3F2A9C1B&page=0&size=20',
    })
    expect(ordersQueryOf({ ...none, reference: order.id }, 20)).toEqual({
      query: `?idPrefix=${order.id}&page=0&size=20`,
    })
  })

  it('refuses a reference that cannot be the start of an Order ID, before anything is sent', () => {
    expect(ordersQueryOf({ ...none, reference: '#3F2A-XYZ' }, 20)).toEqual({
      error: 'An Order reference is hex digits, such as #3F2A9C1B.',
    })
    expect(ordersQueryOf({ ...none, reference: '#' }, 20)).toEqual({
      error: 'An Order reference is hex digits, such as #3F2A9C1B.',
    })
  })
})

describe('pageCount', () => {
  it('is at least one, even for an empty list', () => {
    expect(pageCount({ size: 20, total: 0 })).toBe(1)
    expect(pageCount({ size: 20, total: 20 })).toBe(1)
    expect(pageCount({ size: 20, total: 21 })).toBe(2)
  })
})

describe('historyRowsOf', () => {
  it('lists each Status change, newest first, with its time and the caller that made it', () => {
    const rows = historyRowsOf(order, 'en-GB')

    expect(rows.map((row) => [row.status, row.changedBy, row.current])).toEqual([
      ['Cancelled', 'Checkout', true],
      ['Paid', 'Checkout', false],
      ['Placed', 'Checkout', false],
    ])
    expect(rows[1]).toMatchObject({ at: '2026-10-03T12:00:05Z', time: '3 Oct 2026, 21:00:05' })
  })

  it('names the checkout Saga for the changes it made', () => {
    const rows = historyRowsOf({
      statusHistory: [{ status: 'PLACED', at: '2026-10-09T12:00:00Z', changedBy: 'ORCHESTRATION', backfilled: false }],
    })

    expect(rows[0].changedBy).toBe('Checkout Saga')
  })

  it('says a backfilled change’s time is not known, since it is only the placement time', () => {
    const [cancelled] = historyRowsOf(order, 'en-GB')

    expect(cancelled.time).toBe('Not recorded')
    expect(cancelled.note).toBe('Reconstructed for an Order placed before histories were kept')
  })
})
