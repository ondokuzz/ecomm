/** An amount in the currency's minor unit (cents for EUR), and an ISO 4217 code: the platform's `Money`. */
export interface Money {
  amountMinor: number
  currency: string
}

/** A Currency as Catalog lists it: its ISO 4217 code and how many digits its Minor unit has. */
export interface Currency {
  code: string
  minorDigits: number
}

/**
 * Catalog's Currencies by code. Money is shown by these alone: browsers' own currency data
 * disagrees with ISO 4217 on some Minor units (HUF has 2 digits, not 0), and an amount shown with
 * the wrong one is off a hundredfold.
 */
export type Currencies = ReadonlyMap<string, Currency>

export function currenciesOf(list: readonly Currency[]): Currencies {
  return new Map(list.map((currency) => [currency.code, currency]))
}

/**
 * Renders Money as currency, with as many fraction digits as Catalog gives its Currency. Throws
 * for a Currency Catalog doesn't list, rather than guess its Minor unit.
 */
export function formatMoney(money: Money, currencies: Currencies, locale?: string): string {
  const currency = currencies.get(money.currency)
  if (!currency) throw new Error(`Catalog lists no Currency ${money.currency}`)
  const format = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: money.currency,
    minimumFractionDigits: currency.minorDigits,
    maximumFractionDigits: currency.minorDigits,
  })
  return format.format(money.amountMinor / 10 ** currency.minorDigits)
}

export function timesMoney(money: Money, quantity: number): Money {
  return { amountMinor: money.amountMinor * quantity, currency: money.currency }
}

/** The sum of amounts in one currency; undefined when there is nothing to add or the currencies differ. */
export function sumMoney(amounts: Money[]): Money | undefined {
  if (amounts.length === 0) return undefined
  const currency = amounts[0].currency
  if (amounts.some((m) => m.currency !== currency)) return undefined
  return { amountMinor: amounts.reduce((total, m) => total + m.amountMinor, 0), currency }
}
