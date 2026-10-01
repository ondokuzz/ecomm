import { ApiError, NetworkError } from './http'

const somethingWentWrong = 'Something went wrong. Please try again.'

/** What to tell Staff about a failed request, and the reference support can find it by. */
export interface Failure {
  message: string
  /** The request's Correlation ID, when it got an answer that named one. */
  reference?: string
}

/**
 * A failed request as Staff see it. A 4xx is in the service's own words, since it says what to
 * change; a 5xx is on the platform's side, and its own message ("An unexpected error occurred.") says no more.
 */
export function failureOf(error: unknown): Failure {
  if (error instanceof ApiError) {
    const reference = error.correlationId ? { reference: error.correlationId } : {}
    return { message: apiErrorMessage(error), ...reference }
  }
  if (error instanceof NetworkError) {
    return { message: "We couldn't reach the services. Check your connection and try again." }
  }
  return { message: somethingWentWrong }
}

function apiErrorMessage(error: ApiError): string {
  if (error.status === 401) return 'Your sign-in has expired. Taking you to log in…'
  if (error.status >= 500) return 'Something went wrong on our side. Please try again in a moment.'
  return error.problem.detail ?? error.problem.title ?? somethingWentWrong
}

/** The token was refused: the sign-in expired in the middle of a flow. */
export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
