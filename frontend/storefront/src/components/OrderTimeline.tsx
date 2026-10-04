import { type Order, type OrderStatus, orderStatusLabel, orderTimeline } from '../domain/order'
import { Icon, type IconName } from './ui/Icon'
import { cx } from './ui/cx'

/** An Order Status that ends an Order off the main path, shown in its own colour and icon. */
const finalIcons: Partial<Record<OrderStatus, IconName>> = { CANCELLED: 'close', RETURNED: 'arrowLeft' }

/**
 * The Order Statuses an Order has been through, from its Order Status history, each with when it
 * happened; its current one; and those still to come.
 */
export function OrderTimeline({ order }: { order: Pick<Order, 'statusHistory'> }) {
  return (
    <ol className="timeline" aria-label="Order progress">
      {orderTimeline(order).map((step, i) => {
        const finalIcon = finalIcons[step.status]
        return (
          <li
            key={`${step.status}-${i}`}
            className={cx(
              'timeline-step',
              `timeline-${step.state}`,
              finalIcon && `timeline-final timeline-${step.status.toLowerCase()}`,
            )}
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
                {orderStatusLabel(step.status)}
                {step.state === 'done' && <span className="visually-hidden"> (done)</span>}
              </span>{' '}
              {step.at && (
                <time className="timeline-time" dateTime={step.at}>
                  {new Date(step.at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
                </time>
              )}
            </span>
          </li>
        )
      })}
    </ol>
  )
}
