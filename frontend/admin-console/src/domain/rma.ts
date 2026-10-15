import { formatTime } from './order'

/** An RMA's status: requested by its Customer, then approved or rejected by Staff, once. */
export type RmaStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED'

/** Every RMA status, in the order an RMA moves through them. */
export const rmaStatuses: readonly RmaStatus[] = ['REQUESTED', 'APPROVED', 'REJECTED']

/** What an RMA claims: a return within the Return Period, or a warranty claim within the Warranty Window. */
export type RmaKind = 'RETURN' | 'WARRANTY_CLAIM'

/** One RMA decision, kept for good: who decided, when, and the approval's note or the rejection's reason. */
export interface RmaDecision {
  status: Exclude<RmaStatus, 'REQUESTED'>
  /** The deciding Staff member's ID. */
  decidedBy: string
  decidedAt: string
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
  /** Its decision history, oldest first. */
  decisions: RmaDecision[]
}

/** An RMA status as Staff read it: "Requested", "Approved", "Rejected". */
export function rmaStatusLabel(status: RmaStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase()
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
    status: rmaStatuses.includes(status) ? status : 'REQUESTED',
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
