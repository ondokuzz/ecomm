import type { OrderStatus } from '../domain/order'
import type { Tone } from './ui/Badge'

/** Each Order Status in a colour of its own; its badge always says it too, so colour is never the only cue. */
export const orderStatusTones: Record<OrderStatus, Tone> = {
  PLACED: 'neutral',
  PAID: 'info',
  FULFILLED: 'primary',
  SHIPPED: 'accent',
  DELIVERED: 'success',
  CANCELLED: 'danger',
  RETURNED: 'warning',
}
