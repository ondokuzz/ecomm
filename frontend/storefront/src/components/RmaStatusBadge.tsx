import { type RmaStatus, rmaStatusLabel } from '../domain/rma'
import { Badge, type Tone } from './ui/Badge'

/** Each RMA status in a colour of its own; its badge always says it too, so colour is never the only cue. */
const rmaStatusTones: Record<RmaStatus, Tone> = {
  REQUESTED: 'neutral',
  APPROVED: 'info',
  REJECTED: 'danger',
  RECEIVED: 'primary',
  EXPIRED: 'danger',
  INSPECTED: 'primary',
  REFUNDED: 'success',
  EXCHANGED: 'success',
  CLOSED: 'neutral',
  ON_HOLD: 'warning',
}

export function RmaStatusBadge({ status }: { status: RmaStatus }) {
  return (
    // The smoke test finds the RMA's status by its `rma-*` class.
    <Badge tone={rmaStatusTones[status]} dot className={`rma-${status.toLowerCase().replaceAll('_', '-')}`}>
      {rmaStatusLabel(status)}
    </Badge>
  )
}
