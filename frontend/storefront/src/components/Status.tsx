import { type ReactNode, useEffect, useRef } from 'react'
import { type LookupFailure, failureOf, isUnauthorized } from '../api/failure'
import { Button } from './ui/Button'
import { Icon } from './ui/Icon'

/**
 * Shows why a request failed, using the problem detail's message when the service sent one, and its
 * reference. With a `title`, it says what didn't load; with `onRetry`, it offers to try again.
 */
export function ErrorMessage({
  error,
  title,
  retrying,
  onRetry,
}: {
  error: unknown
  title?: string
  retrying?: boolean
  onRetry?: () => void
}) {
  const { message, reference } = failureOf(error)
  return (
    <div className="alert alert-danger" role="alert">
      <Icon name="alert" size={18} />
      <div className="alert-body">
        {title && <strong>{title}</strong>}
        <span>{message}</span>
        {reference && <SupportReference reference={reference} />}
      </div>
      {onRetry && !isUnauthorized(error) && (
        <Button size="sm" className="alert-action" loading={retrying} onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

/** A failed lookup beside a page's main content, such as Variant names or Stock, saying what it left out. */
export function LookupError({ failure, title }: { failure: LookupFailure | undefined; title: string }) {
  if (!failure) return null
  return <ErrorMessage error={failure.error} title={title} retrying={failure.retrying} onRetry={failure.retry} />
}

/** A page's one `h1`, for a panel standing in for the whole page, or an `h2` under the page's own. */
export type HeadingLevel = 'h1' | 'h2'

/**
 * A request that failed in place of a page's main content: the error panel, with the reason, the
 * support reference and a way to try again. Its title is the page's `h1` unless `heading` says
 * the page already has one.
 */
export function ErrorState({
  title,
  error,
  retrying,
  onRetry,
  heading = 'h1',
}: {
  title: string
  error: unknown
  retrying: boolean
  onRetry: () => void
  heading?: HeadingLevel
}) {
  const { message, reference } = failureOf(error)
  return (
    <ErrorPanel title={title} message={message} reference={reference} heading={heading}>
      {/* An expired sign-in is already on its way to Keycloak; trying again would only fail the same way. */}
      {!isUnauthorized(error) && (
        <Button variant="primary" loading={retrying} onClick={onRetry}>
          Try again
        </Button>
      )}
    </ErrorPanel>
  )
}

/** The error panel's look: an icon, what failed and why, the ways on as `children`, and the reference. */
export function ErrorPanel({
  title,
  message,
  reference,
  heading: Heading = 'h1',
  children,
}: {
  title: string
  message: string
  reference?: string
  heading?: HeadingLevel
  children: ReactNode
}) {
  return (
    <div className="empty error-state" role="alert">
      <span className="error-state-icon">
        <Icon name="alert" size={28} />
      </span>
      <Heading>{title}</Heading>
      <p>{message}</p>
      {children}
      {reference && <SupportReference reference={reference} />}
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

/**
 * A friendly stand-in for a list with nothing in it, with a way on. Its title is an `h2` under the
 * page's `h1`, or, as `heading="h1"`, the page's own when it stands in for the whole page.
 */
export function EmptyState({
  title,
  children,
  action,
  illustration,
  focusTitle,
  heading: Heading = 'h2',
}: {
  title: string
  children?: ReactNode
  action?: ReactNode
  illustration?: ReactNode
  /** Moves focus to the title on arrival, for when the control the Customer used just went away. */
  focusTitle?: boolean
  heading?: HeadingLevel
}) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    if (focusTitle) heading.current?.focus()
  }, [focusTitle])

  return (
    <div className="empty">
      {illustration}
      <Heading ref={heading} tabIndex={focusTitle ? -1 : undefined}>
        {title}
      </Heading>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}
