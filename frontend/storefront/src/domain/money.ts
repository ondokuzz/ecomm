/** An amount in the currency's minor unit (cents for EUR), and an ISO 4217 code: the platform's `Money`. */
export interface Money {
  amountMinor: number
  currency: string
}

/** Renders Money as currency, with as many fraction digits as the currency has minor units. */
export function formatMoney(money: Money, locale?: string): string {
  const format = new Intl.NumberFormat(locale, { style: 'currency', currency: money.currency })
  const minorDigits = format.resolvedOptions().maximumFractionDigits ?? 2
  return format.format(money.amountMinor / 10 ** minorDigits)
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
