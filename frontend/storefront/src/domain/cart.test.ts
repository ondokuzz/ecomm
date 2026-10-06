import { describe, expect, it } from 'vitest'
import { itemCount, itemCountLabel, priceCart, quantityOf } from './cart'
import type { VariantDetail } from './catalog'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

function variant(id: string, price = eur(1000), axisValues: Record<string, string> = {}): VariantDetail {
  return { id, axisValues, price, images: [], product: { sku: id, name: `Product ${id}`, images: [] } }
}

describe('priceCart', () => {
  it('prices each line from its Variant and totals them', () => {
    const cart = {
      items: [
        { variantId: 'A', quantity: 2 },
        { variantId: 'B', quantity: 1 },
      ],
    }
    const a = variant('A', eur(1000))
    const b = variant('B', eur(250))
    const priced = priceCart(cart, { A: a, B: b })

    expect(priced.lines).toEqual([
      { variantId: 'A', quantity: 2, variant: a, name: 'Product A', unitPrice: eur(1000), lineTotal: eur(2000) },
      { variantId: 'B', quantity: 1, variant: b, name: 'Product B', unitPrice: eur(250), lineTotal: eur(250) },
    ])
    expect(priced.total).toEqual(eur(2250))
  })

  it('names a line by its Product and its axis values', () => {
    const cart = { items: [{ variantId: 'A-256', quantity: 1 }] }
    const a = variant('A-256', eur(1000), { color: 'Obsidian', storage: '256 GB' })
    expect(priceCart(cart, { 'A-256': a }).lines[0].name).toBe('Product A-256 · Obsidian · 256 GB')
  })

  it('has no total while a line is not priced yet', () => {
    const cart = {
      items: [
        { variantId: 'A', quantity: 1 },
        { variantId: 'GONE', quantity: 1 },
      ],
    }
    const priced = priceCart(cart, { A: variant('A') })

    expect(priced.lines[1]).toEqual({ variantId: 'GONE', quantity: 1 })
    expect(priced.total).toBeUndefined()
  })

  it('has no total across currencies', () => {
    const cart = {
      items: [
        { variantId: 'A', quantity: 1 },
        { variantId: 'B', quantity: 1 },
      ],
    }
    const priced = priceCart(cart, { A: variant('A'), B: variant('B', { amountMinor: 1000, currency: 'USD' }) })

    expect(priced.total).toBeUndefined()
  })

  it('has no total for an empty Cart', () => {
    expect(priceCart({ items: [] }, {}).total).toBeUndefined()
  })
})

describe('itemCount', () => {
  it('adds up the quantities', () => {
    expect(
      itemCount({
        items: [
          { variantId: 'A', quantity: 2 },
          { variantId: 'B', quantity: 3 },
        ],
      }),
    ).toBe(5)
  })
})

describe('itemCountLabel', () => {
  it('counts the units, not the lines', () => {
    expect(
      itemCountLabel({
        items: [
          { variantId: 'A', quantity: 2 },
          { variantId: 'B', quantity: 3 },
        ],
      }),
    ).toBe('5 items')
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
