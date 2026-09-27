import type { OrderStatus } from '../domain/order'

const labels: Record<OrderStatus, string> = {
  PLACED: 'Placed',
  PAID: 'Paid',
  FULFILLED: 'Fulfilled',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  RETURNED: 'Returned',
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}>{labels[status]}</span>
}
