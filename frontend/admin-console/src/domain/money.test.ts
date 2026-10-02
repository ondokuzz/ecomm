import { describe, expect, it } from 'vitest'
import { currenciesOf, decimalOf, formatMoney, moneyOf } from './money'

// As Catalog lists them. HUF's minor unit has 2 digits in ISO 4217, though browsers' data says 0.
const currencies = currenciesOf([
  { code: 'BHD', minorDigits: 3 },
  { code: 'EUR', minorDigits: 2 },
  { code: 'HUF', minorDigits: 2 },
  { code: 'JPY', minorDigits: 0 },
])

describe('moneyOf', () => {
  it('reads a decimal as Money in the currency’s minor unit', () => {
    expect(moneyOf('799.00', 'EUR', currencies)).toEqual({ amountMinor: 79900, currency: 'EUR' })
    expect(moneyOf('0.07', 'EUR', currencies)).toEqual({ amountMinor: 7, currency: 'EUR' })
  })

  it('takes a whole amount, a single fraction digit and surrounding spaces', () => {
    expect(moneyOf('799', 'EUR', currencies)).toEqual({ amountMinor: 79900, currency: 'EUR' })
    expect(moneyOf(' 799.5 ', 'EUR', currencies)).toEqual({ amountMinor: 79950, currency: 'EUR' })
    expect(moneyOf('0', 'EUR', currencies)).toEqual({ amountMinor: 0, currency: 'EUR' })
  })

  it('has no rounding error, as floating point would', () => {
    // 1.13 * 100 is 112.99999999999999 in floating point.
    expect(moneyOf('1.13', 'EUR', currencies)).toEqual({ amountMinor: 113, currency: 'EUR' })
    expect(moneyOf('4.35', 'EUR', currencies)).toEqual({ amountMinor: 435, currency: 'EUR' })
  })

  it('follows the minor unit Catalog gives the currency, not the browser’s', () => {
    expect(moneyOf('1500', 'JPY', currencies)).toEqual({ amountMinor: 1500, currency: 'JPY' })
    expect(moneyOf('1.250', 'BHD', currencies)).toEqual({ amountMinor: 1250, currency: 'BHD' })
    expect(moneyOf('1000', 'HUF', currencies)).toEqual({ amountMinor: 100000, currency: 'HUF' })
    expect(moneyOf('1500.5', 'JPY', currencies)).toBeUndefined()
  })

  it('upper-cases the currency code', () => {
    expect(moneyOf('1.00', ' eur ', currencies)).toEqual({ amountMinor: 100, currency: 'EUR' })
  })

  it('refuses more fraction digits than the currency has', () => {
    expect(moneyOf('799.001', 'EUR', currencies)).toBeUndefined()
  })

  it('refuses anything but a plain non-negative decimal', () => {
    for (const text of ['', ' ', '-1.00', '+1', 'abc', '1,299.00', '1.2.3', '.5', '5.', '1e3', '€5']) {
      expect(moneyOf(text, 'EUR', currencies), text).toBeUndefined()
    }
  })

  it('refuses an amount too large to count exactly', () => {
    expect(moneyOf('90071992547409.92', 'EUR', currencies)).toBeUndefined()
  })

  it('refuses a currency Catalog doesn’t price in', () => {
    for (const code of ['', 'EU', 'EURO', 'ABC', 'XXX']) {
      expect(moneyOf('1.00', code, currencies), code).toBeUndefined()
    }
  })
})

describe('decimalOf', () => {
  it('writes Money as a decimal with all of the currency’s fraction digits', () => {
    expect(decimalOf({ amountMinor: 79900, currency: 'EUR' }, currencies)).toBe('799.00')
    expect(decimalOf({ amountMinor: 7, currency: 'EUR' }, currencies)).toBe('0.07')
    expect(decimalOf({ amountMinor: 0, currency: 'EUR' }, currencies)).toBe('0.00')
    expect(decimalOf({ amountMinor: 1500, currency: 'JPY' }, currencies)).toBe('1500')
    expect(decimalOf({ amountMinor: 1250, currency: 'BHD' }, currencies)).toBe('1.250')
    expect(decimalOf({ amountMinor: 100000, currency: 'HUF' }, currencies)).toBe('1000.00')
  })

  it('round-trips through moneyOf', () => {
    for (const amountMinor of [0, 1, 99, 100, 79900, 123456789]) {
      const money = { amountMinor, currency: 'EUR' }
      expect(moneyOf(decimalOf(money, currencies), 'EUR', currencies)).toEqual(money)
    }
  })
})

describe('formatMoney', () => {
  it('shows Money as currency', () => {
    expect(formatMoney({ amountMinor: 79900, currency: 'EUR' }, currencies, 'en-US')).toBe('€799.00')
  })

  it('shows as many fraction digits as Catalog gives the currency', () => {
    expect(formatMoney({ amountMinor: 100050, currency: 'HUF' }, currencies, 'en-US')).toBe('HUF\u00a01,000.50')
  })
})

describe('Money in a currency Catalog doesn’t list', () => {
  it('has no decimal to show, rather than a wrong one', () => {
    expect(() => decimalOf({ amountMinor: 100, currency: 'ABC' }, currencies)).toThrow(/ABC/)
    expect(() => formatMoney({ amountMinor: 100, currency: 'ABC' }, currencies)).toThrow(/ABC/)
  })
})
