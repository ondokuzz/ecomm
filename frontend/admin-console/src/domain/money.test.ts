import { describe, expect, it } from 'vitest'
import { decimalOf, formatMoney, moneyOf } from './money'

describe('moneyOf', () => {
  it('reads a decimal as Money in the currency’s minor unit', () => {
    expect(moneyOf('799.00', 'EUR')).toEqual({ amountMinor: 79900, currency: 'EUR' })
    expect(moneyOf('0.07', 'EUR')).toEqual({ amountMinor: 7, currency: 'EUR' })
  })

  it('takes a whole amount, a single fraction digit and surrounding spaces', () => {
    expect(moneyOf('799', 'EUR')).toEqual({ amountMinor: 79900, currency: 'EUR' })
    expect(moneyOf(' 799.5 ', 'EUR')).toEqual({ amountMinor: 79950, currency: 'EUR' })
    expect(moneyOf('0', 'EUR')).toEqual({ amountMinor: 0, currency: 'EUR' })
  })

  it('has no rounding error, as floating point would', () => {
    // 1.13 * 100 is 112.99999999999999 in floating point.
    expect(moneyOf('1.13', 'EUR')).toEqual({ amountMinor: 113, currency: 'EUR' })
    expect(moneyOf('4.35', 'EUR')).toEqual({ amountMinor: 435, currency: 'EUR' })
  })

  it('follows the currency’s minor unit', () => {
    expect(moneyOf('1500', 'JPY')).toEqual({ amountMinor: 1500, currency: 'JPY' })
    expect(moneyOf('1.250', 'BHD')).toEqual({ amountMinor: 1250, currency: 'BHD' })
    expect(moneyOf('1500.5', 'JPY')).toBeUndefined()
  })

  it('upper-cases the currency code', () => {
    expect(moneyOf('1.00', ' eur ')).toEqual({ amountMinor: 100, currency: 'EUR' })
  })

  it('refuses more fraction digits than the currency has', () => {
    expect(moneyOf('799.001', 'EUR')).toBeUndefined()
  })

  it('refuses anything but a plain non-negative decimal', () => {
    for (const text of ['', ' ', '-1.00', '+1', 'abc', '1,299.00', '1.2.3', '.5', '5.', '1e3', '€5']) {
      expect(moneyOf(text, 'EUR'), text).toBeUndefined()
    }
  })

  it('refuses an amount too large to count exactly', () => {
    expect(moneyOf('90071992547409.92', 'EUR')).toBeUndefined()
  })

  it('refuses a currency that is not an ISO 4217 code', () => {
    expect(moneyOf('1.00', 'EU')).toBeUndefined()
    expect(moneyOf('1.00', 'EURO')).toBeUndefined()
    expect(moneyOf('1.00', '')).toBeUndefined()
  })

  it('refuses three letters that name no currency to price in', () => {
    expect(moneyOf('1.00', 'ABC')).toBeUndefined()
    // ISO 4217 codes for testing and for "no currency" are no currency to price in.
    expect(moneyOf('1.00', 'XTS')).toBeUndefined()
    expect(moneyOf('1.00', 'XXX')).toBeUndefined()
  })
})

describe('decimalOf', () => {
  it('writes Money as a decimal with all of the currency’s fraction digits', () => {
    expect(decimalOf({ amountMinor: 79900, currency: 'EUR' })).toBe('799.00')
    expect(decimalOf({ amountMinor: 7, currency: 'EUR' })).toBe('0.07')
    expect(decimalOf({ amountMinor: 0, currency: 'EUR' })).toBe('0.00')
    expect(decimalOf({ amountMinor: 1500, currency: 'JPY' })).toBe('1500')
    expect(decimalOf({ amountMinor: 1250, currency: 'BHD' })).toBe('1.250')
  })

  it('round-trips through moneyOf', () => {
    for (const amountMinor of [0, 1, 99, 100, 79900, 123456789]) {
      const money = { amountMinor, currency: 'EUR' }
      expect(moneyOf(decimalOf(money), 'EUR')).toEqual(money)
    }
  })
})

describe('formatMoney', () => {
  it('shows Money as currency', () => {
    expect(formatMoney({ amountMinor: 79900, currency: 'EUR' }, 'en-US')).toBe('€799.00')
  })
})
