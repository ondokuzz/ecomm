import type { Money } from './money'
import type { Page } from './paging'

export type OrderStatus = 'PLACED' | 'PAID' | 'FULFILLED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'RETURNED'

/** Every Order Status, in the order an Order moves through them; the two final ones last. */
export const orderStatuses: readonly OrderStatus[] = [
  'PLACED',
  'PAID',
  'FULFILLED',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'RETURNED',
]

/** The caller that made a Status change: Checkout, for now. */
export type Caller = 'CHECKOUT'

/**
 * One entry in an Order's Order Status history. A backfilled entry was reconstructed for an Order
 * placed before histories were kept; its `at` is only the Order's placement time.
 */
export interface StatusHistoryEntry {
  status: OrderStatus
  at: string
  changedBy: Caller
  backfilled: boolean
}

export interface OrderLine {
  variantId: string
  quantity: number
  unitPrice: Money
}

/** What a Coupon took off an Order, and its code. */
export interface Discount {
  couponCode: string
  amount: Money
}

/** An Order as Order Management shows it to Staff: whoever it belongs to, and how it got here. */
export interface Order {
  id: string
  /** The Customer the Order belongs to: their token's `sub`. */
  customerId: string
  status: OrderStatus
  lines: OrderLine[]
  subtotal: Money
  discount: Discount | null
  tax: Money
  /** The subtotal, less the discount, plus the tax: what the Customer paid. */
  total: Money
  placedAt: string
  /** Every Order Status the Order has been in, oldest first. */
  statusHistory: StatusHistoryEntry[]
}

/** How many Orders a page of the list shows. */
export const ordersPageSize = 20

/** An Order Status as Staff read it: "Placed", "Paid", … */
export function orderStatusLabel(status: OrderStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase()
}

/** The short handle the Storefront shows a Customer, and they quote: the start of the Order's ID. */
export function orderReference(order: Pick<Order, 'id'>): string {
  return order.id.slice(0, 8).toUpperCase()
}

/**
 * The Order list's filters as the URL keeps them: an Order Status, a Customer ID, the first and
 * last days placed (`YYYY-MM-DD`, in the browser's time zone), an Order reference, and the page,
 * from 0. An empty string is no filter.
 */
export interface OrderFilters {
  status: OrderStatus | ''
  customer: string
  from: string
  to: string
  reference: string
  page: number
}

/** The filters in a URL's query. Its `page` counts from 1, as Staff read it. */
export function filtersOf(params: URLSearchParams): OrderFilters {
  const status = params.get('status') ?? ''
  const page = Number(params.get('page'))
  return {
    status: orderStatuses.includes(status as OrderStatus) ? (status as OrderStatus) : '',
    customer: params.get('customer') ?? '',
    from: dayOf(params.get('from')),
    to: dayOf(params.get('to')),
    reference: params.get('reference') ?? '',
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
  }
}

/** The day a URL names, as `YYYY-MM-DD`, or none unless it is a real one. */
function dayOf(value: string | null): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '')
  if (!match) return ''
  const [year, month, date] = match.slice(1).map(Number)
  const day = new Date(year, month - 1, date)
  return day.getFullYear() === year && day.getMonth() === month - 1 && day.getDate() === date ? value! : ''
}

/** The URL query that keeps the filters: the inverse of `filtersOf`, leaving out what is empty. */
export function searchOf(filters: OrderFilters): string {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.customer.trim()) params.set('customer', filters.customer.trim())
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.reference.trim()) params.set('reference', filters.reference.trim())
  if (filters.page > 0) params.set('page', String(filters.page + 1))
  const search = params.toString()
  return search && `?${search}`
}

const referenceError = 'An Order reference is hex digits, such as #3F2A9C1B.'

/**
 * The query for `GET /staff/orders` that the filters stand for, or why it can't be sent. The days
 * become instants: from the first one's local midnight up to the midnight after the last, so a day
 * is the whole of it as Staff see it. The reference may have its `#`, and may be a full ID.
 */
export function ordersQueryOf(filters: OrderFilters, size: number): { query: string } | { error: string } {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  const customer = filters.customer.trim()
  if (customer) params.set('customerId', customer)
  if (filters.from) params.set('placedFrom', localMidnight(filters.from, 0))
  if (filters.to) params.set('placedTo', localMidnight(filters.to, 1))
  const reference = filters.reference.trim()
  if (reference) {
    const prefix = reference.replace(/^#/, '')
    if (!/^[0-9a-fA-F-]{1,36}$/.test(prefix)) return { error: referenceError }
    params.set('idPrefix', prefix)
  }
  params.set('page', String(filters.page))
  params.set('size', String(size))
  return { query: `?${params}` }
}

/** The instant of the local midnight that starts `day` (`YYYY-MM-DD`), plus `days` more. */
function localMidnight(day: string, days: number): string {
  const [year, month, date] = day.split('-').map(Number)
  return new Date(year, month - 1, date + days).toISOString()
}

/** How many pages the list has: at least one, even when it is empty. */
export function pageCount({ size, total }: Pick<Page<unknown>, 'size' | 'total'>): number {
  return Math.max(1, Math.ceil(total / size))
}

/** One Status change as the Order page lists it. */
export interface HistoryRow {
  status: string
  /** The instant, for `<time dateTime>`; absent when it was never recorded. */
  at?: string
  /** When, in the Staff member's own time zone, to the second. */
  time: string
  changedBy: string
  current: boolean
  /** Why the time is missing, for a backfilled change. */
  note?: string
}

const callers: Record<Caller, string> = { CHECKOUT: 'Checkout' }

/**
 * The Order's Status history for Staff, newest first, each change with when it happened and the
 * caller that made it. A backfilled change's time was never recorded, so it isn't shown.
 */
export function historyRowsOf(order: Pick<Order, 'statusHistory'>, locale?: string): HistoryRow[] {
  const last = order.statusHistory.length - 1
  return order.statusHistory
    .map((entry, i): HistoryRow => {
      const row = {
        status: orderStatusLabel(entry.status),
        changedBy: callers[entry.changedBy] ?? entry.changedBy,
        current: i === last,
      }
      return entry.backfilled
        ? { ...row, time: 'Not recorded', note: 'Reconstructed for an Order placed before histories were kept' }
        : { ...row, at: entry.at, time: formatTime(entry.at, locale) }
    })
    .reverse()
}

/** An instant as a date and a time to the second, in the browser's time zone. */
export function formatTime(at: string, locale?: string): string {
  return new Date(at).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'medium' })
}
