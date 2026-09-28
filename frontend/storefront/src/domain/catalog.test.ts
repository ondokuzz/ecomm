import { describe, expect, it } from 'vitest'
import { type Category, type Product, categoryChips, productCountLabel, productImage } from './catalog'

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

describe('categoryChips', () => {
  const categories: Category[] = [
    { category: 'phones', productCount: 7 },
    { category: 'laptops', productCount: 7 },
    { category: 'audio', productCount: 6 },
  ]

  it('leads with All, counting every Product, then each category in order', () => {
    expect(categoryChips(categories, undefined)).toEqual([
      { category: undefined, label: 'All', productCount: 20, selected: true },
      { category: 'phones', label: 'phones', productCount: 7, selected: false },
      { category: 'laptops', label: 'laptops', productCount: 7, selected: false },
      { category: 'audio', label: 'audio', productCount: 6, selected: false },
    ])
  })

  it('selects only the chosen category', () => {
    expect(categoryChips(categories, 'laptops').filter((c) => c.selected).map((c) => c.category)).toEqual(['laptops'])
  })

  it('selects nothing for a category Catalog does not have', () => {
    expect(categoryChips(categories, 'tablets').some((c) => c.selected)).toBe(false)
  })
})

describe('productCountLabel', () => {
  it.each([
    [0, 'No products'],
    [1, '1 product'],
    [7, '7 products'],
  ])('%i is "%s"', (count, label) => {
    expect(productCountLabel(count)).toBe(label)
  })
})
