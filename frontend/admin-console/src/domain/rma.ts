import { formatTime } from './order'

/**
 * An RMA's status: requested by its Customer, approved or rejected by Staff, once; then received,
 * or expired; inspected; and refunded, exchanged, closed or put on hold.
 */
export type RmaStatus =
  | 'REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'RECEIVED'
  | 'EXPIRED'
  | 'INSPECTED'
  | 'REFUNDED'
  | 'EXCHANGED'
  | 'CLOSED'
  | 'ON_HOLD'

/** The RMA approval queue's tabs, each an RMA status. */
export const queueStatuses: readonly RmaStatus[] = ['REQUESTED', 'APPROVED', 'REJECTED']

/** What an RMA claims: a return within the Return Period, or a warranty claim within the Warranty Window. */
export type RmaKind = 'RETURN' | 'WARRANTY_CLAIM'

/**
 * One entry of an RMA's history, kept for good: the status it moved to, who moved it (a Staff
 * member's ID, `CUSTOMER` or `ORCHESTRATION`), when, and its note, such as a rejection's reason.
 */
export interface RmaHistoryEntry {
  status: RmaStatus
  by: string
  at: string
  note?: string
}

/**
 * The window an RMA was checked against, as it was then: the Return Period, or the Warranty Window
 * with the Product's Warranty terms. Both start at delivery.
 */
export interface RmaWindow {
  kind: 'RETURN_PERIOD' | 'WARRANTY_WINDOW'
  startsAt: string
  endsAt: string
  warranty?: { months: number; manufacturer: string }
}

/** An RMA as Returns & Warranty shows it to Staff. */
export interface Rma {
  id: string
  /** `RMA-` and the start of its ID, as the Customer quotes it. */
  reference: string
  orderId: string
  customerId: string
  variantId: string
  productName?: string
  quantity: number
  /** One per unit, for a serial-tracked Variant; empty otherwise. */
  serialNumbers: string[]
  kind: RmaKind
  /** The Customer's reason. */
  reason: string
  window: RmaWindow
  status: RmaStatus
  requestedAt: string
  /** Its RMA history, oldest first: its request, then every step after. */
  history: RmaHistoryEntry[]
}

/** An RMA status as Staff read it: "Requested", "Approved", "On hold". */
export function rmaStatusLabel(status: RmaStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase().replace('_', ' ')
}

/** An RMA's kind as Staff read it: "Return" or "Warranty claim". */
export function rmaKindLabel(kind: RmaKind): string {
  return kind === 'RETURN' ? 'Return' : 'Warranty claim'
}

/** How many RMAs a page of the queue shows. */
export const rmasPageSize = 20

/** The RMA approval queue's tab, an RMA status, and its page, counting from 0. */
export interface Queue {
  status: RmaStatus
  page: number
}

/** The queue a URL's query names: its `status` (requested unless another is named), and its `page`, counting from 1. */
export function queueOf(params: URLSearchParams): Queue {
  const status = params.get('status') as RmaStatus
  const page = Number(params.get('page'))
  return {
    status: queueStatuses.includes(status) ? status : 'REQUESTED',
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
  }
}

/** The URL query that keeps the queue: the inverse of `queueOf`, leaving out the defaults. */
export function queueSearchOf(queue: Queue): string {
  const params = new URLSearchParams()
  if (queue.status !== 'REQUESTED') params.set('status', queue.status)
  if (queue.page > 0) params.set('page', String(queue.page + 1))
  const search = params.toString()
  return search && `?${search}`
}

/** The query for `GET /rmas` that the queue stands for; Returns & Warranty lists them oldest first. */
export function rmasQueryOf(queue: Queue, size: number): string {
  return `?${new URLSearchParams({ status: queue.status, page: String(queue.page), size: String(size) })}`
}

/** The window an RMA was checked against, as its page shows it. */
export interface WindowView {
  name: 'Return Period' | 'Warranty Window'
  /** A Warranty Window's Warranty terms: "24 months, from Google". */
  terms?: string
  /** The instants, for `<time dateTime>`, and as Staff read them, in their own time zone. */
  startsAt: string
  from: string
  endsAt: string
  to: string
}

/** The window an RMA was checked against, named, with its Warranty terms when it is a Warranty Window. */
export function windowOf(window: RmaWindow, locale?: string): WindowView {
  const view: WindowView = {
    name: window.kind === 'RETURN_PERIOD' ? 'Return Period' : 'Warranty Window',
    startsAt: window.startsAt,
    from: formatTime(window.startsAt, locale),
    endsAt: window.endsAt,
    to: formatTime(window.endsAt, locale),
  }
  if (!window.warranty) return view
  const { months, manufacturer } = window.warranty
  return { ...view, terms: `${months} ${months === 1 ? 'month' : 'months'}, from ${manufacturer}` }
}

/** One RMA decision as the RMA page lists it. */
export interface DecisionRow {
  status: string
  decidedBy: string
  /** The instant, for `<time dateTime>`, and as Staff read it, to the second. */
  at: string
  time: string
  /** An approval's note, or a rejection's reason; absent from an approval without one. */
  note?: string
}

/** An RMA's RMA decisions, from its history, for Staff, newest first. A requested RMA has none. */
export function decisionRowsOf(rma: Pick<Rma, 'history'>, locale?: string): DecisionRow[] {
  return rma.history
    .filter((entry) => entry.status === 'APPROVED' || entry.status === 'REJECTED')
    .map((entry): DecisionRow => {
      const row = {
        status: rmaStatusLabel(entry.status),
        decidedBy: entry.by,
        at: entry.at,
        time: formatTime(entry.at, locale),
      }
      return entry.note ? { ...row, note: entry.note } : row
    })
    .reverse()
}

/** The longest note or reason Returns & Warranty keeps. */
const maxText = 1000

/**
 * The body of `POST /rmas/{id}/approve` or `/reject` for what Staff typed, or why it can't be sent:
 * an approval's note is optional, a rejection's reason is not, and either is at most 1,000
 * characters once trimmed.
 */
export function decisionOf(
  decision: 'approve' | 'reject',
  text: string,
): { body: { note?: string } | { reason: string } } | { error: string } {
  const trimmed = text.trim()
  if (trimmed.length > maxText) return { error: 'Keep it to 1,000 characters.' }
  if (decision === 'approve') return { body: trimmed ? { note: trimmed } : {} }
  if (!trimmed) return { error: 'Say why it is rejected: the Customer is told.' }
  return { body: { reason: trimmed } }
}
