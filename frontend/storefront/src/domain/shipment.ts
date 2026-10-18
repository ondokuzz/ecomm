import type { Order } from './order'

/**
 * A Shipment's status in Fulfillment & Shipping: picked and packed in a Warehouse, then handed to
 * the Carrier and delivered. `ON_HOLD` is a packed Shipment whose Payment couldn't be captured.
 */
export type ShipmentStatus = 'AWAITING_PICK' | 'PICKED' | 'PACKED' | 'ON_HOLD' | 'SHIPPED' | 'DELIVERED'

/**
 * A Shipment's status as the Customer reads it: the Warehouse's steps are all "Being prepared",
 * then "Shipped" and "Delivered".
 */
export function shipmentStatusLabel(status: ShipmentStatus): string {
  switch (status) {
    case 'SHIPPED':
      return 'Shipped'
    case 'DELIVERED':
      return 'Delivered'
    default:
      return 'Being prepared'
  }
}

/** What the Carrier reported about a Shipment on its way, in a Carrier webhook. */
export type TrackingStatus = 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED'

/** A Tracking event: what the Carrier reported, and when it happened. */
export interface TrackingEvent {
  eventId: string
  status: TrackingStatus
  occurredAt: string
}

/**
 * An Order's Shipment, as Fulfillment & Shipping shows it to its Customer: the parts the Order page
 * shows. The carrier and tracking number are null until the Shipment is dispatched.
 */
export interface Shipment {
  id: string
  orderId: string
  status: ShipmentStatus
  carrier: string | null
  trackingNumber: string | null
  trackingEvents: TrackingEvent[]
  deliveredAt: string | null
}

const trackingLabels: Record<TrackingStatus, string> = {
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
}

/** One Tracking event as the Customer reads it. */
export interface TrackingRow {
  id: string
  label: string
  at: string
}

/**
 * How long Orchestration's fulfillment Saga awaits delivery once an Order is Shipped
 * (`ecomm.orchestration.delivery-deadline`). Past it the Saga ends `DELIVERY_UNCONFIRMED`, and the
 * Order and its Shipment stay `Shipped` for good.
 */
export const deliveryDeadlineMs = 30 * 24 * 60 * 60 * 1000

const beingPrepared: ReadonlySet<ShipmentStatus> = new Set(['AWAITING_PICK', 'PICKED', 'PACKED'])

/**
 * How often the Order page asks again for its Order and Shipment, in milliseconds: every 10 seconds
 * while the Shipment is being prepared (so "Shipped" shows without a reload) and while either is
 * `Shipped` (as Tracking events arrive). It stops once the fulfillment Saga's outcome is final, as
 * far as the Storefront can see it: both `Delivered`, the Shipment `On hold` (its Payment couldn't be
 * captured), or the Order `Shipped` for longer than the delivery deadline (delivery unconfirmed).
 * Either may be read as delivered first (the Saga marks the Order delivered moments after the
 * Carrier's webhook), so one stopping alone would leave the other stale. Each is `undefined` until
 * it is read (or when the Order has no Shipment); an unshipped Order without a Shipment isn't asked
 * again, since an Order placed before Shipping never gets one.
 */
export function trackingRefreshInterval(
  order: Pick<Order, 'status' | 'statusHistory'> | undefined,
  shipmentStatus: ShipmentStatus | undefined,
  now: number = Date.now(),
): number | false {
  if (shipmentStatus === 'ON_HOLD') return false
  if (shipmentStatus !== undefined && beingPrepared.has(shipmentStatus)) return 10_000
  if (order?.status !== 'SHIPPED' && shipmentStatus !== 'SHIPPED') return false
  const shippedAt = order?.statusHistory.findLast((entry) => entry.status === 'SHIPPED')?.at
  if (shippedAt !== undefined && now - Date.parse(shippedAt) > deliveryDeadlineMs) return false
  return 10_000
}

/** A Shipment's Tracking events, oldest first, in words. */
export function trackingEventsOf(shipment: Pick<Shipment, 'trackingEvents'>): TrackingRow[] {
  return [...shipment.trackingEvents]
    .sort((a, b) => Date.parse(a.occurredAt) - Date.parse(b.occurredAt))
    .map((event) => ({ id: event.eventId, label: trackingLabels[event.status], at: event.occurredAt }))
}
