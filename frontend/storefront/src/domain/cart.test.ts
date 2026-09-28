import { describe, expect, it } from 'vitest'
import { itemCount, itemCountLabel, priceCart, quantityOf } from './cart'
import type { Product } from './catalog'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

function product(sku: string, price = eur(1000)): Product {
  return { sku, name: `Product ${sku}`, category: 'phones', attributes: {}, price, images: [], variants: [{ id: sku, price }] }
}

describe('priceCart', () => {
  it('prices each line from its Variant and totals them', () => {
    const cart = { items: [{ variantId: 'A', quantity: 2 }, { variantId: 'B', quantity: 1 }] }
    const a = product('A', eur(1000))
    const b = product('B', eur(250))
    const priced = priceCart(cart, { A: a, B: b })

    expect(priced.lines).toEqual([
      { variantId: 'A', quantity: 2, product: a, name: 'Product A', unitPrice: eur(1000), lineTotal: eur(2000) },
      { variantId: 'B', quantity: 1, product: b, name: 'Product B', unitPrice: eur(250), lineTotal: eur(250) },
    ])
    expect(priced.total).toEqual(eur(2250))
  })

  it('uses the Variant price, not the Product price', () => {
    const p = { ...product('A', eur(1000)), variants: [{ id: 'A', price: eur(900) }] }
    expect(priceCart({ items: [{ variantId: 'A', quantity: 1 }] }, { A: p }).total).toEqual(eur(900))
  })

  it('has no total while a line is not priced yet', () => {
    const cart = { items: [{ variantId: 'A', quantity: 1 }, { variantId: 'GONE', quantity: 1 }] }
    const priced = priceCart(cart, { A: product('A') })

    expect(priced.lines[1]).toEqual({ variantId: 'GONE', quantity: 1 })
    expect(priced.total).toBeUndefined()
  })

  it('has no total across currencies', () => {
    const cart = { items: [{ variantId: 'A', quantity: 1 }, { variantId: 'B', quantity: 1 }] }
    const priced = priceCart(cart, { A: product('A'), B: product('B', { amountMinor: 1000, currency: 'USD' }) })

    expect(priced.total).toBeUndefined()
  })

  it('has no total for an empty Cart', () => {
    expect(priceCart({ items: [] }, {}).total).toBeUndefined()
  })
})

describe('itemCount', () => {
  it('adds up the quantities', () => {
    expect(itemCount({ items: [{ variantId: 'A', quantity: 2 }, { variantId: 'B', quantity: 3 }] })).toBe(5)
  })
})

describe('itemCountLabel', () => {
  it('counts the units, not the lines', () => {
    expect(itemCountLabel({ items: [{ variantId: 'A', quantity: 2 }, { variantId: 'B', quantity: 3 }] })).toBe('5 items')
  })

  it('is singular for one unit', () => {
    expect(itemCountLabel({ items: [{ variantId: 'A', quantity: 1 }] })).toBe('1 item')
  })

  it('says so when there is nothing', () => {
    expect(itemCountLabel({ items: [] })).toBe('No items')
  })
})

describe('quantityOf', () => {
  const cart = { items: [{ variantId: 'A', quantity: 2 }] }

  it('is the quantity of a Variant in the Cart', () => {
    expect(quantityOf(cart, 'A')).toBe(2)
  })

  it('is zero for a Variant not in the Cart', () => {
    expect(quantityOf(cart, 'B')).toBe(0)
  })
})
