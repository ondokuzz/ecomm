import type { Money } from './money'

/** How each unit of a Variant is identified when it ships, as Catalog's Serial tracking says. */
export type SerialTracking = 'NONE' | 'SERIAL' | 'IMEI'

/**
 * Where a Shipment is: `AWAITING_PICK → PICKED → PACKED → SHIPPED → DELIVERED`, or from `PACKED` to
 * `ON_HOLD` when its Payment couldn't be captured, which is final.
 */
export type ShipmentStatus = 'AWAITING_PICK' | 'PICKED' | 'PACKED' | 'SHIPPED' | 'DELIVERED' | 'ON_HOLD'

/** What a Tracking event from the Carrier says of the parcel. */
export type TrackingStatus = 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED'

/** Where a Shipment goes, as its Order was placed with it. */
export interface ShippingAddress {
  recipientName: string
  line1: string
  line2?: string | null
  postcode: string
  city: string
  region?: string | null
  /** ISO 3166-1 alpha-2, such as `DE`. */
  country: string
  phone?: string | null
}

/** One line of a Shipment: a Variant, its Product's name, how many, and their Serial numbers once packed. */
export interface ShipmentLine extends PackLine {
  productName: string
  serialNumbers: string[]
}

/** One Shipment status change: when, and by whom (a Staff member's ID, `ORCHESTRATION` or `CARRIER`). */
export interface ShipmentHistoryEntry {
  status: ShipmentStatus
  at: string
  by: string
}

/** One Order's parcel, as Fulfillment & Shipping shows it to Staff. */
export interface Shipment {
  id: string
  orderId: string
  customerId: string
  warehouseId: string
  status: ShipmentStatus
  shippingAddress: ShippingAddress
  shippingOption: { id: string; carrier: string; cost: Money; estimatedDays?: number | null }
  lines: ShipmentLine[]
  /** The Carrier and tracking number, once it is dispatched. */
  carrier: string | null
  trackingNumber: string | null
  /** Oldest first. */
  trackingEvents: { eventId: string; status: TrackingStatus; occurredAt: string }[]
  deliveredAt: string | null
  /** Why it is on hold: the payment gateway's reason for refusing the capture. */
  holdReason: string | null
  /** Oldest first. */
  statusHistory: ShipmentHistoryEntry[]
  /** When the fulfillment Saga created it, as soon as its Order was paid. */
  createdAt: string
}

/** A Warehouse a Shipment ships from, as Staff read the configured ones. */
export interface Warehouse {
  id: string
  name: string
  default: boolean
}

const statusLabels: Record<ShipmentStatus, string> = {
  AWAITING_PICK: 'Awaiting pick',
  PICKED: 'Picked',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  ON_HOLD: 'On hold',
}

/** A Shipment status as Staff read it: "Awaiting pick", "On hold". */
export function shipmentStatusLabel(status: ShipmentStatus): string {
  return statusLabels[status]
}

