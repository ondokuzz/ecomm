import { describe, expect, it } from 'vitest'
import {
  type AttributeDefinition,
  type Category,
  type Product,
  categoryChips,
  productCountLabel,
  productImage,
  specs,
} from './catalog'

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
  const category = (slug: string, name: string, productCount: number): Category => ({
    slug,
    name,
    productCount,
    attributes: [],
  })
  const categories: Category[] = [category('phones', 'Phones', 7), category('laptops', 'Laptops', 7), category('audio', 'Audio', 6)]

  it('leads with All, counting every Product, then each category in order by its name', () => {
    expect(categoryChips(categories, undefined)).toEqual([
      { category: undefined, label: 'All', productCount: 20, selected: true },
      { category: 'phones', label: 'Phones', productCount: 7, selected: false },
      { category: 'laptops', label: 'Laptops', productCount: 7, selected: false },
      { category: 'audio', label: 'Audio', productCount: 6, selected: false },
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

describe('specs', () => {
  const definition = (name: string, type: AttributeDefinition['type'] = 'TEXT'): AttributeDefinition => ({
    name,
    type,
    values: [],
    required: false,
    variantAxis: false,
  })
  const definitions = [definition('brand'), definition('wireless', 'BOOLEAN'), definition('type'), definition('waterproof')]

  it("lists a Product's attributes in the order its Category defines them", () => {
    expect(specs({ type: 'In-ear', brand: 'Sony', wireless: 'true' }, definitions)).toEqual([
      { name: 'brand', value: 'Sony' },
      { name: 'wireless', value: 'Yes' },
      { name: 'type', value: 'In-ear' },
    ])
  })

  it('shows a BOOLEAN as Yes or No', () => {
    expect(specs({ wireless: 'false' }, definitions)).toEqual([{ name: 'wireless', value: 'No' }])
  })

  it('skips definitions the Product has no value for', () => {
    expect(specs({ brand: 'Sony' }, definitions)).toEqual([{ name: 'brand', value: 'Sony' }])
  })

  it('lists attributes no definition names last, in their own order, so none are hidden', () => {
    expect(specs({ strap: 'silicone', brand: 'Garmin', colour: 'Black' }, definitions)).toEqual([
      { name: 'brand', value: 'Garmin' },
      { name: 'strap', value: 'silicone' },
      { name: 'colour', value: 'Black' },
    ])
  })

  it("lists the Product's own order while its Category's definitions are unknown", () => {
    expect(specs({ brand: 'Sony', wireless: 'true' }, undefined)).toEqual([
      { name: 'brand', value: 'Sony' },
      { name: 'wireless', value: 'true' },
    ])
  })
})
