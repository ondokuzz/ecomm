/**
 * A test card the mock payment gateway answers in a set way. Its `token` is the payment method paying
 * a Checkout Session sends; there is no card number to type.
 */
export interface TestCard {
  token: string
  label: string
  /** What paying with it does. */
  outcome: string
  /** The last four digits the drawn card shows. */
  last4: string
}

/** One card per outcome of the mock gateway, the approving one first. */
export const testCards: readonly TestCard[] = [
  { token: 'tok_approve', label: 'Approve', outcome: 'The payment goes through.', last4: '4242' },
  { token: 'tok_decline', label: 'Decline', outcome: 'The bank declines the card.', last4: '0002' },
  {
    token: 'tok_insufficient_funds',
    label: 'Insufficient funds',
    outcome: 'Declined: not enough funds.',
    last4: '9995',
  },
  {
    token: 'tok_gateway_error',
    label: 'Gateway error',
    outcome: 'The payment provider fails to answer.',
    last4: '0119',
  },
]

export const defaultTestCard = testCards[0]

/**
 * Why paying failed in a way the Customer can act on by paying again: the card was declined (402,
 * with the gateway's reason), or the payment didn't go through (502). The session still holds their
 * items either way.
 */
export type PaymentFailure =
  | { kind: 'declined'; reason: string | undefined; title: string; message: string }
  | { kind: 'gatewayError'; title: string; message: string }

const declineMessages: Record<string, string> = {
  card_declined: 'Your bank declined the payment. Try another card.',
  insufficient_funds: "The card doesn't have enough funds. Try another card.",
  unknown_payment_method: "That card isn't one the payment provider knows. Try another card.",
}

/** Reads a failed payment's status and problem detail; undefined for any other failure. */
export function paymentFailure(status: number, problem: Record<string, unknown>): PaymentFailure | undefined {
  if (status === 402) {
    const reason = typeof problem.declineReason === 'string' ? problem.declineReason : undefined
    return {
      kind: 'declined',
      reason,
      title: 'Your card was declined',
      message: reason && Object.hasOwn(declineMessages, reason) ? declineMessages[reason] : 'Try another card.',
    }
  }
  if (status === 502) {
    return {
      kind: 'gatewayError',
      title: "The payment didn't go through",
      message: 'Something went wrong on the way to the payment provider. Your items are still held, so please try again.',
    }
  }
  return undefined
}
