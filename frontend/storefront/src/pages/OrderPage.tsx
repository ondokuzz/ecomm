import { Link, useParams, useSearchParams } from 'react-router'
import { useOrder } from '../api/orders'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { ErrorMessage, Loading } from '../components/Status'
import { formatMoney, timesMoney } from '../domain/money'

/** An Order and its Order Status, read from Order Management; also the confirmation after checkout. */
export function OrderPage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const query = useOrder(id)

  if (query.isPending) return <Loading />
  if (query.error) return <ErrorMessage error={query.error} />
  const order = query.data

  return (
    <section>
      {params.has('placed') && <p className="confirmation">Thank you! Your order is confirmed.</p>}
      <h1>
        Order <code>{order.id}</code>
      </h1>
      <p>
        Status: <OrderStatusBadge status={order.status} />
      </p>
      <p className="muted">Placed {new Date(order.placedAt).toLocaleString()}</p>
      <table className="lines">
        <thead>
          <tr>
            <th>Product</th>
            <th className="num">Price</th>
            <th className="num">Quantity</th>
            <th className="num">Total</th>
          </tr>
        </thead>
        <tbody>
          {order.lines.map((line) => (
            <tr key={line.variantId}>
              <td>
                <Link to={`/products/${encodeURIComponent(line.variantId)}`}>{line.variantId}</Link>
              </td>
              <td className="num">{formatMoney(line.unitPrice)}</td>
              <td className="num">{line.quantity}</td>
              <td className="num">{formatMoney(timesMoney(line.unitPrice, line.quantity))}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th colSpan={3}>Total</th>
            <th className="num">{formatMoney(order.total)}</th>
          </tr>
        </tfoot>
      </table>
      <Link to="/orders">All my orders</Link>
    </section>
  )
}
