import { describe, expect, it } from 'vitest'
import {
  type AttributeDefinition,
  type Product,
  type Variant,
  chosenVariant,
  productCardOf,
  productCountLabel,
  productImage,
  specs,
  variantAxes,
  variantName,
  warrantyLabel,
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
  warranty: null,
  serialTracking: 'NONE',
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
    expect(productImage(product(['/images/a/front.svg']), variant('O', 'Obsidian', '128 GB'))).toBe(
      '/images/a/front.svg',
    )
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
        (o) =>
          `${o.value}${o.selected ? '*' : ''}${o.variant ? `->${o.variant.id}` : ' (none)'}${o.outOfStock ? ' (out)' : ''}`,
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
    expect(summary(variantAxes(pixel, obsidian128, definitions, {}))[1].options).toEqual([
      '128 GB*->O-128',
      '256 GB->O-256',
    ])
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

describe('warrantyLabel', () => {
  it("names a warranty's length and its manufacturer", () => {
    expect(warrantyLabel({ months: 24, manufacturer: 'Samsung' })).toBe("24 months, manufacturer's warranty (Samsung)")
  })

  it('says one month in the singular', () => {
    expect(warrantyLabel({ months: 1, manufacturer: 'Acme' })).toBe("1 month, manufacturer's warranty (Acme)")
  })

  it('says when a Product has none', () => {
    expect(warrantyLabel(null)).toBe("No manufacturer's warranty")
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
  const definitions = [
    definition('brand'),
    definition('wireless', 'BOOLEAN'),
    definition('type'),
    definition('waterproof'),
  ]

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

describe('productCardOf', () => {
  it('shows a Catalog Product as a card: its brand, its first image, and its lowest Price', () => {
    const card = productCardOf({
      ...product(['pixel.png']),
      attributes: { brand: 'Google' },
      priceFrom: eur(79900),
      variants: [variant('A', 'Obsidian', '128 GB', 79900), variant('B', 'Obsidian', '256 GB', 89900)],
    })

    expect(card).toEqual({
      sku: 'PHN-PIXEL-9',
      name: 'Google Pixel 9',
      attributes: { brand: 'Google' },
      image: 'pixel.png',
      priceFrom: eur(79900),
      priceVaries: true,
    })
  })

  it("takes its first Variant's image when the Product has none, and doesn't say 'from' for one Price", () => {
    const card = productCardOf(
      product([], [variant('A', 'Obsidian', '128 GB', 79900, ['a.png']), variant('B', 'Obsidian', '256 GB', 79900)]),
    )

    expect(card.image).toBe('a.png')
    expect(card.priceVaries).toBe(false)
  })

  it('has no image when neither the Product nor its first Variant has one', () => {
    expect(productCardOf(product([])).image).toBeNull()
  })
})
