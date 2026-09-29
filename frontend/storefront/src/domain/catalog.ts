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

/** An attribute a Category's Products carry. A Variant axis tells a Product's Variants apart. */
export interface AttributeDefinition {
  name: string
  type: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM'
  /** The allowed values of an ENUM; empty otherwise. */
  values: string[]
  required: boolean
  variantAxis: boolean
}

export interface Category {
  slug: string
  name: string
  productCount: number
  attributes: AttributeDefinition[]
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

/** One row of a Product's specifications. */
export interface Spec {
  name: string
  value: string
}

/**
 * A Product's specs, in the order and with the names of its Category's definitions, BOOLEANs as Yes or No.
 * Attributes no definition names (say, from before the definitions changed) follow in their own order, so
 * none are hidden. Without definitions, the attributes keep their own order.
 */
export function specs(attributes: Record<string, string>, definitions: AttributeDefinition[] | undefined): Spec[] {
  const defined = (definitions ?? []).filter((d) => attributes[d.name] !== undefined)
  const names = new Set(defined.map((d) => d.name))
  return [
    ...defined.map((d) => ({ name: d.name, value: display(attributes[d.name], d) })),
    ...Object.entries(attributes)
      .filter(([name]) => !names.has(name))
      .map(([name, value]) => ({ name, value })),
  ]
}

function display(value: string, definition: AttributeDefinition): string {
  if (definition.type !== 'BOOLEAN') return value
  return value === 'true' ? 'Yes' : value === 'false' ? 'No' : value
}

/** "All", counting every Product, then each category, with the chosen one (none means "All") selected. */
export function categoryChips(categories: Category[], chosen: string | undefined): CategoryChip[] {
  const total = categories.reduce((sum, c) => sum + c.productCount, 0)
  return [
    { category: undefined, label: 'All', productCount: total, selected: chosen === undefined },
    ...categories.map((c) => ({
      category: c.slug,
      label: c.name,
      productCount: c.productCount,
      selected: c.slug === chosen,
    })),
  ]
}

/** How many Products a list holds, in words. */
export function productCountLabel(count: number): string {
  if (count === 0) return 'No products'
  return count === 1 ? '1 product' : `${count} products`
}
