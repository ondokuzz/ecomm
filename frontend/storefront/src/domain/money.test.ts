import { describe, expect, it } from 'vitest'
import { formatMoney, sumMoney, timesMoney } from './money'

// Intl separates currency and amount with a no-break space; compare with plain ones.
const format = (...args: Parameters<typeof formatMoney>) => formatMoney(...args).replace(/\s/g, ' ')

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

  it('follows the locale', () => {
    expect(format({ amountMinor: 123456, currency: 'EUR' }, 'de-DE')).toBe('1.234,56 €')
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
