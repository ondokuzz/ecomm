import { describe, expect, it } from 'vitest'
import { type Product, productImage } from './catalog'

const product = (images: string[]): Product => ({
  sku: 'PHN-PIXEL-9',
  name: 'Google Pixel 9',
  category: 'phones',
  attributes: {},
  price: { amountMinor: 79900, currency: 'EUR' },
  images,
  variants: [],
})

describe('productImage', () => {
  it("is the Product's first image", () => {
    expect(productImage(product(['/images/a/front.svg', '/images/a/back.svg']))).toBe('/images/a/front.svg')
  })

  it('is undefined when the Product has no images', () => {
    expect(productImage(product([]))).toBeUndefined()
  })
})
