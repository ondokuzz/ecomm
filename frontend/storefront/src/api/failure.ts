import { ApiError, NetworkError } from './http'

const somethingWentWrong = 'Something went wrong. Please try again.'

/** What to tell the Customer about a failed request, and the reference support can find it by. */
export interface Failure {
  message: string
  /** The request's Correlation ID, when it got an answer that named one. */
  reference?: string
}

/**
 * A failed request as the Customer sees it. A 4xx is in the service's own words, since it says what
 * to change; a 5xx is the shop's fault, and its own message ("An unexpected error occurred.") says no more.
 */
export function failureOf(error: unknown): Failure {
  if (error instanceof ApiError) {
    const reference = error.correlationId ? { reference: error.correlationId } : {}
    return { message: apiErrorMessage(error), ...reference }
  }
  if (error instanceof NetworkError) {
    return { message: "We couldn't reach the shop. Check your connection and try again." }
  }
  return { message: somethingWentWrong }
}

function apiErrorMessage(error: ApiError): string {
  if (error.status === 401) return 'Your sign-in has expired. Taking you to log in…'
  if (error.status >= 500) return 'Something went wrong on our side. Please try again in a moment.'
  return error.problem.detail ?? error.problem.title ?? somethingWentWrong
}

/** The Customer's token was refused: their sign-in expired in the middle of a flow. */
export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}

/** Some lookups a page shows beside its main content, such as Variant names or Stock, as TanStack Query reports each. */
interface Lookup {
  error: unknown
  isFetching: boolean
  refetch: () => unknown
}

/** One of a page's lookups failed: the first failure, and a way to retry every one that did. */
export interface LookupFailure {
  error: unknown
  retrying: boolean
  retry: () => void
}

export function lookupFailure(lookups: Lookup[]): LookupFailure | undefined {
  const failed = lookups.filter((lookup) => lookup.error)
  if (failed.length === 0) return undefined
  return {
    error: failed[0]!.error,
    retrying: lookups.some((lookup) => lookup.isFetching),
    retry: () => failed.forEach((lookup) => lookup.refetch()),
  }
}
