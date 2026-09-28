import type { OrderStatus } from '../domain/order'
import { Badge, type Tone } from './ui/Badge'

const looks: Record<OrderStatus, { label: string; tone: Tone }> = {
  PLACED: { label: 'Placed', tone: 'info' },
  PAID: { label: 'Paid', tone: 'success' },
  FULFILLED: { label: 'Fulfilled', tone: 'primary' },
  SHIPPED: { label: 'Shipped', tone: 'primary' },
  DELIVERED: { label: 'Delivered', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
  RETURNED: { label: 'Returned', tone: 'warning' },
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const { label, tone } = looks[status]
  return (
    <Badge tone={tone} dot className={`status-${status.toLowerCase()}`}>
      {label}
    </Badge>
  )
}
