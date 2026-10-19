import { useFormatMoney } from '../api/currencies'
import {
  type Payment,
  type PaymentStatus,
  paymentStatusLabel,
  paymentStatusNote,
  paymentTransactionsOf,
  refundedOf,
} from '../domain/payment'
import { Badge, type Tone } from './ui/Badge'

const paymentStatusTones: Record<PaymentStatus, Tone> = {
  PENDING: 'warning',
  AUTHORIZED: 'success',
  DECLINED: 'danger',
  VOIDED: 'neutral',
  CAPTURED: 'success',
  PARTIALLY_REFUNDED: 'neutral',
  REFUNDED: 'neutral',
}

/**
 * An Order's Payments, newest first: each one's status, what it means for the Customer's money, its
 * amount, what its Refunds gave back, and its Payment transactions, oldest first, such as the amount
 * held, then charged, then refunded.
 * Read only. An Order can have none, when the payment gateway never answered.
 */
export function OrderPayments({ payments }: { payments: readonly Payment[] }) {
  const formatMoney = useFormatMoney()
  if (payments.length === 0) {
    return <p className="muted">No payment was recorded for this order.</p>
  }
  return (
    <ul className="order-payments" aria-label="Payments">
      {payments.map((payment) => (
        <li key={payment.id} className="order-payment">
          <div className="order-payment-top">
            {/* The smoke test finds the Payment status by its `payment-*` class. */}
            <Badge tone={paymentStatusTones[payment.status]} dot className={`payment-${payment.status.toLowerCase()}`}>
              {paymentStatusLabel(payment.status)}
            </Badge>
            <strong className="order-payment-amount">{formatMoney(payment.amount)}</strong>
          </div>
          <p className="muted">{paymentStatusNote(payment)}</p>
          {payment.status === 'PARTIALLY_REFUNDED' && (
            <p className="order-payment-refunded">
              Refunded {formatMoney(refundedOf(payment))} of {formatMoney(payment.amount)}
            </p>
          )}
          {payment.transactions.length > 0 && (
            <ol className="order-payment-transactions" aria-label="Payment activity">
              {paymentTransactionsOf(payment).map((row, i) => (
                <li key={i}>
                  <span>{row.label}</span>
                  <span className="muted">{formatMoney(row.amount)}</span>
                  <time className="muted" dateTime={row.at}>
                    {new Date(row.at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
                  </time>
                </li>
              ))}
            </ol>
          )}
        </li>
      ))}
    </ul>
  )
}
