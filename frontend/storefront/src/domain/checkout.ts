import type { Cart, CartLine } from './cart'
import type { Money } from './money'
import { type Discount, type OrderSummaryRow, summaryRows } from './order'

/** One line of a Checkout Session, at the Price captured when it started. */
export interface CheckoutSessionLine {
  variantId: string
  quantity: number
  unitPrice: Money
  lineTotal: Money
}

/**
 * The Customer's Cart held for checkout: its lines at the Prices captured when it started, and its
 * Stock reserved, until `expiresAt`. Paying it buys it at those Prices, less every Discount it is
 * due: the running Campaigns', then the applied Coupon's, if any.
 */
export interface CheckoutSession {
  id: string
  lines: CheckoutSessionLine[]
  subtotal: Money
  /** Every Discount, in the order they apply; empty when none. */
  discounts: Discount[]
  tax: Money
  /** The subtotal, less every Discount, plus the tax. */
  total: Money
  /** An ISO 8601 instant. */
  expiresAt: string
}

/** How long a Checkout Session has left at `now` (epoch ms), as `m:ss`, rounded up to the second. */
export function sessionCountdown(expiresAt: string, now: number): { expired: boolean; label: string } {
  const secondsLeft = Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000))
  const minutes = Math.floor(secondsLeft / 60)
  const seconds = secondsLeft % 60
  return { expired: secondsLeft === 0, label: `${minutes}:${String(seconds).padStart(2, '0')}` }
}

/** What a Checkout Session comes to: subtotal, each Discount, tax (even when zero) and total. */
export function sessionSummaryRows(session: CheckoutSession): OrderSummaryRow[] {
  return summaryRows(session)
}

/** Whether a Checkout Session holds exactly this Cart; one changed since would buy the wrong things. */
export function sessionHoldsCart(session: CheckoutSession, cart: Cart): boolean {
  if (session.lines.length !== cart.items.length) return false
  return cart.items.every((item) =>
    session.lines.some((line) => line.variantId === item.variantId && line.quantity === item.quantity),
  )
}

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
      products: variantIds
        .filter((id): id is string => typeof id === 'string')
        .map((variantId) => ({
          variantId,
          name: lines.find((line) => line.variantId === variantId)?.name ?? variantId,
        })),
    }
  }
  return undefined
}
