import { describe, expect, it } from 'vitest'
import { lineAvailability, shortLines, stockLevel } from './stock'

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

describe('lineAvailability', () => {
  it('says nothing while Stock is unknown', () => {
    expect(lineAvailability(2, undefined)).toBeUndefined()
  })

  it('says nothing with plenty left', () => {
    expect(lineAvailability(2, 6)).toBeUndefined()
  })

  it('is out of stock with none left, and short', () => {
    expect(lineAvailability(1, 0)).toEqual({ level: 'out', label: 'Out of stock', short: true })
  })

  it('says how many are left when fewer than the Cart holds, and is short', () => {
    expect(lineAvailability(3, 2)).toEqual({ level: 'low', label: 'Only 2 left', short: true })
  })

  it('says how many are left when Stock runs low, without being short', () => {
    expect(lineAvailability(2, 5)).toEqual({ level: 'low', label: 'Only 5 left', short: false })
    expect(lineAvailability(5, 5)).toEqual({ level: 'low', label: 'Only 5 left', short: false })
  })
})

describe('shortLines', () => {
  it('lists the lines whose quantity is more than is available, in Cart order', () => {
    const lines = [
      { variantId: 'A', quantity: 3 },
      { variantId: 'B', quantity: 1 },
      { variantId: 'C', quantity: 1 },
      { variantId: 'D', quantity: 2 },
    ]
    expect(shortLines(lines, { A: 2, B: 9, C: 0 })).toEqual([lines[0], lines[2]])
  })
})
