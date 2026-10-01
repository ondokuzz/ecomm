import { Link } from 'react-router'
import { useVariants } from '../api/catalog'
import { useOrders } from '../api/orders'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { OrdersSkeleton } from '../components/PageSkeletons'
import { ProductThumb } from '../components/ProductImage'
import { EmptyState, ErrorState, LookupError } from '../components/Status'
import { ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import type { VariantDetail } from '../domain/catalog'
import { formatMoney } from '../domain/money'
import { type Order, orderItemCountLabel, orderReference } from '../domain/order'

/** How many of an Order's lines its card shows a thumbnail for; the rest are counted. */
const maxThumbnails = 3

/** The Customer's Orders as cards, newest first. */
export function OrdersPage() {
  const orders = useOrders()
  const { variants, failure: variantsFailure } = useVariants(
    (orders.data ?? []).flatMap((order) => order.lines.slice(0, maxThumbnails).map((line) => line.variantId)),
  )

  if (orders.isPending) return <OrdersSkeleton />
  if (orders.error) {
    return (
      <ErrorState
        title="We couldn't load your orders"
        error={orders.error}
        retrying={orders.isFetching}
        onRetry={() => orders.refetch()}
      />
    )
  }

  return (
    <section>
      <h1>My orders</h1>
      <LookupError failure={variantsFailure} title="We couldn't load the products' pictures" />
      {orders.data.length === 0 ? (
        <EmptyState
          title="No orders yet"
          illustration={<NoOrdersIllustration />}
          action={
            <ButtonLink variant="primary" size="lg" to="/">
              Start shopping
            </ButtonLink>
          }
        >
          Your orders show up here once you check out.
        </EmptyState>
      ) : (
        <ul className="order-cards" aria-label="Orders">
          {orders.data.map((order) => (
            <OrderCard key={order.id} order={order} variants={variants} />
          ))}
        </ul>
      )}
    </section>
  )
}

function OrderCard({ order, variants }: { order: Order; variants: Record<string, VariantDetail | undefined> }) {
  const more = order.lines.length - maxThumbnails
  return (
    <li>
      <Link to={`/orders/${encodeURIComponent(order.id)}`} className="card-link">
        <Card interactive className="order-card">
          <div className="order-card-top">
            <div>
              <span className="order-card-reference">Order #{orderReference(order)}</span>
              <span className="muted order-card-date">
                {new Date(order.placedAt).toLocaleDateString(undefined, { dateStyle: 'medium' })}
              </span>
            </div>
            <OrderStatusBadge status={order.status} />
          </div>
          <ul className="order-card-thumbs">
            {order.lines.slice(0, maxThumbnails).map((line) => (
              <li key={line.variantId}>
                {/* The Variant ID names a Variant Catalog no longer has. */}
                <ProductThumb variant={variants[line.variantId]} label={line.variantId} />
              </li>
            ))}
            {more > 0 && (
              <li className="order-card-more" aria-label={`and ${more} more`}>
                +{more}
              </li>
            )}
          </ul>
          <div className="order-card-bottom">
            <span className="muted">{orderItemCountLabel(order)}</span>
            <span className="price">{formatMoney(order.total)}</span>
          </div>
        </Card>
      </Link>
    </li>
  )
}

/** An empty parcel box with its flaps open and a couple of sparkles. */
function NoOrdersIllustration() {
  return (
    <svg viewBox="0 0 200 140" width="200" height="140" aria-hidden="true" className="empty-illustration">
      <ellipse cx="100" cy="126" rx="70" ry="8" className="empty-shadow" />
      <path d="M52 56h96v62H52z" className="empty-box" />
      <path d="M52 56l-16-20h52l22 20 M148 56l16-20h-52l-12 20" className="empty-flap" />
      <path d="M100 56v62" className="empty-tape" />
      <circle cx="160" cy="18" r="5" className="empty-spark" />
      <circle cx="34" cy="74" r="4" className="empty-spark-2" />
      <path d="M100 6l3.5 7 7 3.5-7 3.5-3.5 7-3.5-7-7-3.5 7-3.5z" className="empty-spark" />
    </svg>
  )
}
