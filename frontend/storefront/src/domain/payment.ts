import type { Money } from './money'
import type { OrderStatus } from './order'

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

/**
 * One card per outcome of the mock gateway, the approving one first, then the ones the bank confirms
 * later, then the one whose capture is refused, then the one whose refunds are refused.
 */
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
  {
    token: 'tok_capture_decline',
    label: 'Charge refused',
    outcome: 'The payment goes through, but the charge is refused when the order ships.',
    last4: '0069',
  },
  {
    token: 'tok_refund_decline',
    label: 'Refund refused',
    outcome: 'The payment goes through, but any refund of it is refused.',
    last4: '0077',
  },
]

export const defaultTestCard = testCards[0]

/** How an attempt to pay a Checkout Session stands: the checkout Saga is still going, or how it ended. */
export type PaymentAttemptStatus = 'PROCESSING' | 'PAID' | 'DECLINED' | 'HOLD_EXPIRED' | 'FAILED'

/** The latest attempt to pay a Checkout Session, as Checkout reports it. */
export interface PaymentAttempt {
  status: PaymentAttemptStatus
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
 * Calls `onConfirming` when a Pay sent at `payingSince` (epoch ms) and still unanswered becomes one
 * being confirmed, `confirmingAfter` after it was sent: a payment the bank confirms later keeps the
 * answer waiting for seconds, which a plain one doesn't. Returns what cancels it, for when the answer
 * comes first.
 */
export function whenConfirmingPayment(payingSince: number, onConfirming: () => void): () => void {
  const timer = setTimeout(onConfirming, payingSince + confirmingAfter - Date.now())
  return () => clearTimeout(timer)
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

/**
 * A Payment's status, worked out from its Payment transactions: its authorization awaits the bank,
 * the amount is held, the gateway declined it, the hold was released by a Void, the amount was
 * charged by a Capture, or Refunds gave back part or all of it.
 */
export type PaymentStatus =
  'PENDING' | 'AUTHORIZED' | 'DECLINED' | 'VOIDED' | 'CAPTURED' | 'PARTIALLY_REFUNDED' | 'REFUNDED'

export const paymentStatuses: readonly PaymentStatus[] = [
  'PENDING',
  'AUTHORIZED',
  'DECLINED',
  'VOIDED',
  'CAPTURED',
  'PARTIALLY_REFUNDED',
  'REFUNDED',
]

/**
 * One interaction with the payment gateway on a Payment, as the Customer reads it; `reference` is
 * what a Refund was for, such as an RMA reference, and null for any other kind.
 */
export interface PaymentTransaction {
  kind: 'AUTHORIZATION' | 'VOID' | 'CAPTURE' | 'REFUND'
  outcome: 'APPROVED' | 'DECLINED' | 'PENDING'
  amount: Money
  declineReason: string | null
  at: string
  reference: string | null
}

/**
 * An Order's Payment as the Customer reads it from Payment, with its Payment transactions oldest
 * first; `declineReason` is null unless it was declined.
 */
export interface Payment {
  id: string
  orderId: string
  amount: Money
  status: PaymentStatus
  declineReason: string | null
  transactions: PaymentTransaction[]
}

const paymentStatusLabels: Record<PaymentStatus, string> = {
  PENDING: 'Being confirmed',
  AUTHORIZED: 'Authorized',
  DECLINED: 'Declined',
  VOIDED: 'Released',
  CAPTURED: 'Charged',
  PARTIALLY_REFUNDED: 'Partly refunded',
  REFUNDED: 'Refunded',
}

/** A Payment status as the Customer reads it. */
export function paymentStatusLabel(status: PaymentStatus): string {
  return paymentStatusLabels[status]
}

const declineReasons: Record<string, string> = {
  card_declined: 'Your bank declined the card.',
  insufficient_funds: "The card didn't have enough funds.",
  unknown_payment_method: "The payment provider didn't know the card.",
}

/** What a Payment's status means for the Customer's money; for a declined one, why it was declined. */
export function paymentStatusNote({ status, declineReason }: Pick<Payment, 'status' | 'declineReason'>): string {
  switch (status) {
    case 'PENDING':
      return 'Your bank is still confirming this payment.'
    case 'AUTHORIZED':
      return 'The amount is held on your card.'
    case 'VOIDED':
      return 'The hold on your card was released. Nothing was charged.'
    case 'CAPTURED':
      return 'The amount was charged to your card.'
    case 'PARTIALLY_REFUNDED':
      return 'Part of the amount was refunded to your card.'
    case 'REFUNDED':
      return 'The whole amount was refunded to your card.'
    case 'DECLINED':
      if (!declineReason) return 'The payment was declined.'
      return Object.hasOwn(declineReasons, declineReason)
        ? declineReasons[declineReason]
        : `The payment was declined (${declineReason}).`
  }
}

/** One Payment transaction as the Customer's Order page lists it: what it did to their money, and when. */
export interface PaymentTransactionRow {
  label: string
  amount: Money
  /** The instant, for `<time dateTime>`. */
  at: string
}

function transactionLabel({
  kind,
  outcome,
  reference,
}: Pick<PaymentTransaction, 'kind' | 'outcome' | 'reference'>): string {
  switch (kind) {
    case 'AUTHORIZATION':
      return outcome === 'APPROVED' ? 'Amount held' : outcome === 'DECLINED' ? 'Declined' : 'Sent to your bank'
    case 'CAPTURE':
      return outcome === 'APPROVED' ? 'Charged' : 'Charge refused'
    case 'VOID':
      return 'Hold released'
    case 'REFUND': {
      const label = outcome === 'APPROVED' ? 'Refunded' : 'Refund refused'
      return reference ? `${label} for ${reference}` : label
    }
  }
}

/** What a Payment's Refunds gave back: the approved ones, in the Payment's Currency. */
export function refundedOf(payment: {
  amount: Money
  transactions: readonly Pick<PaymentTransaction, 'kind' | 'outcome' | 'amount'>[]
}): Money {
  const amountMinor = payment.transactions
    .filter((transaction) => transaction.kind === 'REFUND' && transaction.outcome === 'APPROVED')
    .reduce((sum, transaction) => sum + transaction.amount.amountMinor, 0)
  return { amountMinor, currency: payment.amount.currency }
}

/** A Payment's transactions for the Customer, oldest first, a Refund with the RMA it was for. */
export function paymentTransactionsOf(payment: {
  transactions: readonly Pick<PaymentTransaction, 'kind' | 'outcome' | 'amount' | 'at' | 'reference'>[]
}): PaymentTransactionRow[] {
  return payment.transactions.map((transaction) => ({
    label: transactionLabel(transaction),
    amount: transaction.amount,
    at: transaction.at,
  }))
}

/**
 * Why a cancelled Order was cancelled, by its latest Payment (Payment lists them newest first): the
 * payment was declined, or the card authorization was released. Undefined for an Order that isn't
 * cancelled, or whose Payment doesn't say, such as one the gateway never answered for.
 */
export function cancellationReason(status: OrderStatus, payments: readonly Payment[]): string | undefined {
  if (status !== 'CANCELLED') return undefined
  switch (payments[0]?.status) {
    case 'DECLINED':
      return 'Payment declined'
    case 'VOIDED':
      return 'Card authorization released'
    default:
      return undefined
  }
}
