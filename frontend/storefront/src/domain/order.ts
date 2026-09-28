import type { Product } from './catalog'
import { type Money, timesMoney } from './money'

export type OrderStatus = 'PLACED' | 'PAID' | 'FULFILLED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'RETURNED'

export interface OrderLine {
  variantId: string
  quantity: number
  unitPrice: Money
}

export interface Order {
  id: string
  status: OrderStatus
  lines: OrderLine[]
  total: Money
  placedAt: string
}

/** What checkout answers with: the new Order's ID and its Order Status. */
export interface CheckoutResult {
  orderId: string
  status: OrderStatus
}

/** An Order Status as the Customer reads it: "Placed", "Paid", … */
export function orderStatusLabel(status: OrderStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase()
}

/** A short handle for an Order to show the Customer: the start of its ID, which is a random UUID. */
export function orderReference(order: Order): string {
  return order.id.slice(0, 8).toUpperCase()
}

/** How many units an Order holds, in words. */
export function orderItemCountLabel(order: Order): string {
  const count = order.lines.reduce((sum, line) => sum + line.quantity, 0)
  return count === 1 ? '1 item' : `${count} items`
}

/** An Order Line with its total at the captured price, and its Product when Catalog still has it. */
export interface NamedOrderLine extends OrderLine {
  lineTotal: Money
  product?: Product
  name?: string
}

export function nameOrderLines(order: Order, productsByVariantId: Record<string, Product | undefined>): NamedOrderLine[] {
  return order.lines.map((line) => {
    const named: NamedOrderLine = { ...line, lineTotal: timesMoney(line.unitPrice, line.quantity) }
    const product = productsByVariantId[line.variantId]
    return product ? { ...named, product, name: product.name } : named
  })
}

export interface TimelineStep {
  status: OrderStatus
  state: 'done' | 'current' | 'upcoming'
}

const mainPath: OrderStatus[] = ['PLACED', 'PAID', 'FULFILLED', 'SHIPPED', 'DELIVERED']

/**
 * The Order Statuses an Order goes through, up to and including its current one. The main path
 * shows every step to come; Cancelled and Returned are final, so they end it.
 */
export function orderTimeline(status: OrderStatus): TimelineStep[] {
  // An Order can be cancelled once Placed or Paid; its Order Status doesn't say which.
  const path: OrderStatus[] =
    status === 'CANCELLED' ? ['PLACED', 'CANCELLED'] : status === 'RETURNED' ? [...mainPath, 'RETURNED'] : mainPath
  const currentIndex = path.indexOf(status)
  return path.map((step, i) => ({
    status: step,
    state: i < currentIndex ? 'done' : i === currentIndex ? 'current' : 'upcoming',
  }))
}
