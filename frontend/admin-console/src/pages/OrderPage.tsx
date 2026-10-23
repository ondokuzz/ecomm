import { Link, useLocation, useParams } from 'react-router'
import { useCurrencies } from '../api/currencies'
import { isNotFound } from '../api/failure'
import { useOrderNotifications } from '../api/notifications'
import { useOrder } from '../api/orders'
import { usePayments } from '../api/payments'
import { useOrderRmas } from '../api/rmas'
import { useOrderShipment, useReplacements } from '../api/shipments'
import { NotFound } from '../components/NotFound'
import { Notifications } from '../components/Notifications'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { RmaStatusBadge } from '../components/RmaStatusBadge'
import { ShipmentStatusBadge } from '../components/ShipmentStatusBadge'
import { ErrorMessage } from '../components/Status'
import { Badge, type Tone } from '../components/ui/Badge'
import { Icon } from '../components/ui/Icon'
import { type Currencies, type Money, formatMoney } from '../domain/money'
import { type Order, discountLabel, formatTime, historyRowsOf, orderReference } from '../domain/order'
import {
  type Payment,
  type PaymentStatus,
  paymentStatusLabel,
  refundRowsOf,
  refundedOf,
  transactionRowsOf,
} from '../domain/payment'
import { type Rma, rmaKindLabel } from '../domain/rma'
import type { FromListState } from './OrderListPage'

/**
 * `/orders/:id`: one Order, whoever it belongs to, how it got to its Status, its Payments, its
 * Shipment, its RMAs with their replacement Shipments and Refunds, and the Notifications its Customer
 * was sent about it. Read only.
 */
export function OrderPage() {
  const { id } = useParams()
  const order = useOrder(id!)
  const currencies = useCurrencies()

  if (isNotFound(order.error)) return <NotFound what="Order" />
  const failed = order.error
    ? { error: order.error, title: "Couldn't load the Order", query: order }
    : currencies.error
      ? { error: currencies.error, title: "Couldn't load the currencies", query: currencies }
      : undefined
  if (failed) {
    return (
      <>
        <BackLink />
        <ErrorMessage
          error={failed.error}
          title={failed.title}
          retrying={failed.query.isFetching}
          onRetry={() => failed.query.refetch()}
        />
      </>
    )
  }
  if (!order.data || !currencies.data) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  return <OrderDetail order={order.data} currencies={currencies.data} />
}

/** Back to the list, as it was filtered and paged when the Order was opened from it. */
function BackLink() {
  const list = (useLocation().state as FromListState | null)?.list ?? ''
  return (
    <Link to={`/orders${list}`} className="back-link">
      <Icon name="arrowLeft" size={16} />
      Orders
    </Link>
  )
}

