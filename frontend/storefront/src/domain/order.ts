import type { Money } from './money'

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
