import type { FieldErrors } from '../api/fieldErrors'
import type { Currencies } from './money'
import { type Terms, type TermsForm, termsFormOf, termsRequest } from './promotion'

/** A code a Customer enters at checkout for a Discount, as Promotions answers it: its code upper-case. */
export interface Coupon extends Terms {
  code: string
}

/** The Coupon editor's state. A saved Coupon's code can't change. */
export interface CouponForm extends TermsForm {
  code: string
}

/** `coupon` laid out for editing, or a new Coupon with no code, starting `now`. */
export function formOf(coupon: Coupon | undefined, currencies: Currencies, now: Date): CouponForm {
  return { code: coupon?.code ?? '', ...termsFormOf(coupon, currencies, now) }
}

/**
 * The form as Promotions takes it, or the fields of its terms it can't send. The code is trimmed;
 * Promotions upper-cases it and names it if it isn't one.
 */
export function couponRequest(
  form: CouponForm,
  currencies: Currencies,
): { request: Coupon; errors: FieldErrors } | { request?: undefined; errors: FieldErrors } {
  const errors: FieldErrors = {}
  const terms = termsRequest(form, currencies, errors)
  if (Object.keys(errors).length > 0) return { errors }
  return { request: { code: form.code.trim(), ...terms }, errors }
}

/** `coupon` switched on or off and otherwise as it is. */
export function switched(coupon: Coupon, active: boolean): Coupon {
  return { ...coupon, active }
}
