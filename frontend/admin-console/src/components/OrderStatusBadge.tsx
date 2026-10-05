import { type OrderStatus, orderStatusLabel } from '../domain/order'
import { Badge, type Tone } from './ui/Badge'

const tones: Record<OrderStatus, Tone> = {
  PLACED: 'info',
  PAID: 'primary',
  FULFILLED: 'primary',
  SHIPPED: 'primary',
  DELIVERED: 'success',
  CANCELLED: 'danger',
  RETURNED: 'warning',
}

/** An Order Status as a pill: waiting, on its way, done, or ended off the main path. */
export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    <Badge tone={tones[status]} dot>
      {orderStatusLabel(status)}
    </Badge>
  )
}
