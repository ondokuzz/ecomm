import { describe, expect, it } from 'vitest'
import { couponRejection } from './coupon'

describe('couponRejection', () => {
  it.each([
    ['unknown', "We don't know that code. Check it and try again."],
    ['inactive', "That code isn't available any more."],
    ['notYetValid', "That code isn't valid yet."],
    ['expired', 'That code has expired.'],
    ['belowMinimum', "Your order doesn't reach that code's minimum spend yet."],
    ['currencyMismatch', "That code can't be used with this order's currency."],
  ])('says why a %s Coupon does not apply', (reason, message) => {
    expect(couponRejection(422, { reason })).toEqual({ reason, message })
  })

  it('gives each reason its own message', () => {
    const reasons = ['unknown', 'inactive', 'notYetValid', 'expired', 'belowMinimum', 'currencyMismatch']
    const messages = reasons.map((reason) => couponRejection(422, { reason })?.message)
    expect(new Set(messages).size).toBe(reasons.length)
  })

  it('says the code does not apply when the reason is one it does not know', () => {
    expect(couponRejection(422, { reason: 'somethingNew' })).toEqual({
      reason: 'somethingNew',
      message: "That code can't be applied to this order.",
    })
    expect(couponRejection(422, {})?.message).toBe("That code can't be applied to this order.")
  })

  it('is undefined for any failure other than a 422', () => {
    expect(couponRejection(502, { reason: 'expired' })).toBeUndefined()
    expect(couponRejection(400, {})).toBeUndefined()
  })
})
