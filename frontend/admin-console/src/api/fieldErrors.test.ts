import { describe, expect, it } from 'vitest'
import { fieldErrorsOf } from './fieldErrors'
import { ApiError, NetworkError } from './http'

const badRequest = (problem: Record<string, unknown>) => new ApiError(400, { status: 400, ...problem })

describe('fieldErrorsOf', () => {
  it("maps each of a problem detail's errors to its field", () => {
    const error = badRequest({
      detail: 'Category is invalid: attributes[1].values are required for an ENUM',
      errors: [
        { field: 'name', message: 'is required' },
        { field: 'attributes[1].values', message: 'are required for an ENUM' },
      ],
    })
    expect(fieldErrorsOf(error)).toEqual({
      name: 'is required',
      'attributes[1].values': 'are required for an ENUM',
    })
  })

  it('joins several messages about the same field', () => {
    const error = badRequest({
      errors: [
        { field: 'attributes.screen', message: 'is required' },
        { field: 'attributes.screen', message: 'must be a number' },
      ],
    })
    expect(fieldErrorsOf(error)).toEqual({ 'attributes.screen': 'is required; must be a number' })
  })

  it('has none for a problem detail without errors, or with malformed ones', () => {
    expect(fieldErrorsOf(badRequest({ detail: 'Malformed JSON' }))).toEqual({})
    expect(fieldErrorsOf(badRequest({ errors: 'name is required' }))).toEqual({})
    expect(fieldErrorsOf(badRequest({ errors: [{ field: 'name' }, { message: 'is required' }, null] }))).toEqual({})
  })

  it('has none for a failure that is not a problem detail', () => {
    expect(fieldErrorsOf(new NetworkError(new TypeError('Failed to fetch')))).toEqual({})
    expect(fieldErrorsOf(new Error('boom'))).toEqual({})
    expect(fieldErrorsOf(undefined)).toEqual({})
  })
})
