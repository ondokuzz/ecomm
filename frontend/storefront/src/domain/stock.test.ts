import { describe, expect, it } from 'vitest'
import { stockLevel } from './stock'

describe('stockLevel', () => {
  it('is in stock with plenty left', () => {
    expect(stockLevel(25)).toEqual({ level: 'in', label: 'In stock' })
  })

  it('is low stock with 5 or fewer left, saying how many', () => {
    expect(stockLevel(5)).toEqual({ level: 'low', label: 'Only 5 left' })
    expect(stockLevel(1)).toEqual({ level: 'low', label: 'Only 1 left' })
  })

  it('is in stock with 6 left', () => {
    expect(stockLevel(6).level).toBe('in')
  })

  it('is out of stock with none left', () => {
    expect(stockLevel(0)).toEqual({ level: 'out', label: 'Out of stock' })
  })

  it('is unknown when Inventory has no Stock record', () => {
    expect(stockLevel(undefined)).toEqual({ level: 'unknown', label: 'Stock unknown' })
  })
})
