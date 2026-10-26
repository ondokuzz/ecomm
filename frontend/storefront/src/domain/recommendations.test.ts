import { describe, expect, it } from 'vitest'
import { alsoBoughtHeading, type Recommendation, recommendationLabel } from './recommendations'

const together: Recommendation = { sku: 'PHN-PIXEL-9', orders: 3, reason: 'BOUGHT_TOGETHER' }
const popular: Recommendation = {
  sku: 'AUD-SONY-WH1000XM5',
  orders: 7,
  reason: 'POPULAR_IN_CATEGORY',
  category: 'Audio',
}

describe('recommendationLabel', () => {
  it('labels one popular in its Category', () => {
    expect(recommendationLabel(popular)).toBe('Popular in Audio')
  })

  it('leaves one bought together unlabelled', () => {
    expect(recommendationLabel(together)).toBeUndefined()
  })

  it('reads one without a reason, from before Sprint 6, as bought together', () => {
    expect(recommendationLabel({ sku: 'X', orders: 1 })).toBeUndefined()
  })
})

describe('alsoBoughtHeading', () => {
  it('is Customers also bought while at least one was bought together', () => {
    expect(alsoBoughtHeading([together, popular])).toBe('Customers also bought')
  })

  it('is Popular in the Category when none was', () => {
    expect(alsoBoughtHeading([popular, { ...popular, sku: 'AUD-AIRPODS-PRO-2' }])).toBe('Popular in Audio')
  })

  it('is Customers also bought for Recommendations without a reason', () => {
    expect(alsoBoughtHeading([{ sku: 'X', orders: 1 }])).toBe('Customers also bought')
  })
})
