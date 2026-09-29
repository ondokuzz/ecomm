import type { Money } from './money'

/**
 * A purchasable version of a Product, told apart from its siblings by its axis values (such as color
 * and storage). Its Variant ID is unique across the Catalog; a Product's first Variant's is its SKU.
 */
export interface Variant {
  id: string
  /** Its value for each of its Category's Variant axes, in the Category's order. */
  axisValues: Record<string, string>
  price: Money
  /** Its own images, which replace its Product's; usually none. */
  images: string[]
}

export interface Product {
  sku: string
  name: string
  category: string
  attributes: Record<string, string>
  /** The lowest of its Variants' Prices. */
  priceFrom: Money
  images: string[]
  variants: Variant[]
}

/** A Variant looked up by its ID, with enough of its Product to show it, as a Cart or an Order line needs. */
export interface VariantDetail extends Variant {
  product: { sku: string; name: string; images: string[] }
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

/** The Variant a Product's page offers until the Customer picks another: its first. */
export function defaultVariant(product: Product): Variant {
  return product.variants[0]
}

/** The Variant a Product page's `?variant=` names, or the default when it names none of the Product's. */
export function chosenVariant(product: Product, variantId: string | null): Variant {
  return product.variants.find((v) => v.id === variantId) ?? defaultVariant(product)
}

/** The image the Storefront shows a Product (or one of its Variants) by: the Variant's own first, else the Product's. */
export function productImage(product: Pick<Product, 'images'>, variant?: Pick<Variant, 'images'>): string | undefined {
  return variant?.images[0] ?? product.images[0]
}

/** What a Product card shows: the lowest Price, "from" it when the Variants' Prices differ. */
export function cardPrice(product: Product): { price: Money; from: boolean } {
  const amounts = new Set(product.variants.map((v) => v.price.amountMinor))
  return { price: product.priceFrom, from: amounts.size > 1 }
}

/** A Variant by its Product's name and its axis values, e.g. "Google Pixel 9 · Obsidian · 256 GB". */
export function variantName(variant: VariantDetail): string {
  return [variant.product.name, ...Object.values(variant.axisValues)].join(' · ')
}

/** One value of a Variant axis in the picker, and the Variant choosing it leads to. */
export interface VariantOption {
  value: string
  /** The Variant with this value and the chosen Variant's other axis values; undefined when there is none. */
  variant?: Variant
  selected: boolean
  /** Whether `variant` is known to be sold out. */
  outOfStock: boolean
}

export interface VariantAxis {
  name: string
  options: VariantOption[]
}

/**
 * The Variant picker for a Product with more than one Variant: each axis, in its Category's order, with the values
 * its Variants take (an ENUM's in its definition's order, any other's in the order the Variants use them). Picking a
 * value keeps the chosen Variant's other axis values, so a combination no Variant has leads nowhere. Until the
 * Category's definitions are known, the axes are the ones the Variants carry.
 */
export function variantAxes(
  product: Product,
  chosen: Variant,
  definitions: AttributeDefinition[] | undefined,
  stock: Record<string, number | undefined>,
): VariantAxis[] {
  if (product.variants.length < 2) return []
  const axes =
    definitions?.filter((d) => d.variantAxis) ??
    Object.keys(chosen.axisValues).map((name): AttributeDefinition => ({ name, type: 'TEXT', values: [], required: true, variantAxis: true }))
  return axes.map(({ name, type, values }) => {
    const used = [...new Set(product.variants.map((v) => v.axisValues[name]).filter((v) => v !== undefined))]
    const ordered = type === 'ENUM' ? [...values.filter((v) => used.includes(v)), ...used.filter((v) => !values.includes(v))] : used
    return {
      name,
      options: ordered.map((value): VariantOption => {
        const wanted = { ...chosen.axisValues, [name]: value }
        const variant = product.variants.find((v) => sameAxisValues(v.axisValues, wanted))
        const quantity = variant && stock[variant.id]
        return { value, variant, selected: chosen.axisValues[name] === value, outOfStock: quantity !== undefined && quantity <= 0 }
      }),
    }
  })
}

function sameAxisValues(a: Record<string, string>, b: Record<string, string>): boolean {
  const keys = Object.keys(a)
  return keys.length === Object.keys(b).length && keys.every((key) => a[key] === b[key])
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
