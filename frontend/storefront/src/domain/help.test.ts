import { describe, expect, it } from 'vitest'
import { type Reference, messageLimit, referencePath, remainingCharacters } from './help'

describe('referencePath', () => {
  it('links an Order to its Order page', () => {
    const order: Reference = {
      kind: 'ORDER',
      id: '7f1c2a3b-0000-4000-8000-000000000001',
      reference: '#7F1C2A3B',
      label: 'Order #7F1C2A3B',
    }
    expect(referencePath(order)).toBe('/orders/7f1c2a3b-0000-4000-8000-000000000001')
  })

  it('links an RMA to its RMA page', () => {
    const rma: Reference = {
      kind: 'RMA',
      id: '3f2a9c1b-0000-4000-8000-000000000011',
      reference: 'RMA-3F2A9C1B',
      label: 'RMA-3F2A9C1B',
    }
    expect(referencePath(rma)).toBe('/returns/3f2a9c1b-0000-4000-8000-000000000011')
  })

  it('links a Product to its Product page, by its SKU', () => {
    const product: Reference = {
      kind: 'PRODUCT',
      id: 'PHN-PIXEL-9',
      reference: 'PHN-PIXEL-9',
      label: 'Google Pixel 9',
    }
    expect(referencePath(product)).toBe('/products/PHN-PIXEL-9')
  })
})

describe('remainingCharacters', () => {
  it('counts down from the 2,000-character limit', () => {
    expect(messageLimit).toBe(2000)
    expect(remainingCharacters('')).toBe(2000)
    expect(remainingCharacters('Where is my Order?')).toBe(1982)
  })
})
