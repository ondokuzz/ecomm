import { describe, expect, it } from 'vitest'
import type { CartLine } from './cart'
import { checkoutProblem } from './checkout'

const lines: CartLine[] = [
  { variantId: 'PIXEL-9', quantity: 1, name: 'Google Pixel 9' },
  { variantId: 'GONE', quantity: 1 },
]

describe('checkoutProblem', () => {
  it('names the out-of-stock Products', () => {
    expect(checkoutProblem({ outOfStock: ['PIXEL-9'] }, lines)).toEqual({
      reason: 'outOfStock',
      products: [{ variantId: 'PIXEL-9', name: 'Google Pixel 9' }],
    })
  })

  it('names the unknown Variants by ID when Catalog has no name for them', () => {
    expect(checkoutProblem({ unknownVariants: ['GONE'] }, lines)).toEqual({
      reason: 'unknownVariants',
      products: [{ variantId: 'GONE', name: 'GONE' }],
    })
  })

  it('names a Variant no longer in the Cart by its ID', () => {
    expect(checkoutProblem({ outOfStock: ['OTHER'] }, lines)?.products).toEqual([{ variantId: 'OTHER', name: 'OTHER' }])
  })

  it('skips anything in the list that is not a Variant ID', () => {
    expect(checkoutProblem({ outOfStock: ['PIXEL-9', 7, null] }, lines)?.products).toEqual([
      { variantId: 'PIXEL-9', name: 'Google Pixel 9' },
    ])
  })

  it('is undefined for any other problem', () => {
    expect(checkoutProblem({ detail: 'Your cart is empty' }, lines)).toBeUndefined()
    expect(checkoutProblem({ outOfStock: 'PIXEL-9' }, lines)).toBeUndefined()
  })
})
