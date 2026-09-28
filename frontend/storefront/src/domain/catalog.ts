import type { Money } from './money'

/** A purchasable version of a Product. Until multi-Variant Products arrive, its ID is the Product's SKU. */
export interface Variant {
  id: string
  price: Money
}

export interface Product {
  sku: string
  name: string
  category: string
  attributes: Record<string, string>
  price: Money
  images: string[]
  variants: Variant[]
}

export interface Category {
  category: string
  productCount: number
}

/** The Variant a Customer buys from a Product's page; every Product has exactly one for now. */
export function defaultVariant(product: Product): Variant {
  return product.variants[0]
}

/** The image the Storefront shows a Product by: its first, if it has any. */
export function productImage(product: Product): string | undefined {
  return product.images[0]
}

/** A chip on the Product list that filters it by category; `category` is undefined for "All". */
export interface CategoryChip {
  category: string | undefined
  label: string
  productCount: number
  selected: boolean
}

/** "All", counting every Product, then each category, with the chosen one (none means "All") selected. */
export function categoryChips(categories: Category[], chosen: string | undefined): CategoryChip[] {
  const total = categories.reduce((sum, c) => sum + c.productCount, 0)
  return [
    { category: undefined, label: 'All', productCount: total, selected: chosen === undefined },
    ...categories.map((c) => ({
      category: c.category,
      label: c.category,
      productCount: c.productCount,
      selected: c.category === chosen,
    })),
  ]
}

/** How many Products a list holds, in words. */
export function productCountLabel(count: number): string {
  if (count === 0) return 'No products'
  return count === 1 ? '1 product' : `${count} products`
}