/** A Tracking event's status as Staff read it: "In transit", "Out for delivery", "Delivered". */
export function trackingStatusLabel(status: TrackingStatus): string {
  const words = status.toLowerCase().replaceAll('_', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/**
 * A Serial number as Staff type it, checked as Fulfillment & Shipping will: valid, with the value
 * it keeps; still being typed; or wrong already, whatever comes next.
 */
export type SerialCheck =
  | { state: 'valid'; serialNumber: string }
  | { state: 'incomplete'; message: string }
  | { state: 'invalid'; message: string }

const imeiLength = 15
const serialForm = /^[A-Za-z0-9-]*$/
const serialMin = 4
const serialMax = 40

/**
 * Checks one unit's Serial number as it is typed or scanned. An IMEI is 15 digits, the last a Luhn
 * check digit over the other 14; a serial number is 4 to 40 letters, digits and hyphens, kept
 * upper-cased. Something that can't become one is invalid at once; something too short is only
 * incomplete, which a Pack refuses but typing doesn't flag.
 */
export function serialCheckOf(tracking: Exclude<SerialTracking, 'NONE'>, entered: string): SerialCheck {
  const typed = entered.trim()
  if (tracking === 'IMEI') {
    if (!/^\d*$/.test(typed) || typed.length > imeiLength) return { state: 'invalid', message: 'An IMEI is 15 digits.' }
    if (typed.length < imeiLength) return { state: 'incomplete', message: 'Enter the IMEI: 15 digits.' }
    if (!luhn(typed)) {
      return { state: 'invalid', message: 'Not a valid IMEI: its check digit is wrong. Check for a typo.' }
    }
    return { state: 'valid', serialNumber: typed }
  }
  if (!serialForm.test(typed) || typed.length > serialMax) {
    return { state: 'invalid', message: 'A serial number is 4 to 40 letters, digits and hyphens.' }
  }
  if (typed.length < serialMin) return { state: 'incomplete', message: 'Enter the serial number.' }
  return { state: 'valid', serialNumber: typed.toUpperCase() }
}

/** The statuses the fulfillment queue has a tab for: the Shipments still in Staff's hands, or held. */
export type QueuedStatus = Extract<ShipmentStatus, 'AWAITING_PICK' | 'PICKED' | 'PACKED' | 'ON_HOLD'>

/** The fulfillment queue's tabs, in the order a Shipment moves through them. */
export const queuedStatuses: readonly QueuedStatus[] = ['AWAITING_PICK', 'PICKED', 'PACKED', 'ON_HOLD']

/** How many Shipments a page of the queue shows. */
export const shipmentsPageSize = 20

/** The fulfillment queue's tab and its page, counting from 0. */
export interface Queue {
  status: QueuedStatus
  page: number
}

/** The queue a URL's query names: its `status` (awaiting pick unless another tab is named), and its `page`, from 1. */
export function queueOf(params: URLSearchParams): Queue {
  const status = params.get('status') as QueuedStatus
  const page = Number(params.get('page'))
  return {
    status: queuedStatuses.includes(status) ? status : 'AWAITING_PICK',
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
  }
}

/** The URL query that keeps the queue: the inverse of `queueOf`, leaving out the defaults. */
export function queueSearchOf(queue: Queue): string {
  const params = new URLSearchParams()
  if (queue.status !== 'AWAITING_PICK') params.set('status', queue.status)
  if (queue.page > 0) params.set('page', String(queue.page + 1))
  const search = params.toString()
  return search && `?${search}`
}

/**
 * The query for `GET /shipments` that a tab stands for, from one Warehouse or, without one, from
 * all; Fulfillment & Shipping lists them oldest first.
 */
export function shipmentsQueryOf(queue: Queue, warehouseId: string | undefined, size: number): string {
  const params = new URLSearchParams({ status: queue.status, page: String(queue.page), size: String(size) })
  if (warehouseId) params.set('warehouseId', warehouseId)
  return `?${params}`
}

/** How many units a Shipment holds, over all its lines. */
export function unitsOf(shipment: { lines: readonly { quantity: number }[] }): number {
  return shipment.lines.reduce((units, line) => units + line.quantity, 0)
}

/** What a Pack needs of a Shipment line: its Variant, how many units, and how each is identified. */
export interface PackLine {
  variantId: string
  quantity: number
  serialTracking: SerialTracking
}

/** The body of `POST /shipments/{id}/pack`: the Serial numbers of each serial-tracked line, by Variant ID. */
export interface PackRequest {
  serialNumbers: Record<string, string[]>
}

/**
 * The name of one unit's Serial number input, as Fulfillment & Shipping names it in a violation,
 * such as `serialNumbers.PHN-1[0]`, so the server's violations land on the same input.
 */
export function serialFieldOf(variantId: string, unit: number): string {
  return `serialNumbers.${variantId}[${unit}]`
}

/**
 * The Pack request for what Staff typed, one Serial number per unit of each serial-tracked line, or
 * each unit's problem by its field: empty, half typed, wrong, or entered twice in the Shipment.
 */
export function packRequestOf(
  lines: readonly PackLine[],
  entered: Readonly<Record<string, readonly string[]>>,
): { body: PackRequest } | { errors: Record<string, string> } {
  const serialNumbers: Record<string, string[]> = {}
  const errors: Record<string, string> = {}
  const seen = new Set<string>()
  for (const line of lines) {
    if (line.serialTracking === 'NONE') continue
    const kept: string[] = []
    for (let unit = 0; unit < line.quantity; unit++) {
      const field = serialFieldOf(line.variantId, unit)
      const check = serialCheckOf(line.serialTracking, entered[line.variantId]?.[unit] ?? '')
      if (check.state !== 'valid') errors[field] = check.message
      else if (seen.has(check.serialNumber)) errors[field] = 'Entered twice in this Shipment.'
      else {
        seen.add(check.serialNumber)
        kept.push(check.serialNumber)
      }
    }
    serialNumbers[line.variantId] = kept
  }
  return Object.keys(errors).length > 0 ? { errors } : { body: { serialNumbers } }
}

/** Whether the last digit is the Luhn check digit over the others. */
function luhn(digits: string): boolean {
  let sum = 0
  for (let i = 0; i < digits.length; i++) {
    let digit = Number(digits[digits.length - 1 - i])
    if (i % 2 === 1) {
      digit *= 2
      if (digit > 9) digit -= 9
    }
    sum += digit
  }
  return sum % 10 === 0
}
