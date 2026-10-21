import type { Money } from './money'

/** What an RMA claims: a return within the Return Period, or a warranty claim within the Warranty Window. */
export type RmaKind = 'RETURN' | 'WARRANTY_CLAIM'

/** An RMA's kind as the Customer reads it. */
export function rmaKindLabel(kind: RmaKind): 'Return' | 'Warranty claim' {
  return kind === 'RETURN' ? 'Return' : 'Warranty claim'
}

/**
 * One delivered line of an Order, as Returns & Warranty answers `GET /rmas/eligibility`: when its
 * Return Period ends, when its Warranty Window ends (absent when its Product has none), and the
 * Serial numbers and quantity still free to claim. Either window may have ended already.
 */
export interface LineEligibility {
  variantId: string
  productName?: string
  returnPeriodEndsAt: string
  warrantyWindowEndsAt?: string
  /** One per unit still free to claim, for a serial-tracked Variant; empty otherwise. */
  serialNumbers: string[]
  quantity: number
}

/** `GET /rmas/eligibility?orderId=`: each line the Customer's delivered Order delivered. */
export interface OrderEligibility {
  orderId: string
  lines: LineEligibility[]
}

/** A kind of RMA as the request form offers it: until when it is open, or why it isn't. */
export interface KindOption {
  kind: RmaKind
  label: 'Return' | 'Warranty claim'
  /** When its window ends; absent for a Product without a warranty. */
  endsAt?: string
  available: boolean
  /** Why it isn't available. */
  reason?: string
}

const allClaimed = 'Every unit is already in a return or claim.'
const chooseUnits = 'Choose the units you’re sending back.'
const noWarranty = 'It has no manufacturer’s warranty.'
const returnPeriodEnded = (on: string) => `Your Return Period ended on ${on}.`
const warrantyEnded = (on: string) => `Its warranty ended on ${on}.`

/**
 * The kinds of RMA a delivered line offers at `now`: a return until its Return Period ends, and a
 * warranty claim until its Warranty Window does. One that ended, a warranty the Product doesn't
 * have, or a line whose units are all claimed, is offered disabled, with the reason, dates as
 * `formatDate` writes them.
 */
export function kindOptionsOf(
  line: LineEligibility,
  now: Date,
  formatDate: (at: string) => string = formatDay,
): KindOption[] {
  const option = (kind: RmaKind, endsAt: string | undefined, ended: (at: string) => string): KindOption => {
    const offered = { kind, label: rmaKindLabel(kind), ...(endsAt ? { endsAt } : {}) }
    if (line.quantity === 0) return { ...offered, available: false, reason: allClaimed }
    if (!endsAt) return { ...offered, available: false, reason: noWarranty }
    if (Date.parse(endsAt) <= now.getTime()) return { ...offered, available: false, reason: ended(formatDate(endsAt)) }
    return { ...offered, available: true }
  }
  return [
    option('RETURN', line.returnPeriodEndsAt, returnPeriodEnded),
    option('WARRANTY_CLAIM', line.warrantyWindowEndsAt, warrantyEnded),
  ]
}

/** A date as the Customer reads it, in their own locale and time zone. */
export function formatDay(at: string): string {
  return new Date(at).toLocaleDateString(undefined, { dateStyle: 'medium' })
}

/** What the Customer chose on the request form; the quantity as typed, for a Variant without Serial tracking. */
export interface RmaForm {
  kind: RmaKind | undefined
  serialNumbers: string[]
  quantity: string
  reason: string
}

/** The body of `POST /rmas`. */
export interface RmaRequestBody {
  orderId: string
  variantId: string
  kind: RmaKind
  quantity: number
  serialNumbers: string[]
  reason: string
}

/** The request form's fields, as its errors name them. */
export type RmaField = 'kind' | 'units' | 'reason'

/** The longest reason Returns & Warranty keeps. */
export const maxReason = 1000

/**
 * The body of `POST /rmas` for what the Customer chose on a line's form at `now`, or why it can't be
 * sent, by field: an available kind; units, as Serial numbers still free to claim or as a quantity
 * up to what is left; and a reason of at most 1,000 characters once trimmed.
 */
