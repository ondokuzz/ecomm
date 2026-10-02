/** An amount in the currency's minor unit (cents for EUR), and an ISO 4217 code: the platform's `Money`. */
export interface Money {
  amountMinor: number
  currency: string
}

/** A currency a Price can be in, as Catalog lists it: its ISO 4217 code and how many digits its minor unit has. */
export interface Currency {
  code: string
  minorDigits: number
}

/**
 * Catalog's currencies by code. Money is read and written by these alone: browsers' own currency
 * data disagrees with ISO 4217 on some minor units (HUF has 2 digits, not 0), and an amount read
 * with the wrong one is off a hundredfold.
 */
export type Currencies = ReadonlyMap<string, Currency>

export function currenciesOf(list: readonly Currency[]): Currencies {
  return new Map(list.map((currency) => [currency.code, currency]))
}

/** Renders Money as currency, with as many fraction digits as Catalog gives the currency. */
export function formatMoney(money: Money, currencies: Currencies, locale?: string): string {
  const digits = minorDigitsOf(money, currencies)
  const format = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: money.currency,
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
  return format.format(money.amountMinor / 10 ** digits)
}

/**
 * The decimal Staff type, such as `799.00`, as Money in `currency`: undefined unless it is a plain
 * non-negative decimal with no more fraction digits than the currency has, and Catalog prices in
 * `currency`. It is read digit by digit, so `1.13` is 113 cents and never 112.
 */
export function moneyOf(decimal: string, currency: string, currencies: Currencies): Money | undefined {
  const found = currencies.get(currency.trim().toUpperCase())
  const match = /^(\d+)(?:\.(\d+))?$/.exec(decimal.trim())
  if (!found || !match) return undefined
  const [, whole, fraction = ''] = match
  if (fraction.length > found.minorDigits) return undefined
  const amountMinor = Number(whole + fraction.padEnd(found.minorDigits, '0'))
  return Number.isSafeInteger(amountMinor) ? { amountMinor, currency: found.code } : undefined
}

/** Money as the decimal Staff edit, with all of its currency's fraction digits, such as `799.00`. */
export function decimalOf(money: Money, currencies: Currencies): string {
  const digits = minorDigitsOf(money, currencies)
  const padded = String(money.amountMinor).padStart(digits + 1, '0')
  return digits === 0 ? padded : `${padded.slice(0, -digits)}.${padded.slice(-digits)}`
}

// Catalog keeps Prices only in currencies it lists, so any other is a mistake to show, not to guess at.
function minorDigitsOf(money: Money, currencies: Currencies): number {
  const currency = currencies.get(money.currency)
  if (!currency) throw new Error(`Catalog lists no currency ${money.currency}`)
  return currency.minorDigits
}
