import { type VariantDetail, variantName } from './catalog'
import { type Money, timesMoney } from './money'

export type OrderStatus = 'PLACED' | 'PAID' | 'FULFILLED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'RETURNED'

export interface OrderLine {
  variantId: string
  quantity: number
  unitPrice: Money
}

/** What a Coupon took off an Order, and the code the Customer entered for it. */
export interface Discount {
  couponCode: string
  amount: Money
}

export interface Order {
  id: string
  status: OrderStatus
  lines: OrderLine[]
  /** The sum of the lines. */
  subtotal: Money
  discount: Discount | null
  tax: Money
  /** The subtotal, less the discount, plus the tax: what the Customer paid. */
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

/** One line of an Order's summary; a discount's amount is negative, as it comes off. */
export interface OrderSummaryRow {
  label: string
  amount: Money
  isTotal?: boolean
}

/**
 * What an Order, or a Checkout Session, comes to: subtotal, any discount with its Coupon code, tax
 * and total.
 */
export function summaryRows({
  subtotal,
  discount,
  tax,
  total,
}: Pick<Order, 'subtotal' | 'discount' | 'tax' | 'total'>): OrderSummaryRow[] {
  const discountRows: OrderSummaryRow[] = discount
    ? [
        {
          label: `Discount (${discount.couponCode})`,
          amount: { ...discount.amount, amountMinor: -discount.amount.amountMinor },
        },
      ]
    : []
  return [
    { label: 'Subtotal', amount: subtotal },
    ...discountRows,
    { label: 'Tax', amount: tax },
    { label: 'Total', amount: total, isTotal: true },
  ]
}

/** What an Order comes to, as Order Management worked it out: subtotal, any discount, tax and total. */
export function orderSummaryRows(order: Order): OrderSummaryRow[] {
  return summaryRows(order)
}

/** An Order Line with its total at the captured price, and its Variant when Catalog still has it. */
export interface NamedOrderLine extends OrderLine {
  lineTotal: Money
  variant?: VariantDetail
  name?: string
}

export function nameOrderLines(order: Order, variantsById: Record<string, VariantDetail | undefined>): NamedOrderLine[] {
  return order.lines.map((line) => {
    const named: NamedOrderLine = { ...line, lineTotal: timesMoney(line.unitPrice, line.quantity) }
    const variant = variantsById[line.variantId]
    return variant ? { ...named, variant, name: variantName(variant) } : named
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
