import type { Money } from './money'
import { formatTime } from './order'

/**
 * A Payment's status, worked out from its Payment transactions: its authorization awaits the
 * gateway, the amount is authorized, the gateway declined it, a Void released it, a Capture
 * charged it, or Refunds gave back part or all of it.
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
 * One interaction with the payment gateway, recorded for good. `declineReason` and `gatewayEventId`
 * are null unless the gateway declined it or a webhook recorded it; a backfilled one was
 * reconstructed from a Payment recorded before the ledger, and its `at` is approximate; `reference`
 * is what a Refund was for, such as an RMA reference, and null for any other kind.
 */
export interface PaymentTransaction {
  kind: 'AUTHORIZATION' | 'VOID' | 'CAPTURE' | 'REFUND'
  amount: Money
  outcome: 'APPROVED' | 'DECLINED' | 'PENDING'
  gatewayReference: string | null
  declineReason: string | null
  gatewayEventId: string | null
  at: string
  backfilled: boolean
  reference: string | null
}

/** A Payment as Payment shows it to Staff, with its Payment transactions oldest first. */
export interface Payment {
  id: string
  orderId: string
  amount: Money
  status: PaymentStatus
  /** Null unless it was declined. */
  declineReason: string | null
  /** Its authorization's reference at the gateway. */
  gatewayReference: string | null
  transactions: PaymentTransaction[]
}

/** A gateway code as Staff read it: "Authorized", "Void", "Approved", … */
function labelOf(code: string): string {
  return code.charAt(0) + code.slice(1).toLowerCase()
}

/** A Payment status as Staff read it; a captured Payment is "Charged", as the Customer sees it. */
export function paymentStatusLabel(status: PaymentStatus): string {
  switch (status) {
    case 'CAPTURED':
      return 'Charged'
    case 'PARTIALLY_REFUNDED':
      return 'Partly refunded'
    default:
      return labelOf(status)
  }
}

/** What a Payment's Refunds gave back: the approved ones, in the Payment's Currency. */
export function refundedOf(payment: Pick<Payment, 'amount' | 'transactions'>): Money {
  const amountMinor = payment.transactions
    .filter((transaction) => transaction.kind === 'REFUND' && transaction.outcome === 'APPROVED')
    .reduce((sum, transaction) => sum + transaction.amount.amountMinor, 0)
  return { amountMinor, currency: payment.amount.currency }
}

/** One Payment transaction as the Order page lists it. */
export interface TransactionRow {
  kind: string
  outcome: string
  amount: Money
  /** The gateway's reference, or a dash when it gave none. */
  reference: string
  declineReason?: string
  /** What a Refund was for, such as an RMA reference. */
  refundFor?: string
  /** The instant, for `<time dateTime>`. */
  at: string
  /** When, in the Staff member's own time zone, to the second. */
  time: string
  /** Why the time is only approximate, for a backfilled transaction. */
  note?: string
}

/** A Payment's transactions for Staff, oldest first, as the gateway saw them happen. */
export function transactionRowsOf(payment: Pick<Payment, 'transactions'>, locale?: string): TransactionRow[] {
  return payment.transactions.map((transaction) => ({
    kind: labelOf(transaction.kind),
    outcome: labelOf(transaction.outcome),
    amount: transaction.amount,
    reference: transaction.gatewayReference ?? '—',
    ...(transaction.declineReason ? { declineReason: transaction.declineReason } : {}),
    ...(transaction.reference ? { refundFor: transaction.reference } : {}),
    at: transaction.at,
    time: formatTime(transaction.at, locale),
    ...(transaction.backfilled ? { note: 'Backfilled from before the ledger: the time is approximate' } : {}),
  }))
}
