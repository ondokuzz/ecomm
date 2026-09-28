import type { CartLine } from './cart'

/** Why Checkout refused a Cart, with the Products it names: out of Stock, or unknown to Catalog. */
export interface CheckoutProblem {
  reason: 'outOfStock' | 'unknownVariants'
  products: { variantId: string; name: string }[]
}

/**
 * Reads the Variants a checkout problem detail names, and names each by its Product from the Cart
 * lines, or by its Variant ID when Catalog has no name for it. Undefined for any other problem.
 */
export function checkoutProblem(problem: Record<string, unknown>, lines: CartLine[]): CheckoutProblem | undefined {
  for (const reason of ['outOfStock', 'unknownVariants'] as const) {
    const variantIds = problem[reason]
    if (!Array.isArray(variantIds)) continue
    return {
      reason,
      products: variantIds.filter((id): id is string => typeof id === 'string').map((variantId) => ({
        variantId,
        name: lines.find((line) => line.variantId === variantId)?.name ?? variantId,
      })),
    }
  }
  return undefined
}
