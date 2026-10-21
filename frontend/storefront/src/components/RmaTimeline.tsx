import { type Rma, type RmaStatus, rmaTimelineOf } from '../domain/rma'
import { Icon, type IconName } from './ui/Icon'
import { cx } from './ui/cx'

/** A status that ends an RMA without giving anything back, shown in its own colour and icon. */
const finalIcons: Partial<Record<RmaStatus, IconName>> = {
  REJECTED: 'close',
  EXPIRED: 'close',
  CLOSED: 'close',
  ON_HOLD: 'alert',
}

/**
 * An RMA's timeline, down the card: each step from its RMA history with when and its note, then
 * the steps still to come.
 */
export function RmaTimeline({ rma }: { rma: Rma }) {
  return (
    <ol className="timeline rma-timeline" aria-label="RMA progress">
      {rmaTimelineOf(rma).map((step, i) => {
        const finalIcon = finalIcons[step.status]
        return (
          <li
            key={`${step.status}-${i}`}
            className={cx('timeline-step', `timeline-${step.state}`, finalIcon && 'timeline-final timeline-cancelled')}
            aria-current={step.state === 'current' ? 'step' : undefined}
          >
            <span className="timeline-marker" aria-hidden="true">
              {finalIcon ? (
                <Icon name={finalIcon} size={14} />
              ) : step.state === 'upcoming' ? (
                i + 1
              ) : (
                <Icon name="check" size={14} />
              )}
            </span>
            <span className="timeline-label">
              <span className="timeline-status">
                {step.label}
                {step.state === 'done' && <span className="visually-hidden"> (done)</span>}
              </span>{' '}
              {step.at && (
                <time className="timeline-time" dateTime={step.at}>
                  {new Date(step.at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
                </time>
              )}
              {step.note && <span className="timeline-note">“{step.note}”</span>}
            </span>
          </li>
        )
      })}
    </ol>
  )
}
