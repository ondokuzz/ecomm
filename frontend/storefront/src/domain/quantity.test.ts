import { describe, expect, it } from 'vitest'
import { clampQuantity, parseQuantity } from './quantity'

describe('clampQuantity', () => {
  it('keeps a quantity inside the bounds', () => {
    expect(clampQuantity(3, { min: 1, max: 5 })).toBe(3)
    expect(clampQuantity(0, { min: 1, max: 5 })).toBe(1)
    expect(clampQuantity(9, { min: 1, max: 5 })).toBe(5)
  })

  it('has no upper bound without a max', () => {
    expect(clampQuantity(250, { min: 1 })).toBe(250)
  })

  it('drops fractions', () => {
    expect(clampQuantity(2.7, { min: 1 })).toBe(2)
  })

  it('never goes below min when max is smaller', () => {
    expect(clampQuantity(3, { min: 1, max: 0 })).toBe(1)
  })
})

describe('parseQuantity', () => {
  it('reads what the Customer typed, clamped', () => {
    expect(parseQuantity('4', { min: 1, max: 10 })).toBe(4)
    expect(parseQuantity('40', { min: 1, max: 10 })).toBe(10)
    expect(parseQuantity('-2', { min: 1 })).toBe(1)
  })

  it('answers undefined for text that is not a number', () => {
    expect(parseQuantity('', { min: 1 })).toBeUndefined()
    expect(parseQuantity('abc', { min: 1 })).toBeUndefined()
  })
})
