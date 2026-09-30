/**
 * Why a Coupon doesn't apply to the Checkout Session, as Promotions' `reason` says, with what to tell
 * the Customer. Applying it changed nothing.
 */
export interface CouponRejection {
  reason: string | undefined
  message: string
}

const messages: Record<string, string> = {
  unknown: "We don't know that code. Check it and try again.",
  inactive: "That code isn't available any more.",
  notYetValid: "That code isn't valid yet.",
  expired: 'That code has expired.',
  belowMinimum: "Your order doesn't reach that code's minimum spend yet.",
  currencyMismatch: "That code can't be used with this order's currency.",
}

/** Reads a rejected Coupon's status and problem detail (a 422 with a `reason`); undefined for any other failure. */
export function couponRejection(status: number, problem: Record<string, unknown>): CouponRejection | undefined {
  if (status !== 422) return undefined
  const reason = typeof problem.reason === 'string' ? problem.reason : undefined
  return {
    reason,
    message: reason && Object.hasOwn(messages, reason) ? messages[reason] : "That code can't be applied to this order.",
  }
}
