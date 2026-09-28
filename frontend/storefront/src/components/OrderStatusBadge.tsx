import { type OrderStatus, orderStatusLabel } from '../domain/order'
import { orderStatusTones } from './orderStatusTones'
import { Badge } from './ui/Badge'

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    // The smoke test finds the Order Status by its `status-*` class.
    <Badge tone={orderStatusTones[status]} dot className={`status-${status.toLowerCase()}`}>
      {orderStatusLabel(status)}
    </Badge>
  )
}
