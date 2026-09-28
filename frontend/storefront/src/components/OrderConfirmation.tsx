import type { CSSProperties } from 'react'
import { type Order, orderItemCountLabel, orderReference } from '../domain/order'
import { formatMoney } from '../domain/money'
import { ButtonLink } from './ui/Button'
import { Icon } from './ui/Icon'

/** The celebration after checkout: a big check, a burst of confetti, the Order in brief, and where to go next. */
export function OrderConfirmation({ order }: { order: Order }) {
  return (
    <div className="confirmation">
      <Confetti />
      <span className="confirmation-check">
        <Icon name="check" size={44} strokeWidth={3} />
      </span>
      <h1>Thank you! Your order is confirmed.</h1>
      <p className="confirmation-summary">
        Order <strong>#{orderReference(order)}</strong> · {orderItemCountLabel(order)} ·{' '}
        <strong>{formatMoney(order.total)}</strong>
      </p>
      <div className="confirmation-actions">
        <ButtonLink variant="primary" size="lg" to="/">
          Continue shopping
        </ButtonLink>
        <ButtonLink variant="secondary" size="lg" to="/orders">
          View my orders
        </ButtonLink>
      </div>
    </div>
  )
}

const pieces = 28

/**
 * Paper bits that fall once and fade. Decorative, so hidden from screen readers; the stylesheet
 * hides it outright when the Customer prefers reduced motion.
 */
function Confetti() {
  return (
    <div className="confetti" aria-hidden="true">
      {Array.from({ length: pieces }, (_, i) => (
        <span
          key={i}
          // Spread evenly but not in step: prime strides scatter the positions, delays and spins.
          style={
            {
              '--x': `${(i * 37) % 100}%`,
              '--delay': `${(i * 53) % 600}ms`,
              '--spin': `${((i * 97) % 720) - 360}deg`,
              '--drift': `${((i * 29) % 80) - 40}px`,
            } as CSSProperties
          }
        />
      ))}
    </div>
  )
}
