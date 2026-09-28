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
