import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** A Product in Catalog's seed, as far as the Storefront's images need it. */
export interface SeedProduct {
  sku: string
  category: string
  attributes: Record<string, string>
  images: string[]
  variants: SeedVariant[]
}

/** A seed Variant; `images` only when it has its own. */
export interface SeedVariant {
  id: string
  axisValues: Record<string, string>
  images?: string[]
}

const seedUrl = new URL('../../../services/catalog/src/main/resources/seed/products.json', import.meta.url)
const publicUrl = new URL('../public/', import.meta.url)

export function seedProducts(): SeedProduct[] {
  return JSON.parse(readFileSync(seedUrl, 'utf8'))
}

/** Where in public/ the file lives that Vite and nginx serve at an image path Catalog returns. */
export function publicFile(imagePath: string): string {
  return fileURLToPath(new URL(`.${imagePath}`, publicUrl))
}
