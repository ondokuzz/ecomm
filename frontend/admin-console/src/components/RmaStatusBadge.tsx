import { type RmaStatus, rmaStatusLabel } from '../domain/rma'
import { Badge, type Tone } from './ui/Badge'

const tones: Record<RmaStatus, Tone> = {
  REQUESTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
}

/** An RMA status as a pill: awaiting a decision, approved, or rejected. */
export function RmaStatusBadge({ status }: { status: RmaStatus }) {
  return (
    <Badge tone={tones[status]} dot>
      {rmaStatusLabel(status)}
    </Badge>
  )
}
