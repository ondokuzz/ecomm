import { describe, expect, it, vi } from 'vitest'
import { failureOf, isNotFound, isUnauthorized, lookupFailure } from './failure'
import { ApiError, NetworkError, errorFrom } from './http'

const problemJson = { 'Content-Type': 'application/problem+json' }

function problemResponse(status: number, problem: object, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(problem), { status, headers: { ...problemJson, ...headers } })
}

describe('errorFrom', () => {
  it('reads the problem detail and its Correlation ID', async () => {
    const error = await errorFrom(
      problemResponse(409, { title: 'Conflict', detail: 'The Cart changed.', status: 409, correlationId: 'abc-123' }),
    )
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(409)
    expect(error.message).toBe('The Cart changed.')
    expect(error.problem.detail).toBe('The Cart changed.')
    expect(error.correlationId).toBe('abc-123')
  })

  it("prefers the problem detail's Correlation ID to the response header's", async () => {
    const error = await errorFrom(
      problemResponse(500, { correlationId: 'from-body' }, { 'X-Correlation-Id': 'from-header' }),
    )
    expect(error.correlationId).toBe('from-body')
  })

  it('falls back to the response header when the body has no Correlation ID', async () => {
    const error = await errorFrom(problemResponse(404, { title: 'Not Found' }, { 'X-Correlation-Id': 'from-header' }))
    expect(error.correlationId).toBe('from-header')
  })

  it('reads the header from a response that is not JSON, such as a web server error page', async () => {
    const error = await errorFrom(
      new Response('<html>Bad Gateway</html>', {
        status: 502,
        headers: { 'Content-Type': 'text/html', 'X-Correlation-Id': 'edge-7' },
      }),
    )
    expect(error.status).toBe(502)
    expect(error.problem).toEqual({})
    expect(error.correlationId).toBe('edge-7')
  })

  it('has no Correlation ID when neither the body nor the headers name one', async () => {
    const error = await errorFrom(new Response('', { status: 503 }))
    expect(error.correlationId).toBeUndefined()
  })

  it('ignores a Correlation ID in the body that is not a string', async () => {
    const error = await errorFrom(problemResponse(500, { correlationId: 42 }, { 'X-Correlation-Id': 'from-header' }))
    expect(error.correlationId).toBe('from-header')
  })
})

describe('failureOf', () => {
  it("shows a 4xx in the service's own words, with its reference", () => {
    const error = new ApiError(409, { detail: 'The Cart changed.', correlationId: 'abc-123' })
    expect(failureOf(error)).toEqual({ message: 'The Cart changed.', reference: 'abc-123' })
  })

  it('falls back to the title when a 4xx has no detail', () => {
    expect(failureOf(new ApiError(400, { title: 'Bad Request' })).message).toBe('Bad Request')
  })

  it("says a 5xx is on the shop's side rather than repeating the service's generic message", () => {
    const error = new ApiError(500, { detail: 'An unexpected error occurred.', correlationId: 'f1d7' })
    expect(failureOf(error)).toEqual({
      message: 'Something went wrong on our side. Please try again in a moment.',
      reference: 'f1d7',
    })
    expect(failureOf(new ApiError(502, {}, 'edge-7'))).toEqual({
      message: 'Something went wrong on our side. Please try again in a moment.',
      reference: 'edge-7',
    })
  })

  it('says an expired sign-in is being renewed', () => {
    expect(failureOf(new ApiError(401, { title: 'Unauthorized' })).message).toBe(
      'Your sign-in has expired. Taking you to log in…',
    )
  })

  it('has a general message and no reference for a failure that never got an answer', () => {
    expect(failureOf(new NetworkError(new TypeError('Failed to fetch')))).toEqual({
      message: "We couldn't reach the shop. Check your connection and try again.",
    })
    expect(failureOf(new TypeError('x is undefined'))).toEqual({ message: 'Something went wrong. Please try again.' })
    expect(failureOf('nope')).toEqual({ message: 'Something went wrong. Please try again.' })
  })
})

describe('isUnauthorized and isNotFound', () => {
  it('read the status of a failed response', () => {
    expect(isUnauthorized(new ApiError(401, {}))).toBe(true)
    expect(isUnauthorized(new ApiError(403, {}))).toBe(false)
    expect(isNotFound(new ApiError(404, {}))).toBe(true)
    expect(isNotFound(new ApiError(500, {}))).toBe(false)
    expect(isNotFound(new Error('404'))).toBe(false)
  })
})

describe('lookupFailure', () => {
  const lookup = (error: unknown, isFetching = false) => ({ error, isFetching, refetch: vi.fn() })

  it('is undefined while every lookup is fine', () => {
    expect(lookupFailure([lookup(null), lookup(null)])).toBeUndefined()
    expect(lookupFailure([])).toBeUndefined()
  })

  it('names the first failure, and retries only the lookups that failed', () => {
    const first = new ApiError(500, { correlationId: 'first' })
    const lookups = [lookup(null), lookup(first), lookup(new ApiError(503, {}))]
    const failure = lookupFailure(lookups)!
    expect(failure.error).toBe(first)

    failure.retry()
    expect(lookups[0]!.refetch).not.toHaveBeenCalled()
    expect(lookups[1]!.refetch).toHaveBeenCalled()
    expect(lookups[2]!.refetch).toHaveBeenCalled()
  })

  it('is retrying while any lookup is fetching again', () => {
    expect(lookupFailure([lookup(new Error('x')), lookup(null, true)])!.retrying).toBe(true)
    expect(lookupFailure([lookup(new Error('x'))])!.retrying).toBe(false)
  })
})
