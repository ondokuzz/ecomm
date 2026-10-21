import { useId } from 'react'
import { Link, useParams } from 'react-router'
import { useFormatMoney } from '../api/currencies'
import { isNotFound } from '../api/failure'
import { useReplacementShipment, useReturnWarehouse, useRma } from '../api/rmas'
import { NotFound } from '../components/NotFound'
import { ShipmentPanel } from '../components/OrderShipment'
import { OrderSkeleton } from '../components/PageSkeletons'
import { RmaStatusBadge } from '../components/RmaStatusBadge'
import { RmaTimeline } from '../components/RmaTimeline'
import { ErrorMessage, ErrorState } from '../components/Status'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { Skeleton } from '../components/ui/Skeleton'
import { orderReference } from '../domain/order'
import { type Rma, rmaKindLabel, returnAddressLines } from '../domain/rma'

/**
 * One of the Customer's RMAs, followed: its timeline; once approved, where to send its units, the
 * RMA reference for the parcel and the Send-back deadline; its Refund amount; and for an exchange,
 * the Replacement Shipment. It is asked again every 10 s while its units are received or inspected.
 */
export function RmaPage() {
  const { id = '' } = useParams()
  const query = useRma(id)

  if (query.isPending) return <OrderSkeleton />
  // Returns & Warranty answers 404 for another Customer's RMA too, so it is never told apart.
  if (isNotFound(query.error)) {
    return (
      <NotFound title="Return not found" back={{ label: 'All my returns', to: '/returns' }}>
        We couldn't find that return or warranty claim among yours.
      </NotFound>
    )
  }
  if (query.error) {
    return (
      <ErrorState
        title="We couldn't load this return"
        error={query.error}
        retrying={query.isFetching}
        onRetry={() => query.refetch()}
      />
    )
  }
  const rma = query.data
  const replacing =
    rma.kind === 'WARRANTY_CLAIM' &&
    (rma.status === 'EXCHANGED' || (rma.status === 'INSPECTED' && rma.inspection?.outcome === 'FAULT_CONFIRMED'))

  return (
    <section>
      <Link to="/returns" className="back-link">
        <Icon name="arrowLeft" size={16} />
        All my returns
      </Link>
      <div className="page-heading order-heading">
        <h1>{rma.reference}</h1>
        <RmaStatusBadge status={rma.status} />
      </div>
      <div className="cart-layout">
        <div className="cart-main stack">
          <Card>
            <h2 className="card-title">Progress</h2>
            <RmaTimeline rma={rma} />
          </Card>
          {rma.status === 'APPROVED' && <SendBack rma={rma} />}
          <RefundCard rma={rma} />
          {replacing && <Replacement rmaId={rma.id} />}
        </div>
        <RmaFacts rma={rma} />
      </div>
    </section>
  )
}

/** Where to send an approved RMA's units, what to write on the parcel, and by when. */
function SendBack({ rma }: { rma: Rma }) {
  const warehouse = useReturnWarehouse(rma.warehouseId)
  const headingId = useId()
  return (
    <Card className="rma-send-back" role="region" aria-labelledby={headingId}>
      <h2 id={headingId} className="card-title">
        Send it back
      </h2>
      {warehouse.error ? (
        <ErrorMessage
          error={warehouse.error}
          title="We couldn't load the return address"
          retrying={warehouse.isFetching}
          onRetry={() => warehouse.refetch()}
        />
      ) : warehouse.data ? (
        <address aria-label="Return address">
          {returnAddressLines(warehouse.data).map((line) => (
            <span key={line}>{line}</span>
          ))}
        </address>
      ) : (
        rma.warehouseId && <Skeleton height="5rem" />
      )}
      <p className="rma-parcel">
        Write <strong>{rma.reference}</strong> on the parcel.
      </p>
      {rma.sendBackBy && (
        <p>
          Send it to arrive by{' '}
          <time dateTime={rma.sendBackBy}>
            {new Date(rma.sendBackBy).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
          </time>
          .{' '}
          <span className="muted">
            If it hasn't arrived by then, this {rmaKindLabel(rma.kind).toLowerCase()} expires.
          </span>
        </p>
      )}
    </Card>
  )
}

/**
 * What the RMA gives back: for a return, its Refund amount before it is refunded, noting that
 * Shipping isn't refunded; once refunded, the amount and "Refunded", as for a warranty claim
 * refunded when no replacement was in Stock.
 */
function RefundCard({ rma }: { rma: Rma }) {
  const formatMoney = useFormatMoney()
  const headingId = useId()
  if (rma.status !== 'REFUNDED' && rma.kind !== 'RETURN') return null
  const amount = rma.refundAmount && formatMoney(rma.refundAmount)
  return (
    <Card className="rma-refund" role="region" aria-labelledby={headingId}>
      <h2 id={headingId} className="card-title">
        Refund
      </h2>
      {rma.status === 'REFUNDED' ? (
        <p>
          <strong className="rma-refund-amount">{amount}</strong> refunded to your card. It shows on your order’s
          payment.
          {rma.kind === 'WARRANTY_CLAIM' && ' No replacement was in stock, so you were refunded instead.'}
        </p>
      ) : (
        <>
          <p>
            {amount ? (
              <>
                <strong className="rma-refund-amount">{amount}</strong> comes back to your card once we accept the{' '}
                {rma.quantity === 1 ? 'unit' : 'units'}.
              </>
            ) : (
              'We’re working out what you get back. Check again in a moment.'
            )}
          </p>
          <p className="muted">Shipping isn't refunded for a return.</p>
        </>
      )}
    </Card>
  )
}

/** The Replacement Shipment of an upheld warranty claim; nothing until the returns Saga has created it. */
function Replacement({ rmaId }: { rmaId: string }) {
  const shipment = useReplacementShipment(rmaId).data
  if (!shipment) return null
  return <ShipmentPanel shipment={shipment} title="Replacement shipment" />
}

/** The RMA in brief: its Order, Product, units, kind, reason, and when it was requested. */
function RmaFacts({ rma }: { rma: Rma }) {
  const headingId = useId()
  return (
    <Card className="order-summary" aria-labelledby={headingId} role="region">
      <h2 id={headingId}>Return summary</h2>
      <dl>
        <div>
          <dt>Kind</dt>
          <dd>{rmaKindLabel(rma.kind)}</dd>
        </div>
        <div>
          <dt>Product</dt>
          <dd>{rma.productName ?? rma.variantId}</dd>
        </div>
        <div>
          <dt>Units</dt>
          <dd>
            {rma.serialNumbers.length > 0 ? (
              <ul className="rma-serials" aria-label="Serial numbers">
                {rma.serialNumbers.map((serial) => (
                  <li key={serial}>
                    <code>{serial}</code>
                  </li>
                ))}
              </ul>
            ) : (
              rma.quantity
            )}
          </dd>
        </div>
        <div>
          <dt>Requested</dt>
          <dd>{new Date(rma.requestedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</dd>
        </div>
        <div>
          <dt>Order</dt>
          <dd>
            <Link to={`/orders/${encodeURIComponent(rma.orderId)}`}>#{orderReference({ id: rma.orderId })}</Link>
          </dd>
        </div>
      </dl>
      <p className="muted rma-reason">“{rma.reason}”</p>
    </Card>
  )
}
