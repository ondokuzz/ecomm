import { describe, expect, it } from 'vitest'
import {
  type LineEligibility,
  type Rma,
  kindOptionsOf,
  rmaRefreshInterval,
  rmaRefusalsOf,
  rmaRequestOf,
  rmaTimelineOf,
} from './rma'

/** Dates as `YYYY-MM-DD`, so the tests don't depend on the machine's locale or time zone. */
const day = (at: string) => at.slice(0, 10)

const now = new Date('2026-10-20T12:00:00Z')

const phone: LineEligibility = {
  variantId: 'PHN-PIXEL-9',
  productName: 'Pixel 9',
  returnPeriodEndsAt: '2026-11-01T09:00:00Z',
  warrantyWindowEndsAt: '2028-10-18T09:00:00Z',
  serialNumbers: ['490154203237518'],
  quantity: 1,
}

describe('kindOptionsOf', () => {
  it('offers a return and a warranty claim, each until its window ends', () => {
    expect(kindOptionsOf(phone, now, day)).toEqual([
      { kind: 'RETURN', label: 'Return', endsAt: '2026-11-01T09:00:00Z', available: true },
      { kind: 'WARRANTY_CLAIM', label: 'Warranty claim', endsAt: '2028-10-18T09:00:00Z', available: true },
    ])
  })

  it('disables a return once its Return Period ended, saying when', () => {
    const [ret, warranty] = kindOptionsOf(phone, new Date('2026-11-02T00:00:00Z'), day)
    expect(ret).toMatchObject({ available: false, reason: 'Your Return Period ended on 2026-11-01.' })
    expect(warranty.available).toBe(true)
  })

  it('disables a warranty claim once its Warranty Window ended, or when the Product has no warranty', () => {
    const ended = kindOptionsOf(phone, new Date('2028-10-19T00:00:00Z'), day)[1]
    expect(ended).toMatchObject({ available: false, reason: 'Its warranty ended on 2028-10-18.' })

    const none = kindOptionsOf({ ...phone, warrantyWindowEndsAt: undefined }, now, day)[1]
    expect(none).toEqual({
      kind: 'WARRANTY_CLAIM',
      label: 'Warranty claim',
      available: false,
      reason: 'It has no manufacturer’s warranty.',
    })
  })

  it('disables both once every unit is in an RMA', () => {
    const options = kindOptionsOf({ ...phone, serialNumbers: [], quantity: 0 }, now, day)
    expect(options.map((option) => option.reason)).toEqual([
      'Every unit is already in a return or claim.',
      'Every unit is already in a return or claim.',
    ])
  })
})

describe('rmaRequestOf', () => {
  const chosen = { kind: 'RETURN' as const, serialNumbers: ['490154203237518'], quantity: '', reason: ' Too big ' }

  it('makes the body of POST /rmas: the chosen Serial numbers, as many units, and the trimmed reason', () => {
    expect(rmaRequestOf('order-1', phone, chosen, now)).toEqual({
      body: {
        orderId: 'order-1',
        variantId: 'PHN-PIXEL-9',
        kind: 'RETURN',
        quantity: 1,
        serialNumbers: ['490154203237518'],
        reason: 'Too big',
      },
    })
  })

  it('takes a quantity for a Variant without Serial tracking', () => {
    const charger = { ...phone, variantId: 'ACC-CHARGER', serialNumbers: [], quantity: 3 }
    const form = { ...chosen, serialNumbers: [], quantity: '2' }
    expect(rmaRequestOf('order-1', charger, form, now)).toMatchObject({ body: { quantity: 2, serialNumbers: [] } })
    expect(rmaRequestOf('order-1', charger, { ...form, quantity: '4' }, now)).toEqual({
      errors: { units: 'Choose 1 to 3 units.' },
    })
    expect(rmaRequestOf('order-1', charger, { ...form, quantity: '1.5' }, now)).toEqual({
      errors: { units: 'Choose 1 to 3 units.' },
    })
  })

  it('names each field the Customer still has to fill in', () => {
    expect(
      rmaRequestOf('order-1', phone, { kind: undefined, serialNumbers: [], quantity: '', reason: '  ' }, now),
    ).toEqual({
      errors: {
        kind: 'Choose a return or a warranty claim.',
        units: 'Choose the units you’re sending back.',
        reason: 'Tell us what’s wrong.',
      },
    })
    expect(rmaRequestOf('order-1', phone, { ...chosen, reason: 'x'.repeat(1001) }, now)).toEqual({
      errors: { reason: 'Keep it to 1,000 characters.' },
    })
  })

  it('refuses a kind that isn’t available, and a Serial number no longer free to claim', () => {
    const late = new Date('2026-11-02T00:00:00Z')
    expect(rmaRequestOf('order-1', phone, chosen, late, day)).toEqual({
      errors: { kind: 'Your Return Period ended on 2026-11-01.' },
    })
    expect(rmaRequestOf('order-1', phone, { ...chosen, serialNumbers: ['000'] }, now)).toEqual({
      errors: { units: 'Choose the units you’re sending back.' },
    })
  })
})

