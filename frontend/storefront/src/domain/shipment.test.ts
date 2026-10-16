import { describe, expect, it } from 'vitest'
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
  it('refreshes every 10 seconds while the Order is Shipped', () => {
    expect(trackingRefreshInterval('SHIPPED', 'SHIPPED')).toBe(10_000)
    expect(trackingRefreshInterval('SHIPPED', undefined)).toBe(10_000)
  })

  it('keeps refreshing while either one is still Shipped, since either may hear of the delivery first', () => {
    expect(trackingRefreshInterval('DELIVERED', 'SHIPPED')).toBe(10_000)
    expect(trackingRefreshInterval('SHIPPED', 'DELIVERED')).toBe(10_000)
  })

  it('stops once both are Delivered, and does not refresh before the Order ships', () => {
    expect(trackingRefreshInterval('DELIVERED', 'DELIVERED')).toBe(false)
    expect(trackingRefreshInterval('DELIVERED', undefined)).toBe(false)
    expect(trackingRefreshInterval('PAID', 'AWAITING_PICK')).toBe(false)
    expect(trackingRefreshInterval('FULFILLED', 'PACKED')).toBe(false)
    expect(trackingRefreshInterval(undefined, undefined)).toBe(false)
  })
})
