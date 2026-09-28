import { existsSync, statSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { publicFile, seedProducts } from './seed.ts'

const seed = seedProducts()

describe('seed Product images', () => {
  it('covers all 20 seed Products', () => {
    expect(seed).toHaveLength(20)
  })

  it.each(seed)('$sku has a small image at the path Catalog returns', ({ sku, images }) => {
    expect(images).toEqual([`/images/products/${sku.toLowerCase()}/front.svg`])
    const file = publicFile(images[0])
    expect(existsSync(file)).toBe(true)
    expect(statSync(file).size).toBeLessThan(50 * 1024)
  })
})
