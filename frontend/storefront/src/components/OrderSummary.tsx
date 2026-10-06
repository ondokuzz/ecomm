import { type ReactNode, useId } from 'react'
import { type Cart, type PricedCart, itemCountLabel } from '../domain/cart'
import { Card } from './ui/Card'
import { useFormatMoney } from '../api/currencies'

/**
 * What the Cart comes to: its item count, subtotal and total, with the page's next step as
 * `children`. Beside the lines on wide screens, where it stays in view; below them on phones.
 */
export function OrderSummary({ cart, priced, children }: { cart: Cart; priced: PricedCart; children?: ReactNode }) {
  const formatMoney = useFormatMoney()
  const headingId = useId()
  // Checkout adds no tax or shipping yet, so the total is the subtotal.
  const total = priced.total ? formatMoney(priced.total) : '—'
  return (
    <Card className="order-summary" aria-labelledby={headingId} role="region">
      <h2 id={headingId}>Order summary</h2>
      <dl>
        <div>
          <dt>Items</dt>
          <dd>{itemCountLabel(cart)}</dd>
        </div>
        <div>
          <dt>Subtotal</dt>
          <dd>{total}</dd>
        </div>
        <div className="order-summary-total">
          <dt>Total</dt>
          <dd>{total}</dd>
        </div>
      </dl>
      {children}
    </Card>
  )
}
