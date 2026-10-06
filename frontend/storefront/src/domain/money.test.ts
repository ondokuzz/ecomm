import { describe, expect, it } from 'vitest'
import { currenciesOf, decimalOf, formatMoney, moneyOf, sumMoney, timesMoney } from './money'

// As Catalog lists them. HUF's Minor unit has 2 digits in ISO 4217, though browsers' data says 0.
const currencies = currenciesOf([
  { code: 'EUR', minorDigits: 2 },
  { code: 'HUF', minorDigits: 2 },
  { code: 'JPY', minorDigits: 0 },
  { code: 'KWD', minorDigits: 3 },
])

// Intl separates currency and amount with a no-break space; compare with plain ones.
const format = (money: Parameters<typeof formatMoney>[0], locale: string) =>
  formatMoney(money, currencies, locale).replace(/\s/g, ' ')

describe('formatMoney', () => {
  it('renders minor units as the currency', () => {
    expect(format({ amountMinor: 79900, currency: 'EUR' }, 'en-US')).toBe('€799.00')
  })

  it('knows currencies without minor units', () => {
    expect(format({ amountMinor: 1500, currency: 'JPY' }, 'en-US')).toBe('¥1,500')
  })

  it('knows currencies with three minor digits', () => {
    expect(format({ amountMinor: 1234, currency: 'KWD' }, 'en-US')).toBe('KWD 1.234')
  })

  it('follows the Minor unit Catalog gives the Currency, not the browser’s', () => {
    expect(format({ amountMinor: 100000, currency: 'HUF' }, 'en-US')).toBe('HUF 1,000.00')
  })

  it('follows the locale', () => {
    expect(format({ amountMinor: 123456, currency: 'EUR' }, 'de-DE')).toBe('1.234,56 €')
  })

  it('has nothing to show for a Currency Catalog doesn’t list, rather than a guess', () => {
    expect(() => formatMoney({ amountMinor: 100, currency: 'ABC' }, currencies)).toThrow(/ABC/)
  })
})

describe('timesMoney', () => {
  it('multiplies the amount, keeping the currency', () => {
    expect(timesMoney({ amountMinor: 79900, currency: 'EUR' }, 3)).toEqual({
      amountMinor: 239700,
      currency: 'EUR',
    })
  })
})

describe('sumMoney', () => {
  it('adds amounts in one currency', () => {
    expect(
      sumMoney([
        { amountMinor: 100, currency: 'EUR' },
        { amountMinor: 250, currency: 'EUR' },
      ]),
    ).toEqual({ amountMinor: 350, currency: 'EUR' })
  })

  it('has no sum for nothing', () => {
    expect(sumMoney([])).toBeUndefined()
  })

  it('has no sum across currencies', () => {
    expect(
      sumMoney([
        { amountMinor: 100, currency: 'EUR' },
        { amountMinor: 100, currency: 'USD' },
      ]),
    ).toBeUndefined()
  })
})

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
    expect(moneyOf('1.250', 'KWD', currencies)).toEqual({ amountMinor: 1250, currency: 'KWD' })
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
    expect(decimalOf({ amountMinor: 1250, currency: 'KWD' }, currencies)).toBe('1.250')
    expect(decimalOf({ amountMinor: 100000, currency: 'HUF' }, currencies)).toBe('1000.00')
  })

  it('round-trips through moneyOf', () => {
    for (const amountMinor of [0, 1, 99, 100, 79900, 123456789]) {
      const money = { amountMinor, currency: 'EUR' }
      expect(moneyOf(decimalOf(money, currencies), 'EUR', currencies)).toEqual(money)
    }
  })
})
