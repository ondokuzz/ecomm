import { describe, expect, it } from 'vitest'
import type { Category } from './category'
import { currenciesOf } from './money'
import {
  type Product,
  type ProductForm,
  blankVariant,
  formOf,
  matchesSearch,
  productFieldErrors,
  productRequest,
  stockChanges,
} from './product'

const currencies = currenciesOf([
  { code: 'EUR', minorDigits: 2 },
  { code: 'JPY', minorDigits: 0 },
])

const phones: Category = {
  slug: 'phones',
  name: 'Phones',
  productCount: 7,
  attributes: [
    { name: 'brand', type: 'TEXT', values: [], required: true, variantAxis: false },
    { name: 'color', type: 'TEXT', values: [], required: true, variantAxis: true },
    { name: 'storage', type: 'ENUM', values: ['128 GB', '256 GB'], required: true, variantAxis: true },
    { name: 'screen', type: 'TEXT', values: [], required: false, variantAxis: false },
  ],
}

const pixel: Product = {
  sku: 'PHN-PIXEL-9',
  name: 'Google Pixel 9',
  description: 'Google’s phone, with Gemini built in.',
  category: 'phones',
  attributes: { brand: 'Google', screen: '6.3 in' },
  images: ['/images/front.svg', '/images/back.svg'],
  priceFrom: { amountMinor: 79900, currency: 'EUR' },
  variants: [
    {
      id: 'PHN-PIXEL-9',
      axisValues: { color: 'Obsidian', storage: '128 GB' },
      price: { amountMinor: 79900, currency: 'EUR' },
      images: [],
    },
    {
      id: 'PHN-PIXEL-9-PORCELAIN-256',
      axisValues: { color: 'Porcelain', storage: '256 GB' },
      price: { amountMinor: 89900, currency: 'EUR' },
      images: ['/images/porcelain.svg', '/images/porcelain-back.svg'],
    },
  ],
}

const stock = { 'PHN-PIXEL-9': 12, 'PHN-PIXEL-9-PORCELAIN-256': undefined }

describe('formOf', () => {
  it('lays a Product out for editing: Prices as decimals, its images one per line and a Variant’s on one, saved Variants with their On-hand counts', () => {
    expect(formOf(pixel, phones, stock, currencies)).toEqual({
      sku: 'PHN-PIXEL-9',
      name: 'Google Pixel 9',
      description: 'Google’s phone, with Gemini built in.',
      category: 'phones',
      attributes: { brand: 'Google', screen: '6.3 in' },
      images: '/images/front.svg\n/images/back.svg',
      currency: 'EUR',
      variants: [
        {
          id: 'PHN-PIXEL-9',
          saved: true,
          axisValues: { color: 'Obsidian', storage: '128 GB' },
          price: '799.00',
          images: '',
          onHand: '12',
        },
        {
          id: 'PHN-PIXEL-9-PORCELAIN-256',
          saved: true,
          axisValues: { color: 'Porcelain', storage: '256 GB' },
          price: '899.00',
          images: '/images/porcelain.svg, /images/porcelain-back.svg',
          // Inventory doesn't stock it yet.
          onHand: '',
        },
      ],
    })
  })

  it('starts a new Product in EUR with one blank Variant for the Category’s axes', () => {
    expect(formOf(undefined, phones, {}, currencies)).toEqual({
      sku: '',
      name: '',
      description: '',
      category: 'phones',
      attributes: {},
      images: '',
      currency: 'EUR',
      variants: [blankVariant(phones)],
    })
    expect(blankVariant(phones)).toEqual({
      id: '',
      saved: false,
      axisValues: { color: '', storage: '' },
      price: '',
      images: '',
      onHand: '0',
    })
  })

  it('starts with no Category until one is chosen', () => {
    expect(formOf(undefined, undefined, {}, currencies).category).toBe('')
    expect(blankVariant(undefined).axisValues).toEqual({})
  })
})

