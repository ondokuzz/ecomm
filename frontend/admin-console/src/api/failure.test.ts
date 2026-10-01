import { describe, expect, it } from 'vitest'
import { failureOf } from './failure'
import { ApiError, NetworkError } from './http'

describe('failureOf', () => {
  it("says a 4xx in the service's own words, with its reference", () => {
    const error = new ApiError(409, { detail: 'Category phones still has Products', correlationId: 'abc-123' })
    expect(failureOf(error)).toEqual({ message: 'Category phones still has Products', reference: 'abc-123' })
  })

  it("says a 5xx is on the platform's side, keeping the reference from the header", () => {
    const error = new ApiError(500, { detail: 'An unexpected error occurred.' }, 'from-header')
    expect(failureOf(error)).toEqual({
      message: 'Something went wrong on our side. Please try again in a moment.',
      reference: 'from-header',
    })
  })

  it('says an expired sign-in is on its way to log in', () => {
    expect(failureOf(new ApiError(401, {})).message).toBe('Your sign-in has expired. Taking you to log in…')
  })

  it('says a request with no answer has no reference', () => {
    expect(failureOf(new NetworkError(new TypeError('Failed to fetch')))).toEqual({
      message: "We couldn't reach the services. Check your connection and try again.",
    })
  })
})
