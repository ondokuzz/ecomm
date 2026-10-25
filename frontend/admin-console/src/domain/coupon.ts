import type { FieldErrors } from '../api/fieldErrors'
import type { Currencies } from './money'
import { type Terms, type TermsForm, termsFormOf, termsRequest, wholeNumber } from './promotion'

/**
 * Where a Coupon stands now, as Promotions works it out: `exhausted` once its redemptions and live
 * holds reach its Usage limit.
 */
export type CouponState = 'active' | 'scheduled' | 'expired' | 'off' | 'exhausted'

/** A Coupon as Staff send it. Without a Usage limit it is used without limit. */
export interface CouponRequest extends Terms {
  code: string
  /** The most redemptions it may have in all; null for no limit. */
  usageLimit: number | null
  /** The most each Customer may redeem it; null for no limit. */
  perCustomerLimit: number | null
}

/**
 * A code a Customer enters at checkout for a Discount, as Promotions answers it: its code
 * upper-case, with its redemptions, its live Redemption holds and its state now.
 */
export interface Coupon extends CouponRequest {
  redemptions: number
  holds: number
  state: CouponState
}

/** The Coupon editor's state. A saved Coupon's code can't change; a blank limit is none. */
export interface CouponForm extends TermsForm {
  code: string
  usageLimit: string
  perCustomerLimit: string
}

/** `coupon` laid out for editing, or a new Coupon with no code and no limits, starting `now`. */
export function formOf(coupon: Coupon | undefined, currencies: Currencies, now: Date): CouponForm {
  return {
    code: coupon?.code ?? '',
    ...termsFormOf(coupon, currencies, now),
    usageLimit: coupon?.usageLimit?.toString() ?? '',
    perCustomerLimit: coupon?.perCustomerLimit?.toString() ?? '',
  }
}

/**
 * The form as Promotions takes it, or the fields it can't send. The code is trimmed; Promotions
 * upper-cases it and names it if it isn't one.
 */
export function couponRequest(
  form: CouponForm,
  currencies: Currencies,
): { request: CouponRequest; errors: FieldErrors } | { request?: undefined; errors: FieldErrors } {
  const errors: FieldErrors = {}
  const terms = termsRequest(form, currencies, errors)
  const usageLimit = limit(form.usageLimit, 'usageLimit', errors)
  const perCustomerLimit = limit(form.perCustomerLimit, 'perCustomerLimit', errors)
  if (Object.keys(errors).length > 0) return { errors }
  return { request: { code: form.code.trim(), ...terms, usageLimit, perCustomerLimit }, errors }
}

/** `coupon` as Staff send it, switched on or off and otherwise as it is. */
export function switched(coupon: Coupon, active: boolean): CouponRequest {
  const { redemptions: _, holds: __, state: ___, ...request } = coupon
  return { ...request, active }
}

/** How much of the Coupon is used, such as `40 of 100 used, 2 held`. */
export function describeUse(coupon: Coupon): string {
  const used =
    coupon.usageLimit === null ? `${coupon.redemptions} used` : `${coupon.redemptions} of ${coupon.usageLimit} used`
  return coupon.holds > 0 ? `${used}, ${coupon.holds} held` : used
}

function limit(text: string, field: string, errors: FieldErrors): number | null {
  if (text.trim() === '') return null
  const value = wholeNumber(text)
  if (value === undefined || value < 1) {
    errors[field] = 'must be a whole number, at least 1'
    return null
  }
  return value
}
