import { describe, expect, it } from 'vitest'
import { customerInitials, customerLabel } from './customer'

describe('customerInitials', () => {
  it('takes the first letters of the given and family names', () => {
    expect(customerInitials({ given_name: 'smoke', family_name: 'Test', email: 'x@ecomm.local' })).toBe('ST')
  })

  it('falls back to the first and last words of the full name', () => {
    expect(customerInitials({ name: 'Ada King Lovelace' })).toBe('AL')
  })

  it('falls back to the email address, split on dots, dashes and underscores', () => {
    expect(customerInitials({ email: 'demo@ecomm.local' })).toBe('D')
    expect(customerInitials({ email: 'jane.doe@ecomm.local' })).toBe('JD')
    expect(customerInitials({ email: 'smoke-1727@ecomm.local' })).toBe('S1')
  })

  it('ignores blank names', () => {
    expect(customerInitials({ given_name: ' ', name: '', preferred_username: 'kim' })).toBe('K')
  })

  it('shows a placeholder when nothing names the Customer', () => {
    expect(customerInitials({})).toBe('?')
  })
})

describe('customerLabel', () => {
  it('prefers the email, then the username', () => {
    expect(customerLabel({ email: 'demo@ecomm.local', preferred_username: 'demo' })).toBe('demo@ecomm.local')
    expect(customerLabel({ preferred_username: 'demo' })).toBe('demo')
    expect(customerLabel({})).toBe('Customer')
  })
})
