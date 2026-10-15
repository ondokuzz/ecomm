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
