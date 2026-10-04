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

/**
 * One entry in an Order's Order Status history: the Status it moved to, when, and which caller moved
 * it. A backfilled entry was reconstructed for an Order placed before histories were kept; its `at`
 * is only the Order's placement time.
 */
export interface StatusHistoryEntry {
  status: OrderStatus
  at: string
  changedBy: 'CHECKOUT'
  backfilled: boolean
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
  /** Every Order Status the Order has been in, oldest first. */
  statusHistory: StatusHistoryEntry[]
}

/** How many Orders a page of the Customer's list shows. */
export const ordersPageSize = 12

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
  /** When the Order reached this step; absent for a step still to come, or one whose time is unknown. */
  at?: string
}

const mainPath: OrderStatus[] = ['PLACED', 'PAID', 'FULFILLED', 'SHIPPED', 'DELIVERED']

/**
 * The Order Statuses an Order has been through, from its Order Status history, with when; then, while
 * it is on the main path, every step still to come. Cancelled and Returned are final, so they end it.
 */
export function orderTimeline(order: Pick<Order, 'statusHistory'>): TimelineStep[] {
  const history = order.statusHistory
  const past: TimelineStep[] = history.map((entry, i) => ({
    status: entry.status,
    state: i === history.length - 1 ? 'current' : 'done',
    ...(entry.backfilled ? {} : { at: entry.at }),
  }))
  const onMainPath = mainPath.indexOf(history[history.length - 1].status)
  const upcoming: TimelineStep[] =
    onMainPath === -1 ? [] : mainPath.slice(onMainPath + 1).map((status) => ({ status, state: 'upcoming' }))
  return [...past, ...upcoming]
}
