import type { UseQueryResult } from '@tanstack/react-query'
import type { Notification, NotificationStatus } from '../domain/notification'
import { formatTime } from '../domain/order'
import type { Page } from '../domain/paging'
import { channelLabel, messageTypeLabel, subjectLabel } from '../domain/template'
import { ErrorMessage } from './Status'
import { Badge, type Tone } from './ui/Badge'

const notificationTones: Record<NotificationStatus, Tone> = { PENDING: 'warning', SENT: 'success', FAILED: 'danger' }

/**
 * Every Notification sent about an Order or RMA, newest first: its Message type, channel, masked
 * recipient, status and time, with the message as rendered behind "Message". The table is labelled
 * by the heading whose ID is `labelledBy`.
 */
export function Notifications({
  notifications,
  labelledBy,
}: {
  notifications: UseQueryResult<Page<Notification>>
  labelledBy: string
}) {
  if (notifications.error) {
    return (
      <ErrorMessage
        error={notifications.error}
        title="Couldn't load the Notifications"
        retrying={notifications.isFetching}
        onRetry={() => notifications.refetch()}
      />
    )
  }
  if (!notifications.data) {
    return (
      <div aria-busy="true">
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  if (notifications.data.items.length === 0) return <p className="muted">No Notification was sent about it.</p>
  return (
    <div className="table-scroll">
      <table className="table" aria-labelledby={labelledBy}>
        <thead>
          <tr>
            <th scope="col">Message</th>
            <th scope="col">Channel</th>
            <th scope="col">To</th>
            <th scope="col">Status</th>
            <th scope="col">When</th>
          </tr>
        </thead>
        <tbody>
          {notifications.data.items.map((notification) => (
            <NotificationRow key={notification.id} notification={notification} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function NotificationRow({ notification }: { notification: Notification }) {
  const { message, status, attempts } = notification
  const last = attempts.at(-1)
  const at = notification.sentAt ?? last?.at ?? notification.createdAt
  return (
    <tr>
      <th scope="row">
        {messageTypeLabel(notification.messageType)}
        {message && (
          <details className="notification-message">
            <summary>Message</summary>
            {message.subject && (
              <p>
                <strong>{subjectLabel(notification.channel)}:</strong> {message.subject}
              </p>
            )}
            {message.html ? (
              <iframe
                title={`${messageTypeLabel(notification.messageType)}: as HTML`}
                srcDoc={message.html}
                sandbox=""
                className="template-html"
              />
            ) : (
              message.text && <pre className="template-text">{message.text}</pre>
            )}
          </details>
        )}
      </th>
      <td>{channelLabel(notification.channel)}</td>
      <td>{notification.recipient ? <code>{notification.recipient}</code> : <span className="muted">—</span>}</td>
      <td>
        <Badge tone={notificationTones[status]} dot>
          {status === 'PENDING' ? 'Pending' : status === 'SENT' ? 'Sent' : 'Failed'}
        </Badge>
        {status !== 'SENT' && last?.reason && <small className="hint">{last.reason}</small>}
      </td>
      <td>
        <time dateTime={at}>{formatTime(at)}</time>
      </td>
    </tr>
  )
}