describe('rmaRefusalsOf', () => {
  const refused = (problem: Record<string, unknown>, status = 422) => rmaRefusalsOf(status, problem, day)

  it('puts a refused window or warranty beside the kind', () => {
    expect(refused({ reason: 'outsideReturnPeriod', endedAt: '2026-11-01T09:00:00Z' })).toEqual({
      kind: 'Your Return Period ended on 2026-11-01.',
    })
    expect(refused({ reason: 'outsideWarrantyWindow', endedAt: '2028-10-18T09:00:00Z' })).toEqual({
      kind: 'Its warranty ended on 2028-10-18.',
    })
    expect(refused({ reason: 'noWarranty' })).toEqual({ kind: 'It has no manufacturer’s warranty.' })
  })

  it('puts refused units beside the units, naming the Serial number', () => {
    expect(refused({ reason: 'quantityExceeded', available: 1 })).toEqual({ units: 'Only 1 unit is left to claim.' })
    expect(refused({ reason: 'quantityExceeded', available: 0 })).toEqual({
      units: 'Every unit is already in a return or claim.',
    })
    expect(refused({ reason: 'serialAlreadyClaimed', serialNumber: '490154203237518' })).toEqual({
      units: '490154203237518 is already in another return or claim.',
    })
    expect(refused({ reason: 'serialNotShipped', serialNumber: 'X1' })).toEqual({
      units: 'X1 wasn’t shipped to you on this order.',
    })
    expect(refused({ reason: 'serialNumbersMiscounted', expected: 2 })).toEqual({
      units: 'Choose the units you’re sending back.',
    })
  })

  it('puts a malformed field beside its input', () => {
    expect(refused({ errors: [{ field: 'reason', message: 'must be 1 to 1000 characters' }] }, 400)).toEqual({
      reason: 'Tell us what’s wrong, in at most 1,000 characters.',
    })
    expect(refused({ errors: [{ field: 'serialNumbers[0]', message: 'must be a non-blank string' }] }, 400)).toEqual({
      units: 'Choose the units you’re sending back.',
    })
    expect(refused({ errors: [{ field: 'kind', message: 'must be RETURN or WARRANTY_CLAIM' }] }, 400)).toEqual({
      kind: 'Choose a return or a warranty claim.',
    })
  })

  it('tells the Customer to try again while Returns & Warranty catches up with the Order', () => {
    expect(refused({ reason: 'orderNotKnownYet' }, 503)).toEqual({
      form: 'We’re still catching up with this order. Try again in a moment.',
    })
    expect(refused({ reason: 'productNotKnownYet' }, 503)).toEqual({
      form: 'We’re still catching up with this order. Try again in a moment.',
    })
  })

  it('leaves any other failure to be shown as a whole', () => {
    expect(refused({}, 500)).toBeUndefined()
    expect(refused({ reason: 'somethingNew' })).toBeUndefined()
    expect(refused({}, 404)).toBeUndefined()
  })
})

const requested: Rma = {
  id: '3f2a9c1b-0000-4000-8000-000000000001',
  reference: 'RMA-3F2A9C1B',
  orderId: 'order-1',
  variantId: 'PHN-PIXEL-9',
  productName: 'Pixel 9',
  quantity: 1,
  serialNumbers: ['490154203237518'],
  kind: 'RETURN',
  reason: 'Too big',
  refundAmount: { amountMinor: 79900, currency: 'EUR' },
  status: 'REQUESTED',
  requestedAt: '2026-10-20T10:00:00Z',
  history: [{ status: 'REQUESTED', by: 'CUSTOMER', at: '2026-10-20T10:00:00Z' }],
}

