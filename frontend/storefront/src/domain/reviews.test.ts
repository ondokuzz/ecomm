import { describe, expect, it } from 'vitest'
import type { Variant } from './catalog'
import {
  type RatingSummary,
  emptyDraft,
  ratingLabel,
  refusalMessage,
  reviewCountLabel,
  reviewPayload,
  starFills,
  starShares,
  validateReview,
  variantBoughtLabel,
} from './reviews'

const summary = (perStar: [number, number, number, number, number], average: number | null): RatingSummary => ({
  sku: 'SKU',
  count: perStar.reduce((a, b) => a + b, 0),
  average,
  perStar: { '1': perStar[0], '2': perStar[1], '3': perStar[2], '4': perStar[3], '5': perStar[4] },
})

describe('starFills', () => {
  it('fills whole stars for a whole rating', () => {
    expect(starFills(4)).toEqual(['full', 'full', 'full', 'full', 'empty'])
    expect(starFills(5)).toEqual(['full', 'full', 'full', 'full', 'full'])
  })

  it('rounds an average to the nearest half star', () => {
    expect(starFills(4.8)).toEqual(['full', 'full', 'full', 'full', 'full'])
    expect(starFills(4.7)).toEqual(['full', 'full', 'full', 'full', 'half'])
    expect(starFills(4.3)).toEqual(['full', 'full', 'full', 'full', 'half'])
    expect(starFills(4.2)).toEqual(['full', 'full', 'full', 'full', 'empty'])
    expect(starFills(1.5)).toEqual(['full', 'half', 'empty', 'empty', 'empty'])
  })

  it('leaves every star empty without a rating', () => {
    expect(starFills(null)).toEqual(['empty', 'empty', 'empty', 'empty', 'empty'])
  })
})

describe('ratingLabel', () => {
  it('reads the average to one decimal out of five', () => {
    expect(ratingLabel(4.7)).toBe('Rated 4.7 out of 5')
    expect(ratingLabel(4)).toBe('Rated 4.0 out of 5')
  })

  it('says when nothing is rated yet', () => {
    expect(ratingLabel(null)).toBe('Not rated yet')
  })
})

describe('reviewCountLabel', () => {
  it('counts reviews in the singular and plural', () => {
    expect(reviewCountLabel(0)).toBe('No reviews yet')
    expect(reviewCountLabel(1)).toBe('1 review')
    expect(reviewCountLabel(1234)).toBe('1,234 reviews')
  })
})

describe('starShares', () => {
  it('lists five stars down to one with each count and its share of the whole', () => {
    expect(starShares(summary([0, 0, 1, 1, 2], 4))).toEqual([
      { star: 5, count: 2, percent: 50 },
      { star: 4, count: 1, percent: 25 },
      { star: 3, count: 1, percent: 25 },
      { star: 2, count: 0, percent: 0 },
      { star: 1, count: 0, percent: 0 },
    ])
  })

  it('rounds shares to whole percents', () => {
    expect(starShares(summary([1, 0, 0, 0, 2], 3.7)).map((s) => s.percent)).toEqual([67, 0, 0, 0, 33])
  })

  it('gives every star nothing without reviews', () => {
    expect(starShares(summary([0, 0, 0, 0, 0], null)).every((s) => s.percent === 0)).toBe(true)
  })
})

describe('validateReview', () => {
  const valid = { rating: 4, title: '', body: 'Battery lasts all day.' }

  it('accepts a rating and a body, the title being optional', () => {
    expect(validateReview(valid)).toEqual({})
    expect(validateReview({ ...valid, title: 'Solid' })).toEqual({})
  })

  it('needs a rating from 1 to 5', () => {
    expect(validateReview({ ...valid, rating: null }).rating).toBe('Choose a rating from 1 to 5 stars.')
    expect(validateReview({ ...valid, rating: 0 }).rating).toBeDefined()
    expect(validateReview({ ...valid, rating: 6 }).rating).toBeDefined()
    expect(validateReview({ ...valid, rating: 2.5 }).rating).toBeDefined()
  })

  it('allows a title of up to 120 characters, counting it trimmed', () => {
    expect(validateReview({ ...valid, title: 't'.repeat(120) }).title).toBeUndefined()
    expect(validateReview({ ...valid, title: ` ${'t'.repeat(120)} ` }).title).toBeUndefined()
    expect(validateReview({ ...valid, title: 't'.repeat(121) }).title).toBe('Keep the title to 120 characters (it has 121).')
  })

  it('needs a body of up to 2,000 characters', () => {
    expect(validateReview({ ...valid, body: '   ' }).body).toBe('Tell other Customers what you think.')
    expect(validateReview({ ...valid, body: 'b'.repeat(2000) }).body).toBeUndefined()
    expect(validateReview({ ...valid, body: 'b'.repeat(2001) }).body).toBe(
      'Keep the review to 2,000 characters (it has 2,001).',
    )
  })

  it('reports every problem at once', () => {
    expect(Object.keys(validateReview(emptyDraft))).toEqual(['rating', 'body'])
  })
})

describe('reviewPayload', () => {
  it('trims the text and sends a blank title as none', () => {
    expect(reviewPayload({ rating: 5, title: '  ', body: ' Great \n' })).toEqual({ rating: 5, title: null, body: 'Great' })
    expect(reviewPayload({ rating: 3, title: ' Fine ', body: 'Ok' })).toEqual({ rating: 3, title: 'Fine', body: 'Ok' })
  })
})

describe('refusalMessage', () => {
  it("says why a Customer can't review", () => {
    expect(refusalMessage('notPurchased')).toBe('Only Customers who have bought this product can review it.')
    expect(refusalMessage('alreadyReviewed')).toBe("You've already reviewed this product.")
    expect(refusalMessage('somethingNew')).toBe("You can't review this product.")
  })
})

describe('variantBoughtLabel', () => {
  const variants: Pick<Variant, 'id' | 'axisValues'>[] = [
    { id: 'P-BLK-128', axisValues: { color: 'Obsidian', storage: '128 GB' } },
    { id: 'P-SKU', axisValues: {} },
  ]

  it('names the Variant bought by its axis values', () => {
    expect(variantBoughtLabel(variants, 'P-BLK-128')).toBe('Bought: Obsidian · 128 GB')
  })

  it('says nothing for a Product with a single Variant, or one no longer listed', () => {
    expect(variantBoughtLabel(variants, 'P-SKU')).toBeUndefined()
    expect(variantBoughtLabel(variants, 'GONE')).toBeUndefined()
  })
})
