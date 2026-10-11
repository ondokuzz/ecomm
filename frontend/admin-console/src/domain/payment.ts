import type { Money } from './money'
import { formatTime } from './order'

/**
 * A Payment's status, worked out from its Payment transactions: its authorization awaits the
 * gateway, the amount is authorized, the gateway declined it, or a Void released it.
 */
export type PaymentStatus = 'PENDING' | 'AUTHORIZED' | 'DECLINED' | 'VOIDED'

export const paymentStatuses: readonly PaymentStatus[] = ['PENDING', 'AUTHORIZED', 'DECLINED', 'VOIDED']

/**
 * One interaction with the payment gateway, recorded for good. `declineReason` and `gatewayEventId`
 * are null unless the gateway declined it or a webhook recorded it; a backfilled one was
 * reconstructed from a Payment recorded before the ledger, and its `at` is approximate.
 */
export interface PaymentTransaction {
  kind: 'AUTHORIZATION' | 'VOID'
  amount: Money
  outcome: 'APPROVED' | 'DECLINED' | 'PENDING'
  gatewayReference: string | null
  declineReason: string | null
  gatewayEventId: string | null
  at: string
  backfilled: boolean
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

export function paymentStatusLabel(status: PaymentStatus): string {
  return labelOf(status)
}

/** One Payment transaction as the Order page lists it. */
export interface TransactionRow {
  kind: string
  outcome: string
  amount: Money
  /** The gateway's reference, or a dash when it gave none. */
  reference: string
  declineReason?: string
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
    at: transaction.at,
    time: formatTime(transaction.at, locale),
    ...(transaction.backfilled ? { note: 'Backfilled from before the ledger: the time is approximate' } : {}),
  }))
}
