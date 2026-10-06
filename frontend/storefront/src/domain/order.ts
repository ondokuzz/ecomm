import { type VariantDetail, variantName } from './catalog'
import { type Money, timesMoney } from './money'

export type OrderStatus = 'PLACED' | 'PAID' | 'FULFILLED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'RETURNED'

export interface OrderLine {
  variantId: string
  quantity: number
  unitPrice: Money
}

/**
 * What a running Campaign or a Coupon took off an Order or a Checkout Session: a Campaign's names it
 * by ID and name, a Coupon's by the code the Customer entered; the fields it lacks are null.
 */
export interface Discount {
  source: 'CAMPAIGN' | 'COUPON'
  couponCode: string | null
  campaignId: string | null
  campaignName: string | null
  amount: Money
}

/** What gave a Discount, as the Customer reads it: its Campaign's name, or its Coupon's code. */
function discountName(discount: Discount): string {
  return (discount.source === 'CAMPAIGN' ? discount.campaignName : discount.couponCode) ?? ''
}

/** A Discount's row in a price breakdown: "Campaign: Audio week" or "Coupon: WELCOME10". */
export function discountLabel(discount: Discount): string {
  return `${discount.source === 'CAMPAIGN' ? 'Campaign' : 'Coupon'}: ${discountName(discount)}`
}

/** What gave each Discount, in order, as an Order card lists them. */
export function discountNames(discounts: Discount[]): string[] {
  return discounts.map(discountName)
}

/** The Coupon's Discount among them, if a Coupon is applied. */
export function appliedCoupon(discounts: Discount[]): Discount | undefined {
  return discounts.find((d) => d.source === 'COUPON')
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
  /** Every Discount it got, in the order they applied; empty when none. */
  discounts: Discount[]
  tax: Money
  /** The subtotal, less every Discount, plus the tax: what the Customer paid. */
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

/** One line of an Order's summary; a Discount's amount is negative, as it comes off. */
export interface OrderSummaryRow {
  label: string
  amount: Money
  isTotal?: boolean
}

/**
 * What an Order, or a Checkout Session, comes to: subtotal, each Discount in the order it applied,
 * by its Campaign's name or Coupon's code, tax and total.
 */
export function summaryRows({
  subtotal,
  discounts,
  tax,
  total,
}: Pick<Order, 'subtotal' | 'discounts' | 'tax' | 'total'>): OrderSummaryRow[] {
  const discountRows: OrderSummaryRow[] = discounts.map((discount) => ({
    label: discountLabel(discount),
    amount: { ...discount.amount, amountMinor: -discount.amount.amountMinor },
  }))
  return [
    { label: 'Subtotal', amount: subtotal },
    ...discountRows,
    { label: 'Tax', amount: tax },
    { label: 'Total', amount: total, isTotal: true },
  ]
}

/** What an Order comes to, as Order Management worked it out: subtotal, each Discount, tax and total. */
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
