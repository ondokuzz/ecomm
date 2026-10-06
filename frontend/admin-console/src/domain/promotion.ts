import type { FieldErrors } from '../api/fieldErrors'
import { type Currencies, type Money, decimalOf, formatMoney, moneyOf } from './money'

/** A percentage off a subtotal, or a fixed amount of Money off it. */
export type DiscountType = 'PERCENT_OFF' | 'AMOUNT_OFF'

/** What a Coupon or a Campaign takes off, as Promotions has it: only its type's field is set. */
export interface DiscountRule {
  type: DiscountType
  /** 1 to 100, for `PERCENT_OFF`. */
  percentOff: number | null
  /** Positive, for `AMOUNT_OFF`. */
  amountOff: Money | null
}

/** What a Coupon and a Campaign share: a discount, an optional minimum and a validity window. */
export interface Terms {
  discount: DiscountRule
  minimumSubtotal: Money | null
  /** ISO 8601 instants: valid from `validFrom` up to, but not including, `validUntil`. */
  validFrom: string
  validUntil: string
  /** Whether Staff have it switched on. */
  active: boolean
}

/** Money being edited: a decimal such as `25.00`, and the currency it is in. */
export interface MoneyForm {
  amount: string
  currency: string
}

/**
 * The shared part of a Coupon's or a Campaign's form. Only the discount type's own field is sent.
 * A blank minimum is none. Times are local, as `<input type="datetime-local">` takes them.
 */
export interface TermsForm {
  discountType: DiscountType
  percentOff: string
  amountOff: MoneyForm
  minimumSubtotal: MoneyForm
  validFrom: string
  validUntil: string
  active: boolean
}

const defaultCurrency = 'EUR'
const week = 7 * 24 * 60 * 60 * 1000

/**
 * `terms` laid out for editing, each amount as a decimal by its own currency's minor unit, or new
 * terms: a percentage off, active from the minute `now` is in for a week.
 */
export function termsFormOf(terms: Terms | undefined, currencies: Currencies, now: Date): TermsForm {
  if (!terms) {
    const from = new Date(now)
    from.setSeconds(0, 0)
    return {
      discountType: 'PERCENT_OFF',
      percentOff: '',
      amountOff: blankMoney(),
      minimumSubtotal: blankMoney(),
      validFrom: localOf(from),
      validUntil: localOf(new Date(from.getTime() + week)),
      active: true,
    }
  }
  const { discount, minimumSubtotal } = terms
  return {
    discountType: discount.type,
    percentOff: discount.percentOff?.toString() ?? '',
    amountOff: moneyFormOf(discount.amountOff, currencies),
    minimumSubtotal: moneyFormOf(minimumSubtotal, currencies),
    validFrom: localOf(new Date(terms.validFrom)),
    validUntil: localOf(new Date(terms.validUntil)),
    active: terms.active,
  }
}

/**
 * The form as Promotions takes it, with each field it can't send named in `errors` as Promotions
 * would name it: a percentage that isn't a whole number from 1 to 100, an amount that doesn't read
 * as Money in its currency, a currency Catalog doesn't price in, or a time that isn't one. The
 * rest, such as a window that ends before it starts, Promotions checks and names.
 */
export function termsRequest(form: TermsForm, currencies: Currencies, errors: FieldErrors): Terms {
  let discount: DiscountRule
  if (form.discountType === 'PERCENT_OFF') {
    const percent = wholeNumber(form.percentOff)
    if (percent === undefined || percent < 1 || percent > 100) {
      errors['discount.percentOff'] = 'must be a whole number from 1 to 100'
    }
    discount = { type: 'PERCENT_OFF', percentOff: percent ?? null, amountOff: null }
  } else {
    discount = {
      type: 'AMOUNT_OFF',
      percentOff: null,
      amountOff: money(form.amountOff, 'discount.amountOff', currencies, errors),
    }
  }
  const minimumSubtotal =
    form.minimumSubtotal.amount.trim() === ''
      ? null
      : money(form.minimumSubtotal, 'minimumSubtotal', currencies, errors)
  return {
    discount,
    minimumSubtotal,
    validFrom: instant(form.validFrom, 'validFrom', errors),
    validUntil: instant(form.validUntil, 'validUntil', errors),
    active: form.active,
  }
}

/** A whole number with nothing else around it but spaces, such as ` 7 `; undefined for anything else. */
export function wholeNumber(text: string): number | undefined {
  const trimmed = text.trim()
  return /^\d+$/.test(trimmed) && Number.isSafeInteger(Number(trimmed)) ? Number(trimmed) : undefined
}

/** An instant as `<input type="datetime-local">` shows it, in local time to the minute: `2027-03-01T09:00`. */
export function localOf(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

function blankMoney(): MoneyForm {
  return { amount: '', currency: defaultCurrency }
}

function moneyFormOf(money: Money | null, currencies: Currencies): MoneyForm {
  return money ? { amount: decimalOf(money, currencies), currency: money.currency } : blankMoney()
}

// The currency is named first: until it is one Catalog prices in, no amount can be read in it.
function money(form: MoneyForm, field: string, currencies: Currencies, errors: FieldErrors): Money | null {
  if (moneyOf('0', form.currency, currencies) === undefined) {
    errors[`${field}.currency`] = 'must be a currency Catalog prices in, such as EUR'
    return null
  }
  const amount = moneyOf(form.amount, form.currency, currencies)
  if (!amount) errors[field] = 'must be an amount such as 25.00'
  return amount ?? null
}

// `datetime-local` gives `2027-03-01T09:00`, which Date reads as local time.
function instant(local: string, field: string, errors: FieldErrors): string {
  const date = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(local) ? new Date(local) : undefined
  if (!date || Number.isNaN(date.getTime())) {
    errors[field] = 'must be a date and time'
    return ''
  }
  return date.toISOString().replace('.000Z', 'Z')
}

/** What a discount takes off, as Staff read it in a list: `15% off` or `€25.00 off`. */
export function describeDiscount(discount: DiscountRule, currencies: Currencies, locale?: string): string {
  return discount.type === 'PERCENT_OFF'
    ? `${discount.percentOff}% off`
    : `${formatMoney(discount.amountOff!, currencies, locale)} off`
}

/** A validity window as Staff read it in a list, in their own time zone. */
export function describeWindow(terms: Pick<Terms, 'validFrom' | 'validUntil'>, locale?: string): string {
  const format = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' })
  return `${format.format(new Date(terms.validFrom))} – ${format.format(new Date(terms.validUntil))}`
}
