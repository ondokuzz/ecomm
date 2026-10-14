import { useId } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useVariants } from '../api/catalog'
import { useFormatMoney } from '../api/currencies'
import { isNotFound } from '../api/failure'
import { useOrder } from '../api/orders'
import { usePayments } from '../api/payments'
import { CheckoutSteps } from '../components/CheckoutSteps'
import { OrderConfirmation } from '../components/OrderConfirmation'
import { OrderPayments } from '../components/OrderPayments'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { OrderTimeline } from '../components/OrderTimeline'
import { OrderSkeleton } from '../components/PageSkeletons'
import { ProductLines } from '../components/ProductLines'
import { NotFound } from '../components/NotFound'
import { ErrorMessage, ErrorState, LookupError } from '../components/Status'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { Skeleton } from '../components/ui/Skeleton'
import { type Order, nameOrderLines, orderItemCountLabel, orderReference, orderSummaryRows } from '../domain/order'
import { cancellationReason } from '../domain/payment'
import { addressLines, shippingOptionName } from '../domain/shipping'

/**
 * An Order, its Order Status, its Payments, where it ships and how, and its lines, read from Order
 * Management and Payment; an Order placed before Shipping shows no Shipping card;
 * after checkout, the confirmation first. A cancelled Order says why, when its Payment does.
 */
export function OrderPage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const query = useOrder(id)
  const payments = usePayments(id)
  const formatMoney = useFormatMoney()
  const { variants, failure: variantsFailure } = useVariants(query.data?.lines.map((line) => line.variantId) ?? [])

  if (query.isPending) return <OrderSkeleton />
  // Order Management answers 404 for another Customer's Order too, so it is never told apart.
  if (isNotFound(query.error)) {
    return (
      <NotFound title="Order not found" back={{ label: 'All my orders', to: '/orders' }}>
        We couldn't find that order among yours.
      </NotFound>
    )
  }
  if (query.error) {
    return (
      <ErrorState
        title="We couldn't load this order"
        error={query.error}
        retrying={query.isFetching}
        onRetry={() => query.refetch()}
      />
    )
  }
  const order = query.data
  const placed = params.has('placed')
  const Heading = placed ? 'h2' : 'h1'
  const cancelledBecause = payments.data && cancellationReason(order.status, payments.data)

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
      {cancelledBecause && (
        <p className="alert alert-info">
          <Icon name="alert" size={18} />
          <span>Cancelled: {cancelledBecause}</span>
        </p>
      )}
      <div className="cart-layout">
        <div className="cart-main stack">
          <Card>
            <h2 className="card-title">Order progress</h2>
            <OrderTimeline order={order} />
          </Card>
          <Card>
            <h2 className="card-title">Payment</h2>
            {payments.error ? (
              <ErrorMessage
                error={payments.error}
                title="We couldn't load the payment"
                retrying={payments.isFetching}
                onRetry={() => payments.refetch()}
              />
            ) : payments.data ? (
              <OrderPayments payments={payments.data} />
            ) : (
              <Skeleton height="2.5rem" />
            )}
          </Card>
          {order.shippingAddress && order.shippingOption && (
            <Card className="order-shipping">
              <h2 className="card-title">Shipping</h2>
              <address aria-label="Shipping address">
                {addressLines(order.shippingAddress).map((line) => (
                  <span key={line}>{line}</span>
                ))}
              </address>
              <p className="order-shipping-option">
                {shippingOptionName(order.shippingOption.id)} by {order.shippingOption.carrier},{' '}
                {formatMoney(order.shippingOption.cost)}
              </p>
            </Card>
          )}
          <Card>
            <h2 className="card-title">Products</h2>
            <LookupError failure={variantsFailure} title="We couldn't load the products' names and pictures" />
            <ProductLines lines={nameOrderLines(order, variants)} label="Order lines" compact />
          </Card>
        </div>
        <OrderFacts order={order} />
      </div>
    </section>
  )
}

/** The Order in brief, beside its lines: when it was placed, how many items, what it comes to, and its full ID. */
function OrderFacts({ order }: { order: Order }) {
  const formatMoney = useFormatMoney()
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
