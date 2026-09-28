import { describe, expect, it } from 'vitest'
import { orderStatusTones } from './orderStatusTones'

describe('orderStatusTones', () => {
  it('gives each Order Status a colour of its own', () => {
    const tones = Object.values(orderStatusTones)
    expect(new Set(tones).size).toBe(tones.length)
  })
})
