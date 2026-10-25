import { type ReviewStatus, reviewStatusLabel } from '../domain/review'
import { Badge, type Tone } from './ui/Badge'

const tones: Record<ReviewStatus, Tone> = { PUBLISHED: 'success', HELD: 'warning', REMOVED: 'danger' }

/** A review's status as a pill: shown, waiting for Staff, or taken down. */
export function ReviewStatusBadge({ status }: { status: ReviewStatus }) {
  return (
    <Badge tone={tones[status]} dot>
      {reviewStatusLabel(status)}
    </Badge>
  )
}
