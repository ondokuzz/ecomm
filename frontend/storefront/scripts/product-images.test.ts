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
    expectSmallImage(images[0])
  })

  const variantImages = seed.flatMap((product) =>
    product.variants.flatMap((variant) =>
      (variant.images ?? []).map((image) => ({ sku: product.sku, id: variant.id, image })),
    ),
  )

  it('includes Variants with images of their own', () => {
    expect(variantImages).not.toHaveLength(0)
  })

  it.each(variantImages)("Variant $id has a small image beside its Product's", ({ sku, image }) => {
    expect(image).toMatch(new RegExp(`^/images/products/${sku.toLowerCase()}/[a-z0-9-]+\\.svg$`))
    expectSmallImage(image)
  })

  function expectSmallImage(image: string) {
    const file = publicFile(image)
    expect(existsSync(file)).toBe(true)
    expect(statSync(file).size).toBeLessThan(50 * 1024)
  }
})
