import type { ReactNode } from 'react'
import { ApiError } from '../api/http'
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
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  return (
    <p className="alert alert-danger" role="alert">
      <Icon name="alert" size={18} />
      <span>{message}</span>
    </p>
  )
}

/** A friendly stand-in for a list with nothing in it, with a way on. */
export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <h2>{title}</h2>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}
