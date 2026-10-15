import type { OrderStatus } from './order'

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
 * How often the Order page asks again for its Order and Shipment, in milliseconds: every 10 seconds
 * while the Order is `Shipped`, as Tracking events arrive, and not at all before or once `Delivered`.
 */
export function trackingRefreshInterval(orderStatus: OrderStatus): number | false {
  return orderStatus === 'SHIPPED' ? 10_000 : false
}

/** A Shipment's Tracking events, oldest first, in words. */
export function trackingEventsOf(shipment: Pick<Shipment, 'trackingEvents'>): TrackingRow[] {
  return [...shipment.trackingEvents]
    .sort((a, b) => Date.parse(a.occurredAt) - Date.parse(b.occurredAt))
    .map((event) => ({ id: event.eventId, label: trackingLabels[event.status], at: event.occurredAt }))
}