describe('productRequest', () => {
  it('sends the form back as Catalog takes it', () => {
    expect(productRequest(formOf(pixel, phones, stock, currencies), phones, currencies)).toEqual({
      request: {
        sku: 'PHN-PIXEL-9',
        name: 'Google Pixel 9',
        description: 'Google’s phone, with Gemini built in.',
        category: 'phones',
        attributes: { brand: 'Google', screen: '6.3 in' },
        images: ['/images/front.svg', '/images/back.svg'],
        variants: [
          {
            id: 'PHN-PIXEL-9',
            axisValues: { color: 'Obsidian', storage: '128 GB' },
            price: { amountMinor: 79900, currency: 'EUR' },
            images: [],
          },
          {
            id: 'PHN-PIXEL-9-PORCELAIN-256',
            axisValues: { color: 'Porcelain', storage: '256 GB' },
            price: { amountMinor: 89900, currency: 'EUR' },
            images: ['/images/porcelain.svg', '/images/porcelain-back.svg'],
          },
        ],
      },
      errors: {},
    })
  })

  it('trims what was typed, leaves out blank attributes, and splits images on new lines and commas', () => {
    const form: ProductForm = {
      ...formOf(undefined, phones, {}, currencies),
      sku: ' PHN-NEW ',
      name: ' New phone ',
      attributes: { brand: ' Acme ', screen: '  ' },
      images: ' /a.svg \n\n/b.svg, /c.svg ',
      currency: ' eur ',
      variants: [
        { ...blankVariant(phones), id: ' PHN-NEW ', axisValues: { color: ' Red ', storage: '128 GB' }, price: ' 10 ' },
      ],
    }
    expect(productRequest(form, phones, currencies)).toEqual({
      request: {
        sku: 'PHN-NEW',
        name: 'New phone',
        category: 'phones',
        attributes: { brand: 'Acme' },
        images: ['/a.svg', '/b.svg', '/c.svg'],
        variants: [
          {
            id: 'PHN-NEW',
            axisValues: { color: 'Red', storage: '128 GB' },
            price: { amountMinor: 1000, currency: 'EUR' },
            images: [],
          },
        ],
      },
      errors: {},
    })
  })

  it('trims a description, and sends none when it is blank', () => {
    const form = formOf(pixel, phones, stock, currencies)
    expect(productRequest({ ...form, description: '  Fast.\n' }, phones, currencies).request?.description).toBe('Fast.')
    expect(productRequest({ ...form, description: '   ' }, phones, currencies).request).not.toHaveProperty(
      'description',
    )
  })

  it('edits a Product that has no description as a blank one', () => {
    expect(formOf({ ...pixel, description: null }, phones, stock, currencies).description).toBe('')
  })

  it('sends only what the Category defines, so attributes it no longer has drop out', () => {
    const form = formOf(pixel, phones, stock, currencies)
    form.attributes = { ...form.attributes, weight: '198 g' }
    form.variants[0]!.axisValues = { ...form.variants[0]!.axisValues, finish: 'Matte' }
    const { request } = productRequest(form, phones, currencies)
    expect(request?.attributes).toEqual({ brand: 'Google', screen: '6.3 in' })
    expect(request?.variants[0]!.axisValues).toEqual({ color: 'Obsidian', storage: '128 GB' })
  })

  it('leaves out a blank axis value, for Catalog to name', () => {
    const form = formOf(pixel, phones, stock, currencies)
    form.variants[1]!.axisValues = { color: 'Porcelain', storage: '' }
    expect(productRequest(form, phones, currencies).request?.variants[1]!.axisValues).toEqual({ color: 'Porcelain' })
  })

  it('names each Price and on-hand count it can’t read, by the field Catalog would use, and sends nothing', () => {
    const form = formOf(pixel, phones, stock, currencies)
    form.variants[0]!.price = '799,00'
    form.variants[1]!.price = ''
    form.variants[1]!.onHand = '-1'
    expect(productRequest(form, phones, currencies)).toEqual({
      errors: {
        'variants[0].price': 'must be a Price such as 799.00',
        'variants[1].price': 'must be a Price such as 799.00',
        'variants[1].onHand': 'must be a whole number, 0 or more',
      },
    })
  })

  it('names a currency Catalog doesn’t price in', () => {
    const form = formOf(pixel, phones, stock, currencies)
    for (const currency of ['Euro', 'ABC', 'XXX']) {
      form.currency = currency
      expect(productRequest(form, phones, currencies).errors, currency).toEqual({
        currency: 'must be a currency Catalog prices in, such as EUR',
      })
    }
  })

  it('takes a blank on-hand count as leaving Stock alone, and refuses one that isn’t a whole number', () => {
    const form = formOf(pixel, phones, stock, currencies)
    form.variants[0]!.onHand = ''
    expect(productRequest(form, phones, currencies).errors).toEqual({})
    for (const onHand of ['1.5', 'ten', '1e3']) {
      form.variants[0]!.onHand = onHand
      expect(productRequest(form, phones, currencies).errors, onHand).toEqual({
        'variants[0].onHand': 'must be a whole number, 0 or more',
      })
    }
  })
})

describe('productFieldErrors', () => {
  it('names a Variant’s price currency by the editor’s one currency field', () => {
    expect(
      productFieldErrors({
        'variants[1].price.currency': 'must be an ISO 4217 currency with a minor unit',
        'variants[0].price': 'is required',
        'attributes.screen': 'is required',
      }),
    ).toEqual({
      currency: 'must be an ISO 4217 currency with a minor unit',
      'variants[0].price': 'is required',
      'attributes.screen': 'is required',
    })
  })
})

describe('stockChanges', () => {
  it('sets the On-hand count of each Variant whose count changed, or that Inventory doesn’t stock yet', () => {
    const form = formOf(pixel, phones, stock, currencies)
    form.variants[0]!.onHand = '15'
    form.variants[1]!.onHand = '3'
    form.variants.push({ ...blankVariant(phones), id: ' PHN-PIXEL-9-NEW ', onHand: '0' })
    expect(stockChanges(form, stock)).toEqual([
      { index: 0, variantId: 'PHN-PIXEL-9', onHand: 15 },
      { index: 1, variantId: 'PHN-PIXEL-9-PORCELAIN-256', onHand: 3 },
      { index: 2, variantId: 'PHN-PIXEL-9-NEW', onHand: 0 },
    ])
  })

  it('leaves alone a count that didn’t change, or was left blank', () => {
    const form = formOf(pixel, phones, stock, currencies)
    form.variants[0]!.onHand = ' 12 '
    expect(stockChanges(form, stock)).toEqual([])
  })
})

describe('matchesSearch', () => {
  it('finds a Product by part of its name or SKU, whatever the case', () => {
    expect(matchesSearch(pixel, 'pixel')).toBe(true)
    expect(matchesSearch(pixel, ' phn-pix ')).toBe(true)
    expect(matchesSearch(pixel, 'iphone')).toBe(false)
  })

  it('finds every Product for an empty search', () => {
    expect(matchesSearch(pixel, '  ')).toBe(true)
  })
})
