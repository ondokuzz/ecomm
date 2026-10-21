import type { Money } from './money'
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
  /** What refunding all its units gives back; absent from one requested before Sprint 6 until its Order is projected. */
  refundAmount?: Money
  status: RmaStatus
  requestedAt: string
  /** Its Send-back deadline, from approval. */
  sendBackBy?: string
  /** The Warehouse its units go back to, from approval. */
  warehouseId?: string
  /** Once inspected. */
  inspection?: { outcome: InspectionOutcome; note?: string }
  /** Once resolved: the Refund's reference at the payment gateway, or the replacement Shipment. */
  resolution?: { paymentTransactionReference?: string; replacementShipmentId?: string }
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

/** A tab of the RMA queue: a stage of the RMA lifecycle. */
export type QueueTabId = 'requested' | 'awaiting-units' | 'received' | 'on-hold' | 'closed'

export interface QueueTab {
  id: QueueTabId
  label: string
  /** The RMA statuses it lists. */
  statuses: readonly RmaStatus[]
}

/**
 * The RMA queue's tabs, in the order an RMA moves through them: awaiting a decision; approved, its
 * units on their way back; received, then inspected while the returns Saga resolves it; held after a
 * declined Refund; and every final status.
 */
export const queueTabs: readonly QueueTab[] = [
  { id: 'requested', label: 'Requested', statuses: ['REQUESTED'] },
  { id: 'awaiting-units', label: 'Awaiting units', statuses: ['APPROVED'] },
  { id: 'received', label: 'Received', statuses: ['RECEIVED', 'INSPECTED'] },
  { id: 'on-hold', label: 'On hold', statuses: ['ON_HOLD'] },
  { id: 'closed', label: 'Closed', statuses: ['REJECTED', 'EXPIRED', 'REFUNDED', 'EXCHANGED', 'CLOSED'] },
]

/** The tab with that ID. */
export function queueTabOf(id: QueueTabId): QueueTab {
  return queueTabs.find((tab) => tab.id === id)!
}

/** The RMA queue's tab and its page, counting from 0. */
export interface Queue {
  tab: QueueTabId
  page: number
}

/** The queue a URL's query names: its `tab` (requested unless another is named), and its `page`, counting from 1. */
export function queueOf(params: URLSearchParams): Queue {
  const tab = queueTabs.find((known) => known.id === params.get('tab'))
  const page = Number(params.get('page'))
  return {
    tab: tab?.id ?? 'requested',
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
  }
}

/** The URL query that keeps the queue: the inverse of `queueOf`, leaving out the defaults. */
export function queueSearchOf(queue: Queue): string {
  const params = new URLSearchParams()
  if (queue.tab !== 'requested') params.set('tab', queue.tab)
  if (queue.page > 0) params.set('page', String(queue.page + 1))
  const search = params.toString()
  return search && `?${search}`
}

/**
 * The query for `GET /rmas` that the queue stands for. Returns & Warranty lists approved RMAs by
 * Send-back deadline, soonest first, and any other tab oldest request first.
 */
