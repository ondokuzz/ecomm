import { describe, expect, it } from 'vitest'
import {
  type AttributeDefinition,
  type Category,
  type Product,
  type Variant,
  cardPrice,
  categoryChips,
  chosenVariant,
  productCountLabel,
  productImage,
  specs,
  variantAxes,
  variantName,
} from './catalog'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

const variant = (id: string, color: string, storage: string, amountMinor = 79900, images: string[] = []): Variant => ({
  id,
  axisValues: { color, storage },
  price: eur(amountMinor),
  images,
})

const product = (images: string[], variants: Variant[] = [variant('PHN-PIXEL-9', 'Obsidian', '128 GB')]): Product => ({
  sku: 'PHN-PIXEL-9',
  name: 'Google Pixel 9',
  category: 'phones',
  attributes: {},
  priceFrom: eur(79900),
  images,
  variants,
})

describe('productImage', () => {
  it("is the Product's first image", () => {
    expect(productImage(product(['/images/a/front.svg', '/images/a/back.svg']))).toBe('/images/a/front.svg')
  })

  it('is undefined when the Product has no images', () => {
    expect(productImage(product([]))).toBeUndefined()
  })

  it("is the Variant's own first image when it has one", () => {
    const porcelain = variant('P', 'Porcelain', '128 GB', 79900, ['/images/a/porcelain.svg'])
    expect(productImage(product(['/images/a/front.svg']), porcelain)).toBe('/images/a/porcelain.svg')
  })

  it("falls back to the Product's image for a Variant without its own", () => {
    expect(productImage(product(['/images/a/front.svg']), variant('O', 'Obsidian', '128 GB'))).toBe('/images/a/front.svg')
  })
})

describe('cardPrice', () => {
  it('is the one Price, not "from", when every Variant costs the same', () => {
    const p = product([], [variant('A', 'Obsidian', '128 GB', 79900), variant('B', 'Porcelain', '128 GB', 79900)])
    expect(cardPrice(p)).toEqual({ price: eur(79900), from: false })
  })

  it('is "from" the lowest Price when Variant Prices differ', () => {
    const p = {
      ...product([], [variant('A', 'Obsidian', '256 GB', 89900), variant('B', 'Obsidian', '128 GB', 79900)]),
      priceFrom: eur(79900),
    }
    expect(cardPrice(p)).toEqual({ price: eur(79900), from: true })
  })
})

describe('chosenVariant', () => {
  const p = product([], [variant('A', 'Obsidian', '128 GB'), variant('B', 'Obsidian', '256 GB')])

  it('is the Variant the URL names', () => {
    expect(chosenVariant(p, 'B').id).toBe('B')
  })

  it("is the Product's first Variant when the URL names none, or one it doesn't have", () => {
    expect(chosenVariant(p, null).id).toBe('A')
    expect(chosenVariant(p, 'NOPE').id).toBe('A')
  })
})

describe('variantAxes', () => {
  const definitions: AttributeDefinition[] = [
    { name: 'brand', type: 'TEXT', values: [], required: true, variantAxis: false },
    { name: 'color', type: 'TEXT', values: [], required: true, variantAxis: true },
    { name: 'storage', type: 'ENUM', values: ['128 GB', '256 GB', '512 GB'], required: true, variantAxis: true },
  ]
  // Porcelain comes only in 128 GB.
  const obsidian128 = variant('O-128', 'Obsidian', '128 GB')
  const obsidian256 = variant('O-256', 'Obsidian', '256 GB', 89900)
  const porcelain128 = variant('P-128', 'Porcelain', '128 GB')
  const pixel = product([], [obsidian128, obsidian256, porcelain128])
  const inStock = { 'O-128': 5, 'O-256': 5, 'P-128': 5 }

  const summary = (axes: ReturnType<typeof variantAxes>) =>
    axes.map((axis) => ({
      name: axis.name,
      options: axis.options.map(
        (o) => `${o.value}${o.selected ? '*' : ''}${o.variant ? `->${o.variant.id}` : ' (none)'}${o.outOfStock ? ' (out)' : ''}`,
      ),
    }))

  it("offers each axis's values in the Category's order, the chosen Variant's selected", () => {
    expect(summary(variantAxes(pixel, obsidian128, definitions, inStock))).toEqual([
      { name: 'color', options: ['Obsidian*->O-128', 'Porcelain->P-128'] },
      { name: 'storage', options: ['128 GB*->O-128', '256 GB->O-256'] },
    ])
  })

  it("disables a value whose combination with the chosen Variant's other axis values doesn't exist", () => {
    expect(summary(variantAxes(pixel, obsidian256, definitions, inStock))).toEqual([
      { name: 'color', options: ['Obsidian*->O-256', 'Porcelain (none)'] },
      { name: 'storage', options: ['128 GB->O-128', '256 GB*->O-256'] },
    ])
  })

  it('marks a value that leads to a sold-out Variant', () => {
    expect(summary(variantAxes(pixel, obsidian128, definitions, { ...inStock, 'O-256': 0 }))[1].options).toEqual([
      '128 GB*->O-128',
      '256 GB->O-256 (out)',
    ])
  })

  it("doesn't mark a Variant whose Stock is unknown", () => {
    expect(summary(variantAxes(pixel, obsidian128, definitions, {}))[1].options).toEqual(['128 GB*->O-128', '256 GB->O-256'])
  })

  it("takes the axes from the Variants while the Category's definitions are unknown", () => {
    expect(summary(variantAxes(pixel, porcelain128, undefined, inStock))).toEqual([
      { name: 'color', options: ['Obsidian->O-128', 'Porcelain*->P-128'] },
      { name: 'storage', options: ['128 GB*->P-128', '256 GB (none)'] },
    ])
  })

  it('has no axes for a Product with one Variant', () => {
    expect(variantAxes(product([]), obsidian128, definitions, inStock)).toEqual([])
  })
})

describe('variantName', () => {
  it('names the Product and its axis values', () => {
    expect(
      variantName({
        ...variant('O-256', 'Obsidian', '256 GB'),
        product: { sku: 'PHN-PIXEL-9', name: 'Google Pixel 9', images: [] },
      }),
    ).toBe('Google Pixel 9 · Obsidian · 256 GB')
  })

  it('is just the Product name when the Variant has no axis values', () => {
    expect(
      variantName({
        id: 'AUD-JBL-FLIP-6',
        axisValues: {},
        price: eur(12900),
        images: [],
        product: { sku: 'AUD-JBL-FLIP-6', name: 'JBL Flip 6', images: [] },
      }),
    ).toBe('JBL Flip 6')
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
