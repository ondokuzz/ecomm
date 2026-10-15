import { describe, expect, it } from 'vitest'
import { shipmentStatusLabel } from './shipment'

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
