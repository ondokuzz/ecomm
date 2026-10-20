import { type RmaStatus, rmaStatusLabel } from '../domain/rma'
import { Badge, type Tone } from './ui/Badge'

const tones: Record<RmaStatus, Tone> = {
  REQUESTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  RECEIVED: 'info',
  EXPIRED: 'neutral',
  INSPECTED: 'info',
  REFUNDED: 'success',
  EXCHANGED: 'success',
  CLOSED: 'neutral',
  ON_HOLD: 'danger',
}

/** An RMA status as a pill: awaiting Staff, under way, resolved, or stopped. */
export function RmaStatusBadge({ status }: { status: RmaStatus }) {
  return (
    <Badge tone={tones[status]} dot>
      {rmaStatusLabel(status)}
    </Badge>
  )
}
