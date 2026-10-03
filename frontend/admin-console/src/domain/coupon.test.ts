import { describe, expect, it } from 'vitest'
import { type Coupon, couponRequest, formOf, switched } from './coupon'
import { currenciesOf } from './money'

const currencies = currenciesOf([
  { code: 'EUR', minorDigits: 2 },
  { code: 'USD', minorDigits: 2 },
])

// The tests run in Asia/Tokyo, nine hours ahead of UTC (see vite.config.ts).
const now = new Date('2026-10-03T08:15:42Z')

const spring25: Coupon = {
  code: 'SPRING25',
  discount: { type: 'AMOUNT_OFF', percentOff: null, amountOff: { amountMinor: 2500, currency: 'USD' } },
  minimumSubtotal: { amountMinor: 10000, currency: 'USD' },
  validFrom: '2027-03-01T00:00:00Z',
  validUntil: '2027-06-01T00:00:00Z',
  active: true,
}

describe('formOf', () => {
  it('lays a Coupon out for editing', () => {
    expect(formOf(spring25, currencies, now)).toEqual({
      code: 'SPRING25',
      discountType: 'AMOUNT_OFF',
      percentOff: '',
      amountOff: { amount: '25.00', currency: 'USD' },
      minimumSubtotal: { amount: '100.00', currency: 'USD' },
      validFrom: '2027-03-01T09:00',
      validUntil: '2027-06-01T09:00',
      active: true,
    })
  })

  it('starts a new Coupon with no code', () => {
    expect(formOf(undefined, currencies, now)).toMatchObject({ code: '', discountType: 'PERCENT_OFF', active: true })
  })
})

describe('couponRequest', () => {
  it('turns the form back into the Coupon it was laid out from', () => {
    expect(couponRequest(formOf(spring25, currencies, now), currencies)).toEqual({ request: spring25, errors: {} })
  })

  it('trims the code and leaves its case for Promotions', () => {
    const form = { ...formOf(undefined, currencies, now), code: ' summer-10 ', percentOff: '10' }
    expect(couponRequest(form, currencies).request?.code).toBe('summer-10')
  })

  it('names the fields it can’t send', () => {
    const form = { ...formOf(spring25, currencies, now), minimumSubtotal: { amount: '1,000', currency: 'USD' } }
    expect(couponRequest(form, currencies)).toEqual({
      errors: { minimumSubtotal: 'must be an amount such as 25.00' },
    })
  })
})

describe('switched', () => {
  it('is the Coupon switched on or off', () => {
    expect(switched(spring25, false)).toEqual({ ...spring25, active: false })
  })
})
