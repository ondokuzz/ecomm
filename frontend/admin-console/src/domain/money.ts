/** An amount in the currency's minor unit (cents for EUR), and an ISO 4217 code: the platform's `Money`. */
export interface Money {
  amountMinor: number
  currency: string
}

/** Renders Money as currency, with as many fraction digits as the currency has minor units. */
export function formatMoney(money: Money, locale?: string): string {
  const format = new Intl.NumberFormat(locale, { style: 'currency', currency: money.currency })
  return format.format(money.amountMinor / 10 ** (minorDigitsOf(money.currency) ?? 2))
}

/**
 * The decimal Staff type, such as `799.00`, as Money in `currency`: undefined unless it is a plain
 * non-negative decimal with no more fraction digits than the currency has, and `currency` an ISO
 * 4217 code. It is read digit by digit, so `1.13` is 113 cents and never 112.
 */
export function moneyOf(decimal: string, currency: string): Money | undefined {
  const code = currency.trim().toUpperCase()
  const digits = minorDigitsOf(code)
  const match = /^(\d+)(?:\.(\d+))?$/.exec(decimal.trim())
  if (digits === undefined || !match) return undefined
  const [, whole, fraction = ''] = match
  if (fraction.length > digits) return undefined
  const amountMinor = Number(whole + fraction.padEnd(digits, '0'))
  return Number.isSafeInteger(amountMinor) ? { amountMinor, currency: code } : undefined
}

/** Money as the decimal Staff edit, with all of its currency's fraction digits, such as `799.00`. */
export function decimalOf(money: Money): string {
  const digits = minorDigitsOf(money.currency) ?? 2
  const padded = String(money.amountMinor).padStart(digits + 1, '0')
  return digits === 0 ? padded : `${padded.slice(0, -digits)}.${padded.slice(-digits)}`
}

// The ISO 4217 currencies the browser knows, which leaves out codes such as XXX (no currency) and XTS (testing).
const currencies = new Set(Intl.supportedValuesOf('currency'))

/** How many fraction digits `currency` has, or undefined when it isn't an ISO 4217 currency. */
function minorDigitsOf(currency: string): number | undefined {
  if (!currencies.has(currency)) return undefined
  return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits
}
