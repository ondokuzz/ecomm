import { ApiError } from '../api/http'

export function Loading() {
  return <p className="muted">Loading…</p>
}

/** Shows why a request failed, using the problem detail's message when the service sent one. */
export function ErrorMessage({ error }: { error: unknown }) {
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  return (
    <p className="error" role="alert">
      {message}
    </p>
  )
}
