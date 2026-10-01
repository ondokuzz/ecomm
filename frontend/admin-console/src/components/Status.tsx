import { failureOf, isUnauthorized } from '../api/failure'
import { Button } from './ui/Button'
import { Icon } from './ui/Icon'

/**
 * Why a request failed, in the service's words when it sent a problem detail, with its support
 * reference. With a `title` it says what failed; with `onRetry` it offers to try again.
 */
export function ErrorMessage({
  error,
  title,
  message,
  retrying,
  onRetry,
}: {
  error: unknown
  title?: string
  /** Says this instead of the failure's own message; the reference stays. */
  message?: string
  retrying?: boolean
  onRetry?: () => void
}) {
  const failure = failureOf(error)
  const reference = failure.reference
  return (
    <div className="alert alert-danger" role="alert">
      <Icon name="alert" />
      <div className="alert-body">
        {title && <strong>{title}</strong>}
        <span>{message ?? failure.message}</span>
        {reference && <SupportReference reference={reference} />}
      </div>
      {/* An expired sign-in is already on its way to Keycloak; trying again would only fail the same way. */}
      {onRetry && !isUnauthorized(error) && (
        <Button size="sm" className="alert-action" loading={retrying} onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

/** The Correlation ID of a failed request, which support finds it by in the services' logs. */
export function SupportReference({ reference }: { reference: string }) {
  return (
    <small className="support-reference">
      Reference: <code>{reference}</code>
    </small>
  )
}

/** The message for one field that a service refused, tied to its control through `id`. */
export function FieldError({ id, message }: { id: string; message: string | undefined }) {
  if (!message) return null
  return (
    <span id={id} className="field-error">
      {message}
    </span>
  )
}

/** Screen readers hear "Loading…" once; sighted users see the table's skeleton rows. */
export function SkeletonRows({ rows, columns }: { rows: number; columns: number }) {
  return (
    <tbody aria-busy="true">
      {Array.from({ length: rows }, (_, row) => (
        <tr key={row}>
          {Array.from({ length: columns }, (_, column) => (
            <td key={column}>
              <span className="skeleton" />
              {row === 0 && column === 0 && <span className="visually-hidden">Loading…</span>}
            </td>
          ))}
        </tr>
      ))}
    </tbody>
  )
}
