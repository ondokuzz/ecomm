import { describe, expect, it } from 'vitest'
import { rmaKindLabel, rmaStatusLabel, windowOf } from './rma'

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

describe('windowOf', () => {
  // The tests run in Asia/Tokyo, nine hours ahead of UTC.
  it('names a Return Period and its start and end, in Staff’s time zone', () => {
    const window = windowOf(
      { kind: 'RETURN_PERIOD', startsAt: '2026-10-01T09:30:00Z', endsAt: '2026-10-15T09:30:00Z' },
      'en-GB',
    )
    expect(window).toEqual({
      name: 'Return Period',
      startsAt: '2026-10-01T09:30:00Z',
      from: '1 Oct 2026, 18:30:00',
      endsAt: '2026-10-15T09:30:00Z',
      to: '15 Oct 2026, 18:30:00',
    })
  })

  it('names a Warranty Window with the Warranty terms it was checked against', () => {
    const window = windowOf(
      {
        kind: 'WARRANTY_WINDOW',
        startsAt: '2026-01-31T00:00:00Z',
        endsAt: '2027-01-31T00:00:00Z',
        warranty: { months: 12, manufacturer: 'Sony' },
      },
      'en-GB',
    )
    expect(window.name).toBe('Warranty Window')
    expect(window.terms).toBe('12 months, from Sony')
    expect(window.to).toBe('31 Jan 2027, 09:00:00')
  })

  it('says a one-month warranty in the singular', () => {
    const window = windowOf(
      {
        kind: 'WARRANTY_WINDOW',
        startsAt: '2026-01-31T00:00:00Z',
        endsAt: '2026-02-28T00:00:00Z',
        warranty: { months: 1, manufacturer: 'JBL' },
      },
      'en-GB',
    )
    expect(window.terms).toBe('1 month, from JBL')
  })
})
