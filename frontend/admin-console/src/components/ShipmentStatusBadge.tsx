import { type ShipmentStatus, shipmentStatusLabel } from '../domain/shipment'
import { Badge, type Tone } from './ui/Badge'

const tones: Record<ShipmentStatus, Tone> = {
  AWAITING_PICK: 'warning',
  PICKED: 'info',
  PACKED: 'primary',
  SHIPPED: 'success',
  DELIVERED: 'success',
  ON_HOLD: 'danger',
}

/** A Shipment status as a pill: Staff's to work, with the Carrier, or held. */
export function ShipmentStatusBadge({ status }: { status: ShipmentStatus }) {
  return (
    <Badge tone={tones[status]} dot>
      {shipmentStatusLabel(status)}
    </Badge>
  )
}
