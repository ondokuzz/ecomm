import { useId } from 'react'
import { useShipment } from '../api/shipments'
import { type Shipment, type ShipmentStatus, shipmentStatusLabel, trackingEventsOf } from '../domain/shipment'
import { Badge, type Tone } from './ui/Badge'
import { Card } from './ui/Card'

const shipmentStatusTones: Record<ShipmentStatus, Tone> = {
  AWAITING_PICK: 'info',
  PICKED: 'info',
  PACKED: 'info',
  ON_HOLD: 'info',
  SHIPPED: 'accent',
  DELIVERED: 'success',
}

/**
 * The Order's Shipment, as `ShipmentPanel` shows it. Like "Customers also bought", nothing shows
 * while it loads, for an Order without a Shipment, or when Fulfillment & Shipping can't be asked.
 * It is asked again every 10 s while it or the Order is `Shipped`.
 */
export function OrderShipment({ orderId }: { orderId: string }) {
  const shipment = useShipment(orderId).data
  if (!shipment) return null
  return <ShipmentPanel shipment={shipment} title="Shipment" />
}

/**
 * A Shipment under `title`: whether it is being prepared, shipped or delivered, the Carrier and
 * tracking number once it ships, and its Tracking events, oldest first. The Order page shows its
 * Order's, and an RMA's page its Replacement Shipment.
 */
export function ShipmentPanel({ shipment, title }: { shipment: Shipment; title: string }) {
  const headingId = useId()
  const tracking = trackingEventsOf(shipment)

  return (
    <Card className="order-shipment" role="region" aria-labelledby={headingId}>
      <div className="order-shipment-top">
        <h2 id={headingId} className="card-title">
          {title}
        </h2>
        {/* The smoke test finds the Shipment's status by its `shipment-*` class. */}
        <Badge
          tone={shipmentStatusTones[shipment.status]}
          dot
          className={`shipment-${shipment.status.toLowerCase().replaceAll('_', '-')}`}
        >
          {shipmentStatusLabel(shipment.status)}
        </Badge>
      </div>
      {shipment.carrier && shipment.trackingNumber && (
        <p className="order-shipment-carrier">
          {shipment.carrier}, tracking number <code>{shipment.trackingNumber}</code>
        </p>
      )}
      {tracking.length > 0 && (
        <ol className="order-shipment-tracking" aria-label="Tracking events">
          {tracking.map((row) => (
            <li key={row.id}>
              <span>{row.label}</span>
              <time className="muted" dateTime={row.at}>
                {new Date(row.at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
              </time>
            </li>
          ))}
        </ol>
      )}
    </Card>
  )
}
