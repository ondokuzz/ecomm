import { describe, expect, it } from 'vitest'
import { rmaKindLabel, rmaStatusLabel } from './rma'

describe('rmaStatusLabel', () => {
  it('reads each RMA status as Staff say it', () => {
    expect(rmaStatusLabel('REQUESTED')).toBe('Requested')
    expect(rmaStatusLabel('APPROVED')).toBe('Approved')
    expect(rmaStatusLabel('REJECTED')).toBe('Rejected')
  })
})

describe('rmaKindLabel', () => {
  it('reads a return and a warranty claim', () => {
    expect(rmaKindLabel('RETURN')).toBe('Return')
    expect(rmaKindLabel('WARRANTY_CLAIM')).toBe('Warranty claim')
  })
})
