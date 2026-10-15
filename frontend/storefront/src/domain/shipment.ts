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
