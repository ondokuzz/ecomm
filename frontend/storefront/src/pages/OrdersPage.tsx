import { Link } from 'react-router'
import { useOrders } from '../api/orders'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { ErrorMessage, Loading } from '../components/Status'
import { formatMoney } from '../domain/money'

export function OrdersPage() {
  const orders = useOrders()

  if (orders.isPending) return <Loading />
  if (orders.error) return <ErrorMessage error={orders.error} />

  return (
    <section>
      <h1>My orders</h1>
      {orders.data.length === 0 ? (
        <p>
          You have no orders yet. <Link to="/">Browse products</Link>
        </p>
      ) : (
        <table className="lines">
          <thead>
            <tr>
              <th>Placed</th>
              <th>Order</th>
              <th>Status</th>
              <th className="num">Total</th>
            </tr>
          </thead>
          <tbody>
            {orders.data.map((order) => (
              <tr key={order.id}>
                <td>{new Date(order.placedAt).toLocaleString()}</td>
                <td>
                  <Link to={`/orders/${encodeURIComponent(order.id)}`}>
                    <code>{order.id}</code>
                  </Link>
                </td>
                <td>
                  <OrderStatusBadge status={order.status} />
                </td>
                <td className="num">{formatMoney(order.total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
