import { describe, expect, it } from 'vitest'
import { type Reference, askAgainAt, messageLimit, referencePath, remainingCharacters } from './help'

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

describe('askAgainAt', () => {
  it('is the minute the wait ends in, rounded up so the Customer is let through by then', () => {
    expect(askAgainAt(3600, new Date('2026-11-06T13:04:20Z'))).toEqual(new Date('2026-11-06T14:05:00Z'))
  })

  it('stays on a wait that ends on the minute', () => {
    expect(askAgainAt(60, new Date('2026-11-06T13:04:00Z'))).toEqual(new Date('2026-11-06T13:05:00Z'))
  })
})

describe('remainingCharacters', () => {
  it('counts down from the 2,000-character limit', () => {
    expect(messageLimit).toBe(2000)
    expect(remainingCharacters('')).toBe(2000)
    expect(remainingCharacters('Where is my Order?')).toBe(1982)
  })
})
