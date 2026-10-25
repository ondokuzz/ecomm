import { describe, expect, it } from 'vitest'
import { type Coupon, couponRequest, describeUse, formOf, switched } from './coupon'
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
  usageLimit: 100,
  perCustomerLimit: 1,
  redemptions: 40,
  holds: 2,
  state: 'scheduled',
}

const { redemptions: _, holds: __, state: ___, ...spring25Request } = spring25

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
      usageLimit: '100',
      perCustomerLimit: '1',
    })
  })

  it('starts a new Coupon with no code', () => {
    expect(formOf(undefined, currencies, now)).toMatchObject({
      code: '',
      discountType: 'PERCENT_OFF',
      active: true,
      usageLimit: '',
      perCustomerLimit: '',
    })
  })
})

describe('couponRequest', () => {
  it('turns the form back into the Coupon it was laid out from', () => {
    expect(couponRequest(formOf(spring25, currencies, now), currencies)).toEqual({
      request: spring25Request,
      errors: {},
    })
  })

  it('sends a blank limit as none, so the Coupon is used without limit', () => {
    const form = { ...formOf(spring25, currencies, now), usageLimit: ' ', perCustomerLimit: '' }
    expect(couponRequest(form, currencies).request).toMatchObject({ usageLimit: null, perCustomerLimit: null })
  })

  it('names a limit that isn’t a whole number of at least 1', () => {
    const form = { ...formOf(spring25, currencies, now), usageLimit: '0', perCustomerLimit: '1.5' }
    expect(couponRequest(form, currencies)).toEqual({
      errors: {
        usageLimit: 'must be a whole number, at least 1',
        perCustomerLimit: 'must be a whole number, at least 1',
      },
    })
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
  it('is the Coupon as Staff send it, switched on or off, its limits kept', () => {
    expect(switched(spring25, false)).toEqual({ ...spring25Request, active: false })
  })
})

describe('describeUse', () => {
  it('counts redemptions against the Usage limit, with the holds', () => {
    expect(describeUse(spring25)).toBe('40 of 100 used, 2 held')
  })

  it('counts them alone without a limit, and leaves out no holds', () => {
    expect(describeUse({ ...spring25, usageLimit: null, holds: 0 })).toBe('40 used')
  })
})