function OrderDetail({ order, currencies }: { order: Order; currencies: Currencies }) {
  const payments = usePayments(order.id)
  const rmas = useOrderRmas(order.id)
  const shipment = useOrderShipment(order.id)
  const notifications = useOrderNotifications(order.id)
  const money = (amount: Money) => formatMoney(amount, currencies)
  return (
    <>
      <BackLink />
      <div className="page-heading">
        <h1>
          Order <code>#{orderReference(order)}</code>
        </h1>
        <OrderStatusBadge status={order.status} />
      </div>

      <section className="panel" aria-labelledby="order-facts">
        <h2 id="order-facts" className="visually-hidden">
          About this Order
        </h2>
        <dl className="facts">
          <div>
            <dt>Order ID</dt>
            <dd>
              <code>{order.id}</code>
            </dd>
          </div>
          <div>
            <dt>Customer ID</dt>
            <dd>
              <code>{order.customerId}</code>
            </dd>
          </div>
          <div>
            <dt>Placed</dt>
            <dd>
              <time dateTime={order.placedAt}>{formatTime(order.placedAt)}</time>
            </dd>
          </div>
        </dl>
      </section>

      <section className="panel" aria-labelledby="order-lines">
        <h2 id="order-lines">Lines</h2>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Variant</th>
                <th scope="col" className="num">
                  Quantity
                </th>
                <th scope="col" className="num">
                  Unit price
                </th>
                <th scope="col" className="num">
                  Line total
                </th>
              </tr>
            </thead>
            <tbody>
              {order.lines.map((line) => (
                <tr key={line.variantId}>
                  <th scope="row">
                    <code>{line.variantId}</code>
                  </th>
                  <td className="num">{line.quantity}</td>
                  <td className="num">{money(line.unitPrice)}</td>
                  <td className="num">
                    {money({ ...line.unitPrice, amountMinor: line.unitPrice.amountMinor * line.quantity })}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="summary">
              <tr>
                <th scope="row" colSpan={3}>
                  Subtotal
                </th>
                <td className="num">{money(order.subtotal)}</td>
              </tr>
              {order.discounts.map((discount, i) => (
                <tr key={i}>
                  <th scope="row" colSpan={3}>
                    {discountLabel(discount)}
                  </th>
                  <td className="num">−{money(discount.amount)}</td>
                </tr>
              ))}
              {order.shippingOption && (
                <tr>
                  <th scope="row" colSpan={3}>
                    Shipping ({order.shippingOption.id.toLowerCase()}, {order.shippingOption.carrier})
                  </th>
                  <td className="num">{money(order.shippingOption.cost)}</td>
                </tr>
              )}
              <tr>
                <th scope="row" colSpan={3}>
                  Tax
                </th>
                <td className="num">{money(order.tax)}</td>
              </tr>
              <tr className="summary-total">
                <th scope="row" colSpan={3}>
                  Total
                </th>
                <td className="num">{money(order.total)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <section className="panel" aria-labelledby="order-payments">
        <h2 id="order-payments">Payments</h2>
        <Payments payments={payments} money={money} />
      </section>

      <section className="panel" aria-labelledby="order-shipment">
        <h2 id="order-shipment">Shipment</h2>
        <OrderShipment shipment={shipment} />
      </section>

      <section className="panel" aria-labelledby="order-rmas">
        <h2 id="order-rmas">RMAs</h2>
        <Rmas rmas={rmas} />
      </section>

      {rmas.data && <Replacements rmas={rmas.data.items} />}

      <section className="panel" aria-labelledby="order-refunds">
        <h2 id="order-refunds">Refunds</h2>
        <Refunds payments={payments} rmas={rmas.data?.items ?? []} money={money} />
      </section>

      <section className="panel" aria-labelledby="order-notifications">
        <h2 id="order-notifications">Notifications</h2>
        <Notifications notifications={notifications} labelledBy="order-notifications" />
      </section>

      <section className="panel" aria-labelledby="order-history">
        <h2 id="order-history">Status history</h2>
        <div className="table-scroll">
          <table className="table" aria-labelledby="order-history">
            <thead>
              <tr>
                <th scope="col">Status</th>
                <th scope="col">When</th>
                <th scope="col">Changed by</th>
              </tr>
            </thead>
            <tbody>
              {historyRowsOf(order).map((row, i) => (
                <tr key={i} aria-current={row.current ? 'true' : undefined}>
                  <th scope="row">
                    {row.status}
                    {row.current && <span className="muted"> (current)</span>}
                  </th>
                  <td>
                    {row.at ? <time dateTime={row.at}>{row.time}</time> : <span className="muted">{row.time}</span>}
                    {row.note && <small className="hint">{row.note}</small>}
                  </td>
                  <td>{row.changedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}

type ShipmentQuery = ReturnType<typeof useOrderShipment>

/** The Order's Shipment, its status and a link to it; none until the fulfillment Saga creates it once paid. */
function OrderShipment({ shipment }: { shipment: ShipmentQuery }) {
  if (shipment.error) {
    return (
      <ErrorMessage
        error={shipment.error}
        title="Couldn't load the Shipment"
        retrying={shipment.isFetching}
        onRetry={() => shipment.refetch()}
      />
    )
  }
  if (shipment.data === undefined) {
    return (
      <div aria-busy="true">
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  if (shipment.data === null) {
    return <p className="muted">No Shipment: it is created once the Order is paid, from Sprint 5 on.</p>
  }
  const { id, warehouseId, status, trackingNumber } = shipment.data
  return (
    <dl className="facts">
      <div>
        <dt>Status</dt>
        <dd>
          <ShipmentStatusBadge status={status} />
        </dd>
      </div>
      <div>
        <dt>Warehouse</dt>
        <dd>
          <code>{warehouseId}</code>
        </dd>
      </div>
      {trackingNumber && (
        <div>
          <dt>Tracking number</dt>
          <dd>
            <code>{trackingNumber}</code>
          </dd>
        </div>
      )}
      <div>
        <dt>Shipment</dt>
        <dd>
          <Link to={`/fulfillment/${id}`}>Open the Shipment</Link>
        </dd>
      </div>
    </dl>
  )
}

type RmasQuery = ReturnType<typeof useOrderRmas>

/** The Order's RMAs, oldest first, each linking to its page; none until its Customer requests one. */
function Rmas({ rmas }: { rmas: RmasQuery }) {
  if (rmas.error) {
    return (
      <ErrorMessage
        error={rmas.error}
        title="Couldn't load the RMAs"
        retrying={rmas.isFetching}
        onRetry={() => rmas.refetch()}
      />
    )
  }
  if (!rmas.data) {
    return (
      <div aria-busy="true">
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  if (rmas.data.items.length === 0) return <p className="muted">No return or warranty claim was requested.</p>
  return (
    <div className="table-scroll">
      <table className="table" aria-labelledby="order-rmas">
        <thead>
          <tr>
            <th scope="col">RMA</th>
            <th scope="col">Kind</th>
            <th scope="col">Product</th>
            <th scope="col" className="num">
              Quantity
            </th>
            <th scope="col">Requested</th>
            <th scope="col">Status</th>
          </tr>
        </thead>
        <tbody>
          {rmas.data.items.map((rma) => (
            <tr key={rma.id}>
              <th scope="row">
                <Link to={`/rmas/${rma.id}`}>
                  <code>{rma.reference}</code>
                </Link>
              </th>
              <td>{rmaKindLabel(rma.kind)}</td>
              <td>{rma.productName ?? <code>{rma.variantId}</code>}</td>
              <td className="num">{rma.quantity}</td>
              <td>
                <time dateTime={rma.requestedAt}>{formatTime(rma.requestedAt)}</time>
              </td>
              <td>
                <RmaStatusBadge status={rma.status} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * The replacement Shipments the returns Saga created for the Order's RMAs, one per confirmed fault
 * it could replace, each with its status and a link to it. Shown only when a fault was confirmed.
 */
function Replacements({ rmas }: { rmas: readonly Rma[] }) {
  const faulty = rmas.filter((rma) => rma.inspection?.outcome === 'FAULT_CONFIRMED')
  const replacements = useReplacements(faulty.map((rma) => rma.id))
  if (faulty.length === 0) return null
  const failed = replacements.find((replacement) => replacement.error)
  return (
    <section className="panel" aria-labelledby="order-replacements">
      <h2 id="order-replacements">Replacement Shipments</h2>
      {failed ? (
        <ErrorMessage
          error={failed.error}
          title="Couldn't load the replacements"
          retrying={failed.isFetching}
          onRetry={() => failed.refetch()}
        />
      ) : (
        <div className="table-scroll">
          <table className="table" aria-labelledby="order-replacements">
            <thead>
              <tr>
                <th scope="col">RMA</th>
                <th scope="col">Status</th>
                <th scope="col">Warehouse</th>
                <th scope="col">Tracking number</th>
                <th scope="col">Shipment</th>
              </tr>
            </thead>
            <tbody>
              {faulty.map((rma, i) => {
                const replacement = replacements[i].data
                return (
                  <tr key={rma.id}>
                    <th scope="row">
                      <Link to={`/rmas/${rma.id}`}>
                        <code>{rma.reference}</code>
                      </Link>
                    </th>
                    {replacement ? (
                      <>
                        <td>
                          <ShipmentStatusBadge status={replacement.status} />
                        </td>
                        <td>
                          <code>{replacement.warehouseId}</code>
                        </td>
                        <td>
                          {replacement.trackingNumber ? (
                            <code>{replacement.trackingNumber}</code>
                          ) : (
                            <span className="muted">—</span>
                          )}
                        </td>
                        <td>
                          <Link to={`/fulfillment/${replacement.id}`}>Open the replacement</Link>
                        </td>
                      </>
                    ) : (
                      <td colSpan={4} className="muted">
                        {replacement === null ? 'None: it was refunded, or the returns Saga is at work.' : 'Loading…'}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

type PaymentsQuery = ReturnType<typeof usePayments>

/** The Refunds of the Order's Payments, oldest first, each linking to the RMA it was for. */
function Refunds({
  payments,
  rmas,
  money,
}: {
  payments: PaymentsQuery
  rmas: readonly Rma[]
  money: (amount: Money) => string
}) {
  // The Payments panel says why they couldn't be loaded.
  if (payments.error) return <p className="muted">Unknown: the Payments couldn't be loaded.</p>
  if (!payments.data) {
    return (
      <div aria-busy="true">
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  const rows = refundRowsOf(payments.data)
  if (rows.length === 0) return <p className="muted">Nothing was refunded.</p>
  const rmaIdOf = new Map(rmas.map((rma) => [rma.reference, rma.id]))
  return (
    <div className="table-scroll">
      <table className="table" aria-labelledby="order-refunds">
        <thead>
          <tr>
            <th scope="col">For</th>
            <th scope="col">Outcome</th>
            <th scope="col" className="num">
              Amount
            </th>
            <th scope="col">Gateway reference</th>
            <th scope="col">When</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => {
            const rmaId = row.rmaReference && rmaIdOf.get(row.rmaReference)
            return (
              <tr key={i}>
                <th scope="row">
                  {rmaId ? (
                    <Link to={`/rmas/${rmaId}`}>
                      <code>{row.rmaReference}</code>
                    </Link>
                  ) : (
                    <code>{row.rmaReference ?? '—'}</code>
                  )}
                </th>
                <td>
                  {row.outcome}
                  {row.declineReason && <small className="hint">{row.declineReason}</small>}
                </td>
                <td className="num">{money(row.amount)}</td>
                <td>
                  <code>{row.reference}</code>
                </td>
                <td>
                  <time dateTime={row.at}>{row.time}</time>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

const paymentTones: Record<PaymentStatus, Tone> = {
  PENDING: 'warning',
  AUTHORIZED: 'success',
  DECLINED: 'danger',
  VOIDED: 'neutral',
  CAPTURED: 'success',
  PARTIALLY_REFUNDED: 'neutral',
  REFUNDED: 'neutral',
}

/**
 * The Order's Payments, newest first, each with its Payment transactions. An Order can have none,
 * when the payment gateway never answered, so nothing was recorded.
 */
function Payments({ payments, money }: { payments: PaymentsQuery; money: (amount: Money) => string }) {
  if (payments.error) {
    return (
      <ErrorMessage
        error={payments.error}
        title="Couldn't load the Payments"
        retrying={payments.isFetching}
        onRetry={() => payments.refetch()}
      />
    )
  }
  if (!payments.data) {
    return (
      <div aria-busy="true">
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  if (payments.data.length === 0) {
    return (
      <p className="muted">No Payment was recorded for this Order: the payment gateway may have failed to answer.</p>
    )
  }
  return payments.data.map((payment) => <PaymentDetail key={payment.id} payment={payment} money={money} />)
}

function PaymentDetail({ payment, money }: { payment: Payment; money: (amount: Money) => string }) {
  const transactionsId = `payment-${payment.id}-transactions`
  return (
    <article className="payment" aria-label={`Payment ${payment.id}`}>
      <dl className="facts">
        <div>
          <dt>Status</dt>
          <dd>
            <Badge tone={paymentTones[payment.status]} dot>
              {paymentStatusLabel(payment.status)}
            </Badge>
            {payment.declineReason && <small className="hint">Declined: {payment.declineReason}</small>}
          </dd>
        </div>
        <div>
          <dt>Amount</dt>
          <dd>{money(payment.amount)}</dd>
        </div>
        {payment.status === 'PARTIALLY_REFUNDED' && (
          <div>
            <dt>Refunded</dt>
            <dd>
              {money(refundedOf(payment))} of {money(payment.amount)}
            </dd>
          </div>
        )}
        <div>
          <dt>Gateway reference</dt>
          <dd>
            <code>{payment.gatewayReference ?? '—'}</code>
          </dd>
        </div>
        <div>
          <dt>Payment ID</dt>
          <dd>
            <code>{payment.id}</code>
          </dd>
        </div>
      </dl>
      <h3 id={transactionsId} className="visually-hidden">
        Transactions
      </h3>
      <div className="table-scroll">
        <table className="table" aria-labelledby={transactionsId}>
          <thead>
            <tr>
              <th scope="col">Kind</th>
              <th scope="col">Outcome</th>
              <th scope="col" className="num">
                Amount
              </th>
              <th scope="col">Reference</th>
              <th scope="col">Decline reason</th>
              <th scope="col">When</th>
            </tr>
          </thead>
          <tbody>
            {transactionRowsOf(payment).map((row, i) => (
              <tr key={i}>
                <th scope="row">{row.kind}</th>
                <td>{row.outcome}</td>
                <td className="num">{money(row.amount)}</td>
                <td>
                  <code>{row.reference}</code>
                  {row.refundFor && <small className="hint">For {row.refundFor}</small>}
                </td>
                <td>{row.declineReason ?? <span className="muted">—</span>}</td>
                <td>
                  <time dateTime={row.at}>{row.time}</time>
                  {row.note && <small className="hint">{row.note}</small>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </article>
  )
}
