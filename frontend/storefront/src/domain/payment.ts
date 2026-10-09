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

/** One card per outcome of the mock gateway, the approving one first, then the ones the bank confirms later. */
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
  {
    token: 'tok_async_approve',
    label: 'Bank confirms, approves',
    outcome: 'Your bank confirms the payment a few seconds later, and it goes through.',
    last4: '3155',
  },
  {
    token: 'tok_async_decline',
    label: 'Bank confirms, declines',
    outcome: 'Your bank declines the payment a few seconds later.',
    last4: '3184',
  },
]

export const defaultTestCard = testCards[0]

/** How an attempt to pay a Checkout Session stands: the checkout Saga is still going, or how it ended. */
export type PaymentStatus = 'PROCESSING' | 'PAID' | 'DECLINED' | 'HOLD_EXPIRED' | 'FAILED'

/** The latest attempt to pay a Checkout Session, as Checkout reports it. */
export interface PaymentAttempt {
  status: PaymentStatus
  orderId: string | null
  declineReason: string | null
}

/**
 * Why paying failed, told so the Customer knows what to do: the card was declined (402, with the
 * gateway's reason) and another can be tried; the items stopped being held while they paid (410
 * with `holdExpired`), so nothing was charged and they start again; or the payment failed (502),
 * nothing was charged, and they can try again.
 */
export type PaymentFailure =
  | { kind: 'declined'; reason: string | undefined; title: string; message: string }
  | { kind: 'holdExpired'; title: string; message: string }
  | { kind: 'failed'; title: string; message: string }

const declineMessages: Record<string, string> = {
  card_declined: 'Your bank declined the payment. Try another card.',
  insufficient_funds: "The card doesn't have enough funds. Try another card.",
  unknown_payment_method: "That card isn't one the payment provider knows. Try another card.",
}

function declined(reason: string | undefined): PaymentFailure {
  return {
    kind: 'declined',
    reason,
    title: 'Your card was declined',
    message: reason && Object.hasOwn(declineMessages, reason) ? declineMessages[reason] : 'Try another card.',
  }
}

const holdExpired: PaymentFailure = {
  kind: 'holdExpired',
  title: 'Your hold ran out',
  message:
    'Your items stopped being held while you paid, so the card authorization was released and nothing was charged. Start again to check the prices and stock.',
}

const failed: PaymentFailure = {
  kind: 'failed',
  title: "The payment didn't go through",
  message: 'Something went wrong while taking the payment, and nothing was charged. Please try again.',
}

/** Reads a failed answer to paying, by its status and problem detail; undefined for any other failure. */
export function paymentFailure(status: number, problem: Record<string, unknown>): PaymentFailure | undefined {
  if (status === 402) return declined(typeof problem.declineReason === 'string' ? problem.declineReason : undefined)
  // A 410 without the reason is the session itself having expired, which isn't a payment failure.
  if (status === 410 && problem.reason === 'holdExpired') return holdExpired
  if (status === 502) return failed
  return undefined
}

/** Reads an attempt that ended without paying, as its answer to paying would have said; undefined otherwise. */
export function attemptFailure(attempt: PaymentAttempt): PaymentFailure | undefined {
  switch (attempt.status) {
    case 'DECLINED':
      return declined(attempt.declineReason ?? undefined)
    case 'HOLD_EXPIRED':
      return holdExpired
    case 'FAILED':
      return failed
    default:
      return undefined
  }
}

/** How long a Pay may wait for its answer before the page says the payment is being confirmed, in milliseconds. */
export const confirmingAfter = 2000

/**
 * Whether a Pay sent at `payingSince` and still unanswered at `now` (both epoch ms) is being confirmed:
 * a payment the bank confirms later keeps the answer waiting for seconds, which a plain one doesn't.
 */
export function isConfirmingPayment(payingSince: number | undefined, now: number): boolean {
  return payingSince !== undefined && now - payingSince >= confirmingAfter
}

/** How often the Storefront asks how a payment still being confirmed stands, in milliseconds. */
export const pollEvery = 1500

/** How long it keeps asking before it stops, in milliseconds. */
export const pollFor = 120_000

/** Where a payment still being confirmed stands, as the checkout page shows it. */
export type Confirmation =
  | { kind: 'confirming' }
  | { kind: 'paid'; orderId: string }
  | { kind: 'failed'; failure: PaymentFailure }
  | { kind: 'stillConfirming' }

/**
 * Where a payment the Customer made at `since` stands at `now` (both epoch ms), from the latest
 * attempt read and when it was read. An answer read before they paid is about an earlier attempt,
 * so it says nothing yet. After `pollFor` without an answer, the page stops asking.
 */
export function confirmation(
  latest: { attempt: PaymentAttempt; readAt: number } | undefined,
  since: number,
  now: number,
): Confirmation {
  const attempt = latest && latest.readAt >= since ? latest.attempt : undefined
  if (attempt?.status === 'PAID' && attempt.orderId) return { kind: 'paid', orderId: attempt.orderId }
  const failure = attempt && attemptFailure(attempt)
  if (failure) return { kind: 'failed', failure }
  return now - since < pollFor ? { kind: 'confirming' } : { kind: 'stillConfirming' }
}
