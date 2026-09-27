import type { Product } from './catalog'
import { type Money, sumMoney, timesMoney } from './money'

export interface CartItem {
  variantId: string
  quantity: number
}

/** A Customer's Cart as the Cart service holds it: Variant IDs and quantities, no prices. */
export interface Cart {
  items: CartItem[]
}

/** A Cart line with a copy of Catalog's current Price. Unpriced while its Product is loading or gone. */
export interface CartLine extends CartItem {
  name?: string
  unitPrice?: Money
  lineTotal?: Money
}

export interface PricedCart {
  lines: CartLine[]
  /** Undefined unless every line is priced, in one currency. */
  total?: Money
}

/**
 * Prices a Cart for display from the Products its Variants belong to. Checkout prices it again
 * from Catalog, so this is only what the Customer can expect to pay.
 */
export function priceCart(cart: Cart, productsByVariantId: Record<string, Product | undefined>): PricedCart {
  const lines = cart.items.map((item): CartLine => {
    const product = productsByVariantId[item.variantId]
    const variant = product?.variants.find((v) => v.id === item.variantId)
    if (!product || !variant) return { ...item }
    return {
      ...item,
      name: product.name,
      unitPrice: variant.price,
      lineTotal: timesMoney(variant.price, item.quantity),
    }
  })
  const lineTotals = lines.flatMap((line) => (line.lineTotal ? [line.lineTotal] : []))
  const total = lineTotals.length === lines.length ? sumMoney(lineTotals) : undefined
  return { lines, total }
}

export function itemCount(cart: Cart): number {
  return cart.items.reduce((count, item) => count + item.quantity, 0)
}

export function quantityOf(cart: Cart, variantId: string): number {
  return cart.items.find((item) => item.variantId === variantId)?.quantity ?? 0
}
