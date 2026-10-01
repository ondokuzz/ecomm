import { describe, expect, it } from 'vitest'
import { isStaff } from './roles'

/** An unsigned JWT with `payload`; only its payload is read. */
function token(payload: unknown): string {
  const encode = (value: unknown) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value))))
      .replaceAll('+', '-')
      .replaceAll('/', '_')
      .replaceAll('=', '')
  return `${encode({ alg: 'none' })}.${encode(payload)}.`
}

describe('isStaff', () => {
  it("is true when the access token's realm roles include STAFF", () => {
    expect(isStaff(token({ realm_access: { roles: ['offline_access', 'STAFF'] } }))).toBe(true)
  })

  it('is false for a Customer', () => {
    expect(isStaff(token({ realm_access: { roles: ['CUSTOMER', 'default-roles-ecomm'] } }))).toBe(false)
  })

  it('is false when there are no realm roles, or no readable token', () => {
    expect(isStaff(token({ sub: 'someone' }))).toBe(false)
    expect(isStaff('not-a-jwt')).toBe(false)
    expect(isStaff(undefined)).toBe(false)
  })

  it('reads a payload whose base64url has characters plain base64 lacks', () => {
    // "?>" encodes to "Pz4", and longer runs bring "-" and "_" into the payload.
    expect(isStaff(token({ name: '??>>??>>', realm_access: { roles: ['STAFF'] } }))).toBe(true)
  })
})