export function rmasQueryOf(queue: Queue, size: number): string {
  return `?${new URLSearchParams({
    status: queueTabOf(queue.tab).statuses.join(','),
    page: String(queue.page),
    size: String(size),
  })}`
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

/** One step of an RMA's history as the RMA page lists it. */
export interface HistoryRow {
  status: string
  /** "Customer", "Returns Saga", or the Staff member's ID. */
  by: string
  /** Whether `by` is a Staff member's ID. */
  staff?: true
  /** The instant, for `<time dateTime>`, and as Staff read it, to the second. */
  at: string
  time: string
  /** An approval's note, a rejection's reason, an Inspection's note or a hold's reason. */
  note?: string
}

const takenBy: Record<string, string> = { CUSTOMER: 'Customer', ORCHESTRATION: 'Returns Saga' }

/** An RMA's history for Staff, newest first: its request, its decision, and every step after. */
export function historyRowsOf(rma: Pick<Rma, 'history'>, locale?: string): HistoryRow[] {
  return rma.history
    .map((entry): HistoryRow => {
      const caller = takenBy[entry.by]
      const row: HistoryRow = {
        status: rmaStatusLabel(entry.status),
        by: caller ?? entry.by,
        ...(caller ? {} : { staff: true as const }),
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

/**
 * One unit's Serial number as Staff type it on receiving: one of the RMA's, or the start of one, or
 * neither. Only what can't become one of the RMA's is refused as it is typed.
 */
export type ReceivedCheck =
  | { state: 'valid'; serialNumber: string }
  | { state: 'incomplete'; message: string }
  | { state: 'invalid'; message: string }

/**
 * Checks each input of the Receive form against the RMA's Serial numbers, matched whatever their
 * case as Returns & Warranty matches them, giving each as the RMA has it. One typed twice is refused
 * on its later input.
 */
export function receivedChecksOf(serialNumbers: readonly string[], entered: readonly string[]): ReceivedCheck[] {
  const seen = new Set<string>()
  return entered.map((typed): ReceivedCheck => {
    const upper = typed.trim().toUpperCase()
    const serialNumber = serialNumbers.find((serial) => serial.toUpperCase() === upper)
    if (serialNumber) {
      if (seen.has(serialNumber)) return { state: 'invalid', message: 'Typed twice: each unit has its own.' }
      seen.add(serialNumber)
      return { state: 'valid', serialNumber }
    }
    if (serialNumbers.some((serial) => serial.toUpperCase().startsWith(upper))) {
      return { state: 'incomplete', message: 'Enter the Serial number of a unit received.' }
    }
    return { state: 'invalid', message: 'Not one of this RMA’s Serial numbers.' }
  })
}

/** What Staff typed on receiving: a Serial number per unit, or the number of units for a Variant without Serial tracking. */
export type Received = { serialNumbers: string[] } | { quantity: string }

/** The body of `POST /rmas/{id}/receive`. */
export type ReceiveBody = { serialNumbers: string[] } | { quantity: number }

/**
 * The body of `POST /rmas/{id}/receive` for what Staff typed, or why it can't be sent, by field as
 * Returns & Warranty names them (`serialNumbers[1]`, `quantity`): every unit, each one of the
 * RMA's own Serial numbers, or a quantity confirming them all.
 */
export function receiveOf(
  rma: Pick<Rma, 'serialNumbers' | 'quantity'>,
  received: Received,
): { body: ReceiveBody } | { errors: Record<string, string> } {
  if ('quantity' in received) {
    const quantity = Number(received.quantity.trim())
    if (received.quantity.trim() && quantity === rma.quantity) return { body: { quantity } }
    const units = rma.quantity === 1 ? '1 unit' : `${rma.quantity} units`
    return { errors: { quantity: `Confirm the ${units} this RMA is for.` } }
  }
  const checks = receivedChecksOf(rma.serialNumbers, received.serialNumbers)
  const errors = Object.fromEntries(
    checks.flatMap((check, unit) => (check.state === 'valid' ? [] : [[serialFieldOf(unit), check.message]])),
  )
  if (Object.keys(errors).length > 0) return { errors }
  return { body: { serialNumbers: checks.map((check) => (check as { serialNumber: string }).serialNumber) } }
}

/**
 * Returns & Warranty's 422 for a receipt, by the input it is about: `serialMismatch` beside each
 * input holding a Serial number it refused, `quantityMismatch` beside the quantity, or beside the
 * units of a serial-tracked RMA. Any other refusal names no input.
 */
export function receiveRefusalsOf(problem: Record<string, unknown>, received: Received): Record<string, string> {
  if (problem.reason === 'quantityMismatch') {
    const field = 'quantity' in received ? 'quantity' : 'serialNumbers'
    return { [field]: `Returns & Warranty expects ${String(problem.expected)}.` }
  }
  if (problem.reason !== 'serialMismatch' || !Array.isArray(problem.serialNumbers) || 'quantity' in received) return {}
  const refused = new Set(problem.serialNumbers.map((serial) => String(serial).toUpperCase()))
  return Object.fromEntries(
    received.serialNumbers.flatMap((typed, unit) =>
      refused.has(typed.trim().toUpperCase())
        ? [[serialFieldOf(unit), 'Returns & Warranty says it isn’t one of this RMA’s Serial numbers.']]
        : [],
    ),
  )
}

/** The name Returns & Warranty gives a unit's Serial number on receiving, as its input is named. */
export function serialFieldOf(unit: number): string {
  return `serialNumbers[${unit}]`
}

/** How close a Send-back deadline gets before the queue flags it. */
const sendBackWarning = 2 * 24 * 60 * 60 * 1000

/** An approved RMA's Send-back deadline as Staff read it, flagged once it is within 2 days or passed. */
export interface SendBackView {
  /** The instant, for `<time dateTime>`, and as Staff read it, in their own time zone. */
  at: string
  time: string
  flag?: 'Due within 2 days' | 'Passed'
}

/** The Send-back deadline as of `now`: passed ones wait for the returns Saga to expire them. */
export function sendBackOf(sendBackBy: string, now: Date, locale?: string): SendBackView {
  const view = { at: sendBackBy, time: formatTime(sendBackBy, locale) }
  const left = new Date(sendBackBy).getTime() - now.getTime()
  if (left <= 0) return { ...view, flag: 'Passed' }
  if (left <= sendBackWarning) return { ...view, flag: 'Due within 2 days' }
  return view
}

/** What an Inspection found: a return accepted or refused, or a warranty claim's fault confirmed or not. */
export type InspectionOutcome = 'ACCEPTED' | 'REFUSED' | 'FAULT_CONFIRMED' | 'NO_FAULT_FOUND'

/** An Inspection's outcome as Staff choose it; a refusing one gives nothing back and needs a note. */
export interface OutcomeOption {
  outcome: InspectionOutcome
  label: string
  refusing: boolean
}

const outcomes: Record<RmaKind, readonly OutcomeOption[]> = {
  RETURN: [
    { outcome: 'ACCEPTED', label: 'Accepted', refusing: false },
    { outcome: 'REFUSED', label: 'Refused', refusing: true },
  ],
  WARRANTY_CLAIM: [
    { outcome: 'FAULT_CONFIRMED', label: 'Fault confirmed', refusing: false },
    { outcome: 'NO_FAULT_FOUND', label: 'No fault found', refusing: true },
  ],
}

const allOutcomes = [...outcomes.RETURN, ...outcomes.WARRANTY_CLAIM]

/** Whether an Inspection's outcome refuses the RMA: it gives nothing back, and needs a note. */
export function isRefusing(outcome: InspectionOutcome | undefined): boolean {
  return allOutcomes.some((option) => option.outcome === outcome && option.refusing)
}

/** The outcomes an Inspection of an RMA of that kind can have. */
export function outcomesOf(kind: RmaKind): readonly OutcomeOption[] {
  return outcomes[kind]
}

/** An Inspection's outcome as Staff read it. */
export function outcomeLabel(outcome: InspectionOutcome): string {
  return allOutcomes.find((option) => option.outcome === outcome)!.label
}

/** The body of `POST /rmas/{id}/inspect`. */
export interface InspectBody {
  outcome: InspectionOutcome
  note?: string
}

/**
 * The body of `POST /rmas/{id}/inspect` for what Staff chose and typed, or why it can't be sent, by
 * field: an outcome, and a note of at most 1,000 characters, which a refusing outcome needs.
 */
export function inspectionOf(
  outcome: InspectionOutcome | undefined,
  note: string,
): { body: InspectBody } | { errors: Record<string, string> } {
  if (!outcome) return { errors: { outcome: 'Choose what the inspection found.' } }
  const trimmed = note.trim()
  if (trimmed.length > maxText) return { errors: { note: 'Keep it to 1,000 characters.' } }
  if (isRefusing(outcome) && !trimmed) return { errors: { note: 'Say why: a refusing outcome needs a note.' } }
  return { body: trimmed ? { outcome, note: trimmed } : { outcome } }
}

/**
 * How an inspected RMA is resolved, as its page shows it: the returns Saga still at work; a
 * replacement Shipment, which it creates on a confirmed fault before the RMA is exchanged, so its ID
 * is only known from then; a Refund with its gateway reference; closed with the Inspection's note;
 * or held with the reason.
 */
export type ResolutionView =
  | { kind: 'resolving' }
  | { kind: 'replacement'; shipmentId?: string }
  | { kind: 'refund'; amount?: Money; reference?: string }
  | { kind: 'closed'; note?: string }
  | { kind: 'hold'; reason?: string }

/** An RMA's resolution, or none before its Inspection, or for one rejected or expired. */
export function resolutionOf(rma: Rma): ResolutionView | undefined {
  switch (rma.status) {
    case 'INSPECTED':
      return rma.inspection?.outcome === 'FAULT_CONFIRMED' ? { kind: 'replacement' } : { kind: 'resolving' }
    case 'EXCHANGED':
      return { kind: 'replacement', shipmentId: rma.resolution?.replacementShipmentId }
    case 'REFUNDED':
      return { kind: 'refund', amount: rma.refundAmount, reference: rma.resolution?.paymentTransactionReference }
    case 'CLOSED':
      return { kind: 'closed', note: rma.inspection?.note }
    case 'ON_HOLD':
      return { kind: 'hold', reason: rma.history.findLast((entry) => entry.status === 'ON_HOLD')?.note }
    default:
      return undefined
  }
}