export function rmaRequestOf(
  orderId: string,
  line: LineEligibility,
  form: RmaForm,
  now: Date,
  formatDate: (at: string) => string = formatDay,
): { body: RmaRequestBody } | { errors: Partial<Record<RmaField, string>> } {
  const errors: Partial<Record<RmaField, string>> = {}
  const option = kindOptionsOf(line, now, formatDate).find((candidate) => candidate.kind === form.kind)
  if (!option) errors.kind = 'Choose a return or a warranty claim.'
  else if (!option.available) errors.kind = option.reason

  const serialTracked = line.serialNumbers.length > 0
  const quantity = serialTracked ? form.serialNumbers.length : Number(form.quantity.trim())
  if (serialTracked) {
    const free = form.serialNumbers.every((serial) => line.serialNumbers.includes(serial))
    if (quantity === 0 || !free) errors.units = chooseUnits
  } else if (!form.quantity.trim()) {
    errors.units = chooseUnits
  } else if (!Number.isInteger(quantity) || quantity < 1 || quantity > line.quantity) {
    errors.units = `Choose 1 to ${line.quantity} ${line.quantity === 1 ? 'unit' : 'units'}.`
  }

  const reason = form.reason.trim()
  if (!reason) errors.reason = 'Tell us what’s wrong.'
  else if (reason.length > maxReason) errors.reason = 'Keep it to 1,000 characters.'

  if (Object.keys(errors).length > 0) return { errors }
  return {
    body: {
      orderId,
      variantId: line.variantId,
      kind: form.kind!,
      quantity,
      serialNumbers: serialTracked ? form.serialNumbers : [],
      reason,
    },
  }
}

/** Where Returns & Warranty's refusal of a request goes on the form: beside a field, or above it. */
export type RmaRefusals = Partial<Record<RmaField | 'form', string>>

const catchingUp = 'We’re still catching up with this order. Try again in a moment.'

/**
 * Returns & Warranty's refusal of `POST /rmas`, by the field it is about: a window that ended, or a
 * warranty the Product doesn't have, beside the kind; units that aren't free to claim beside the
 * units, naming the Serial number; a malformed field (a 400 naming it) beside its input; and a 503
 * while its projections catch up with the Order, above the form. Undefined for any other failure,
 * which is shown as a whole.
 */
export function rmaRefusalsOf(
  status: number,
  problem: Record<string, unknown>,
  formatDate: (at: string) => string = formatDay,
): RmaRefusals | undefined {
  if (status === 503) return { form: catchingUp }
  if (status === 400) return malformedOf(problem.errors)
  if (status !== 422) return undefined
  const endedAt = typeof problem.endedAt === 'string' ? formatDate(problem.endedAt) : 'its end'
  switch (problem.reason) {
    case 'outsideReturnPeriod':
      return { kind: returnPeriodEnded(endedAt) }
    case 'outsideWarrantyWindow':
      return { kind: warrantyEnded(endedAt) }
    case 'noWarranty':
      return { kind: noWarranty }
    case 'quantityExceeded': {
      const available = Number(problem.available)
      if (!(available > 0)) return { units: allClaimed }
      return { units: `Only ${available} ${available === 1 ? 'unit is' : 'units are'} left to claim.` }
    }
    case 'serialAlreadyClaimed':
      return { units: `${String(problem.serialNumber)} is already in another return or claim.` }
    case 'serialNotShipped':
      return { units: `${String(problem.serialNumber)} wasn’t shipped to you on this order.` }
    case 'serialNumbersMiscounted':
      return { units: chooseUnits }
    default:
      return undefined
  }
}

/** A 400's malformed fields, as Returns & Warranty names them in `errors`, by the form's fields. */
function malformedOf(errors: unknown): RmaRefusals | undefined {
  if (!Array.isArray(errors)) return undefined
  const refusals: RmaRefusals = {}
  for (const error of errors as { field?: unknown }[]) {
    const field = String(error?.field ?? '')
    if (field === 'kind') refusals.kind = 'Choose a return or a warranty claim.'
    else if (field === 'reason') refusals.reason = 'Tell us what’s wrong, in at most 1,000 characters.'
    else if (field === 'quantity' || field.startsWith('serialNumbers')) refusals.units = chooseUnits
  }
  return Object.keys(refusals).length > 0 ? refusals : undefined
}

/**
 * An RMA's status: requested, approved or rejected by Staff, then its units received or the RMA
 * expired, inspected, and refunded, exchanged for a replacement, closed or put on hold.
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

/** What an Inspection found: a return accepted or refused, or a warranty claim's fault confirmed or not. */
export type InspectionOutcome = 'ACCEPTED' | 'REFUSED' | 'FAULT_CONFIRMED' | 'NO_FAULT_FOUND'

/** One step of an RMA's history: the status it moved to, who moved it, when, and its note. */
export interface RmaHistoryEntry {
  status: RmaStatus
  by: string
  at: string
  note?: string
}

