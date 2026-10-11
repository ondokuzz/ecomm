import { useFormatMoney } from '../api/currencies'
import { type Payment, type PaymentStatus, paymentStatusLabel, paymentStatusNote } from '../domain/payment'
import { Badge, type Tone } from './ui/Badge'

const paymentStatusTones: Record<PaymentStatus, Tone> = {
  PENDING: 'warning',
  AUTHORIZED: 'success',
  DECLINED: 'danger',
  VOIDED: 'neutral',
}

/**
 * An Order's Payments, newest first: each one's status, what it means for the Customer's money, and
 * its amount. Read only. An Order can have none, when the payment gateway never answered.
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
        </li>
      ))}
    </ul>
  )
}
