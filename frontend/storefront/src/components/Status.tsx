import type { ReactNode } from 'react'
import { ApiError } from '../api/http'
import { Button } from './ui/Button'
import { Icon } from './ui/Icon'
import { Skeleton } from './ui/Skeleton'

/** Placeholder lines while a page's data loads; screen readers hear "Loading…". */
export function Loading() {
  return (
    <div className="loading" role="status">
      <span className="visually-hidden">Loading…</span>
      <Skeleton width="40%" height="2.25rem" radius="var(--radius-md)" />
      <Skeleton width="90%" />
      <Skeleton width="75%" />
      <Skeleton width="60%" />
    </div>
  )
}

/** Shows why a request failed, using the problem detail's message when the service sent one. */
export function ErrorMessage({ error }: { error: unknown }) {
  const message = errorText(error)
  return (
    <p className="alert alert-danger" role="alert">
      <Icon name="alert" size={18} />
      <span>{message}</span>
    </p>
  )
}

/** A request that failed in place of a page's main content, with the reason and a way to try again. */
export function ErrorState({ title, error, onRetry }: { title: string; error: unknown; onRetry: () => void }) {
  return (
    <div className="empty error-state" role="alert">
      <span className="error-state-icon">
        <Icon name="alert" size={28} />
      </span>
      <h2>{title}</h2>
      <p>{errorText(error)}</p>
      <Button variant="primary" onClick={onRetry}>
        Try again
      </Button>
    </div>
  )
}

/** A friendly stand-in for a list with nothing in it, with a way on. */
export function EmptyState({
  title,
  children,
  action,
  illustration,
}: {
  title: string
  children?: ReactNode
  action?: ReactNode
  illustration?: ReactNode
}) {
  return (
    <div className="empty">
      {illustration}
      <h2>{title}</h2>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}

function errorText(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
}
