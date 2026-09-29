import { useId } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useProductsByVariantId } from '../api/catalog'
import { useOrder } from '../api/orders'
import { CheckoutSteps } from '../components/CheckoutSteps'
import { OrderConfirmation } from '../components/OrderConfirmation'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { OrderTimeline } from '../components/OrderTimeline'
import { ProductLines } from '../components/ProductLines'
import { ErrorMessage, Loading } from '../components/Status'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { formatMoney } from '../domain/money'
import { type Order, nameOrderLines, orderItemCountLabel, orderReference, orderSummaryRows } from '../domain/order'

/** An Order, its Order Status and its lines, read from Order Management; after checkout, the confirmation first. */
export function OrderPage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const query = useOrder(id)
  const products = useProductsByVariantId(query.data?.lines.map((line) => line.variantId) ?? [])

  if (query.isPending) return <Loading />
  if (query.error) return <ErrorMessage error={query.error} />
  const order = query.data
  const placed = params.has('placed')
  const Heading = placed ? 'h2' : 'h1'

  return (
    <section>
      {placed && <CheckoutSteps current="Done" />}
      {placed && <OrderConfirmation order={order} />}
      {!placed && (
        <Link to="/orders" className="back-link">
          <Icon name="arrowLeft" size={16} />
          All my orders
        </Link>
      )}
      <div className="page-heading order-heading">
        <Heading>Order #{orderReference(order)}</Heading>
        <OrderStatusBadge status={order.status} />
      </div>
      <div className="cart-layout">
        <div className="cart-main stack">
          <Card>
            <h2 className="card-title">Order progress</h2>
            <OrderTimeline status={order.status} />
          </Card>
          <Card>
            <h2 className="card-title">Products</h2>
            <ProductLines lines={nameOrderLines(order, products)} label="Order lines" compact />
          </Card>
        </div>
        <OrderFacts order={order} />
      </div>
    </section>
  )
}

/** The Order in brief, beside its lines: when it was placed, how many items, what it comes to, and its full ID. */
function OrderFacts({ order }: { order: Order }) {
  const headingId = useId()
  return (
    <Card className="order-summary" aria-labelledby={headingId} role="region">
      <h2 id={headingId}>Order summary</h2>
      <dl>
        <div>
          <dt>Placed</dt>
          <dd>{new Date(order.placedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</dd>
        </div>
        <div>
          <dt>Items</dt>
          <dd>{orderItemCountLabel(order)}</dd>
        </div>
        {orderSummaryRows(order).map((row) => (
          <div key={row.label} className={row.isTotal ? 'order-summary-total' : undefined}>
            <dt>{row.label}</dt>
            <dd>{formatMoney(row.amount)}</dd>
          </div>
        ))}
      </dl>
      <p className="muted order-id">
        Order ID <code>{order.id}</code>
      </p>
    </Card>
  )
}
