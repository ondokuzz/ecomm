import { Link, useLocation, useParams } from 'react-router'
import { useCurrencies } from '../api/currencies'
import { isNotFound } from '../api/failure'
import { useOrder } from '../api/orders'
import { NotFound } from '../components/NotFound'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { ErrorMessage } from '../components/Status'
import { Icon } from '../components/ui/Icon'
import { type Currencies, formatMoney } from '../domain/money'
import { type Order, discountLabel, formatTime, historyRowsOf, orderReference } from '../domain/order'
import type { FromListState } from './OrderListPage'

/** `/orders/:id`: one Order, whoever it belongs to, and how it got to its Status. Read only. */
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
  const money = (amount: Order['total']) => formatMoney(amount, currencies)
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