describe('rmaTimelineOf', () => {
  it('shows a requested return’s steps still to come, up to its Refund', () => {
    expect(rmaTimelineOf(requested)).toEqual([
      { status: 'REQUESTED', label: 'Requested', state: 'current', at: '2026-10-20T10:00:00Z' },
      { status: 'APPROVED', label: 'Approved', state: 'upcoming' },
      { status: 'RECEIVED', label: 'Received', state: 'upcoming' },
      { status: 'INSPECTED', label: 'Inspected', state: 'upcoming' },
      { status: 'REFUNDED', label: 'Refunded', state: 'upcoming' },
    ])
  })

  it('ends a warranty claim with its replacement, and shows each step’s note and the Inspection’s outcome', () => {
    const claim: Rma = {
      ...requested,
      kind: 'WARRANTY_CLAIM',
      status: 'INSPECTED',
      inspection: { outcome: 'FAULT_CONFIRMED', note: 'Screen flickers' },
      history: [
        { status: 'REQUESTED', by: 'CUSTOMER', at: '2026-10-20T10:00:00Z' },
        { status: 'APPROVED', by: 'staff-1', at: '2026-10-20T11:00:00Z', note: 'Send it in its box' },
        { status: 'RECEIVED', by: 'staff-1', at: '2026-10-22T09:00:00Z' },
        { status: 'INSPECTED', by: 'staff-2', at: '2026-10-22T10:00:00Z', note: 'Screen flickers' },
      ],
    }
    expect(rmaTimelineOf(claim)).toEqual([
      { status: 'REQUESTED', label: 'Requested', state: 'done', at: '2026-10-20T10:00:00Z' },
      { status: 'APPROVED', label: 'Approved', state: 'done', at: '2026-10-20T11:00:00Z', note: 'Send it in its box' },
      { status: 'RECEIVED', label: 'Received', state: 'done', at: '2026-10-22T09:00:00Z' },
      {
        status: 'INSPECTED',
        label: 'Inspected: fault confirmed',
        state: 'current',
        at: '2026-10-22T10:00:00Z',
        note: 'Screen flickers',
      },
      { status: 'EXCHANGED', label: 'Replacement sent', state: 'upcoming' },
    ])
  })

  it('ends with a final status, such as a rejection with its reason', () => {
    const rejected: Rma = {
      ...requested,
      status: 'REJECTED',
      history: [...requested.history, { status: 'REJECTED', by: 'staff-1', at: '2026-10-21T08:00:00Z', note: 'Worn' }],
    }
    expect(rmaTimelineOf(rejected).map((step) => [step.label, step.state, step.note])).toEqual([
      ['Requested', 'done', undefined],
      ['Rejected', 'current', 'Worn'],
    ])
  })

  it('starts an RMA requested before the RMA history with its request', () => {
    const sprint5: Rma = {
      ...requested,
      status: 'APPROVED',
      history: [{ status: 'APPROVED', by: 'staff-1', at: '2026-10-21T08:00:00Z' }],
    }
    expect(rmaTimelineOf(sprint5).slice(0, 2)).toEqual([
      { status: 'REQUESTED', label: 'Requested', state: 'done', at: '2026-10-20T10:00:00Z' },
      { status: 'APPROVED', label: 'Approved', state: 'current', at: '2026-10-21T08:00:00Z' },
    ])
  })
})

describe('rmaRefreshInterval', () => {
  it('asks again every 10 seconds while the units are received or inspected, and not otherwise', () => {
    expect(rmaRefreshInterval('RECEIVED')).toBe(10_000)
    expect(rmaRefreshInterval('INSPECTED')).toBe(10_000)
    expect(rmaRefreshInterval('APPROVED')).toBe(false)
    expect(rmaRefreshInterval('REFUNDED')).toBe(false)
    expect(rmaRefreshInterval(undefined)).toBe(false)
  })
})