/** An RMA as Returns & Warranty shows it to its Customer. */
export interface Rma {
  id: string
  /** `RMA-` and the start of its ID, for the parcel. */
  reference: string
  orderId: string
  variantId: string
  productName?: string
  quantity: number
  /** One per unit, for a serial-tracked Variant; empty otherwise. */
  serialNumbers: string[]
  kind: RmaKind
  reason: string
  /** What refunding all its units gives back; absent until Returns & Warranty knows its Order. */
  refundAmount?: Money
  status: RmaStatus
  requestedAt: string
  /** Its Send-back deadline, once approved. */
  sendBackBy?: string
  /** The Warehouse its units go back to, once approved. */
  warehouseId?: string
  inspection?: { outcome: InspectionOutcome; note?: string }
  /** Once resolved: the Refund's reference at the payment gateway, or the replacement Shipment. */
  resolution?: { paymentTransactionReference?: string; replacementShipmentId?: string }
  /** Oldest first. One requested before Sprint 6 has no entry for its request. */
  history: RmaHistoryEntry[]
}

const statusLabels: Record<RmaStatus, string> = {
  REQUESTED: 'Requested',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  RECEIVED: 'Received',
  EXPIRED: 'Expired',
  INSPECTED: 'Inspected',
  REFUNDED: 'Refunded',
  EXCHANGED: 'Replacement sent',
  CLOSED: 'Closed',
  ON_HOLD: 'On hold',
}

/** An RMA status as the Customer reads it. */
export function rmaStatusLabel(status: RmaStatus): string {
  return statusLabels[status]
}

const outcomeLabels: Record<InspectionOutcome, string> = {
  ACCEPTED: 'accepted',
  REFUSED: 'refused',
  FAULT_CONFIRMED: 'fault confirmed',
  NO_FAULT_FOUND: 'no fault found',
}

/** One step of an RMA's timeline. */
export interface RmaTimelineStep {
  status: RmaStatus
  label: string
  state: 'done' | 'current' | 'upcoming'
  /** When it was taken; absent for a step still to come. */
  at?: string
  /** An approval's note, a rejection's reason, an Inspection's note, or a hold's reason. */
  note?: string
}

const pathTo: Record<RmaKind, RmaStatus[]> = {
  RETURN: ['REQUESTED', 'APPROVED', 'RECEIVED', 'INSPECTED', 'REFUNDED'],
  WARRANTY_CLAIM: ['REQUESTED', 'APPROVED', 'RECEIVED', 'INSPECTED', 'EXCHANGED'],
}

/**
 * An RMA's timeline, from its RMA history, oldest first, with each step's note and, for its
 * Inspection, the outcome; then, while it is on its way, the steps still to come, up to the Refund
 * for a return or the replacement for a warranty claim. A final status ends it. An RMA requested
 * before the RMA history starts with its request all the same.
 */
export function rmaTimelineOf(rma: Pick<Rma, 'kind' | 'requestedAt' | 'history' | 'inspection'>): RmaTimelineStep[] {
  const history: RmaHistoryEntry[] =
    rma.history[0]?.status === 'REQUESTED'
      ? rma.history
      : [{ status: 'REQUESTED', by: 'CUSTOMER', at: rma.requestedAt }, ...rma.history]
  const past = history.map((entry, i): RmaTimelineStep => {
    const outcome = entry.status === 'INSPECTED' && rma.inspection ? `: ${outcomeLabels[rma.inspection.outcome]}` : ''
    const step: RmaTimelineStep = {
      status: entry.status,
      label: rmaStatusLabel(entry.status) + outcome,
      state: i === history.length - 1 ? 'current' : 'done',
      at: entry.at,
    }
    return entry.note ? { ...step, note: entry.note } : step
  })
  const path = pathTo[rma.kind]
  const reached = path.indexOf(history[history.length - 1].status)
  const upcoming: RmaTimelineStep[] =
    reached === -1 || reached === path.length - 1
      ? []
      : path.slice(reached + 1).map((status) => ({ status, label: rmaStatusLabel(status), state: 'upcoming' }))
  return [...past, ...upcoming]
}

const beingResolved: ReadonlySet<RmaStatus> = new Set(['RECEIVED', 'INSPECTED'])

/**
 * How often an RMA's page asks again, in milliseconds: every 10 seconds while its units are received
 * or inspected, as Staff and the returns Saga move it on, and not otherwise.
 */
export function rmaRefreshInterval(status: RmaStatus | undefined): number | false {
  return status !== undefined && beingResolved.has(status) ? 10_000 : false
}

/** Where an RMA's units go back to: a Warehouse's name and its Return address, from Fulfillment & Shipping. */
export interface ReturnWarehouse {
  id: string
  name: string
  returnAddress: { name: string; street: string; postcode: string; city: string; country: string }
}

/** A Return address as the parcel's label shows it, one line each. */
export function returnAddressLines({ returnAddress: address }: ReturnWarehouse): string[] {
  return [address.name, address.street, `${address.postcode} ${address.city}`, address.country]
}

/** How many RMAs a page of the Customer's Returns page shows. */
export const rmasPageSize = 20
