import { describe, expect, it } from 'vitest'
import type { Order, OrderStatus, StatusHistoryEntry } from './order'
import { type TrackingStatus, shipmentStatusLabel, trackingEventsOf, trackingRefreshInterval } from './shipment'

describe('shipmentStatusLabel', () => {
  it('says "Being prepared" until the Shipment leaves the Warehouse', () => {
    expect(shipmentStatusLabel('AWAITING_PICK')).toBe('Being prepared')
    expect(shipmentStatusLabel('PICKED')).toBe('Being prepared')
    expect(shipmentStatusLabel('PACKED')).toBe('Being prepared')
    expect(shipmentStatusLabel('ON_HOLD')).toBe('Being prepared')
  })

  it('says "Shipped", then "Delivered"', () => {
    expect(shipmentStatusLabel('SHIPPED')).toBe('Shipped')
    expect(shipmentStatusLabel('DELIVERED')).toBe('Delivered')
  })
})

describe('trackingEventsOf', () => {
  const shipment = (trackingEvents: { eventId: string; status: TrackingStatus; occurredAt: string }[]) => ({
    trackingEvents,
  })

  it('lists the Tracking events oldest first, in words, with when', () => {
    expect(
      trackingEventsOf(
        shipment([
          { eventId: 'e3', status: 'DELIVERED', occurredAt: '2026-10-15T10:00:30Z' },
          { eventId: 'e1', status: 'IN_TRANSIT', occurredAt: '2026-10-15T10:00:10Z' },
          { eventId: 'e2', status: 'OUT_FOR_DELIVERY', occurredAt: '2026-10-15T10:00:20Z' },
        ]),
      ),
    ).toEqual([
      { id: 'e1', label: 'In transit', at: '2026-10-15T10:00:10Z' },
      { id: 'e2', label: 'Out for delivery', at: '2026-10-15T10:00:20Z' },
      { id: 'e3', label: 'Delivered', at: '2026-10-15T10:00:30Z' },
    ])
  })

  it('has none before the Carrier reports one', () => {
    expect(trackingEventsOf(shipment([]))).toEqual([])
  })
})

describe('trackingRefreshInterval', () => {
  const now = Date.parse('2026-10-18T12:00:00Z')
  const day = 24 * 60 * 60 * 1000
  const entry = (status: OrderStatus, msAgo: number): StatusHistoryEntry => ({
    status,
    at: new Date(now - msAgo).toISOString(),
    changedBy: 'ORCHESTRATION',
    backfilled: false,
  })
  /** An Order that went through `statuses` a minute apart, the last one `msAgo` ago. */
  const order = (msAgo: number, ...statuses: OrderStatus[]): Pick<Order, 'status' | 'statusHistory'> => ({
    status: statuses[statuses.length - 1],
    statusHistory: statuses.map((status, i) => entry(status, msAgo + (statuses.length - 1 - i) * 60_000)),
  })
  const paid = order(60_000, 'PLACED', 'PAID')
  const fulfilled = order(60_000, 'PLACED', 'PAID', 'FULFILLED')
  const shipped = (msAgo: number) => order(msAgo, 'PLACED', 'PAID', 'FULFILLED', 'SHIPPED')
  const delivered = order(60_000, 'PLACED', 'PAID', 'FULFILLED', 'SHIPPED', 'DELIVERED')

  it('refreshes every 10 seconds while the Shipment is being prepared, so "Shipped" shows', () => {
    expect(trackingRefreshInterval(paid, 'AWAITING_PICK', now)).toBe(10_000)
    expect(trackingRefreshInterval(paid, 'PICKED', now)).toBe(10_000)
    expect(trackingRefreshInterval(paid, 'PACKED', now)).toBe(10_000)
    expect(trackingRefreshInterval(fulfilled, 'PACKED', now)).toBe(10_000)
  })

  it('refreshes every 10 seconds while the Order is Shipped', () => {
    expect(trackingRefreshInterval(shipped(day), 'SHIPPED', now)).toBe(10_000)
    expect(trackingRefreshInterval(shipped(day), undefined, now)).toBe(10_000)
    expect(trackingRefreshInterval(fulfilled, 'SHIPPED', now)).toBe(10_000)
  })

  it('keeps refreshing while either one is still Shipped, since either may hear of the delivery first', () => {
    expect(trackingRefreshInterval(delivered, 'SHIPPED', now)).toBe(10_000)
    expect(trackingRefreshInterval(shipped(day), 'DELIVERED', now)).toBe(10_000)
  })

  it('stops once both are Delivered', () => {
    expect(trackingRefreshInterval(delivered, 'DELIVERED', now)).toBe(false)
    expect(trackingRefreshInterval(delivered, undefined, now)).toBe(false)
  })

  it('stops once the Shipment is On hold, since its Payment could not be captured', () => {
    expect(trackingRefreshInterval(paid, 'ON_HOLD', now)).toBe(false)
  })

  it('stops once the Order has been Shipped longer than the delivery deadline, when delivery is unconfirmed', () => {
    expect(trackingRefreshInterval(shipped(29 * day), 'SHIPPED', now)).toBe(10_000)
    expect(trackingRefreshInterval(shipped(30 * day + 1), 'SHIPPED', now)).toBe(false)
    expect(trackingRefreshInterval(shipped(31 * day), undefined, now)).toBe(false)
  })

  it('does not refresh an unshipped Order without a Shipment, or before either is read', () => {
    expect(trackingRefreshInterval(paid, undefined, now)).toBe(false)
    expect(trackingRefreshInterval(order(60_000, 'PLACED', 'CANCELLED'), undefined, now)).toBe(false)
    expect(trackingRefreshInterval(undefined, undefined, now)).toBe(false)
  })
})
