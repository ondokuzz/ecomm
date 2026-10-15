import { describe, expect, it } from 'vitest'
import {
  decisionOf,
  decisionRowsOf,
  queueOf,
  queueSearchOf,
  rmaKindLabel,
  rmaStatusLabel,
  rmasQueryOf,
  windowOf,
} from './rma'

describe('decisionRowsOf', () => {
  it('lists none for a requested RMA', () => {
    expect(decisionRowsOf({ decisions: [] })).toEqual([])
  })

  it('shows a decision with who made it, when, and its note, newest first', () => {
    const rows = decisionRowsOf(
      {
        decisions: [
          { status: 'APPROVED', decidedBy: 'staff-1', decidedAt: '2026-10-15T03:00:00Z' },
          { status: 'REJECTED', decidedBy: 'staff-2', decidedAt: '2026-10-16T03:00:00Z', note: 'Water damage' },
        ],
      },
      'en-GB',
    )
    expect(rows).toEqual([
      {
        status: 'Rejected',
        decidedBy: 'staff-2',
        at: '2026-10-16T03:00:00Z',
        time: '16 Oct 2026, 12:00:00',
        note: 'Water damage',
      },
      { status: 'Approved', decidedBy: 'staff-1', at: '2026-10-15T03:00:00Z', time: '15 Oct 2026, 12:00:00' },
    ])
  })
})

describe('decisionOf', () => {
  it('approves without a note when none is typed', () => {
    expect(decisionOf('approve', '')).toEqual({ body: {} })
    expect(decisionOf('approve', '   ')).toEqual({ body: {} })
  })

  it('approves with the note, trimmed', () => {
    expect(decisionOf('approve', '  Send it to Berlin ')).toEqual({ body: { note: 'Send it to Berlin' } })
  })

  it('refuses to reject without a reason', () => {
    expect(decisionOf('reject', ' ')).toEqual({ error: 'Say why it is rejected: the Customer is told.' })
  })

  it('rejects with the reason, trimmed', () => {
    expect(decisionOf('reject', ' Outside the box ')).toEqual({ body: { reason: 'Outside the box' } })
  })

  it('takes a note or a reason of up to 1,000 characters', () => {
    expect(decisionOf('reject', 'x'.repeat(1000))).toEqual({ body: { reason: 'x'.repeat(1000) } })
    expect(decisionOf('reject', 'x'.repeat(1001))).toEqual({ error: 'Keep it to 1,000 characters.' })
    expect(decisionOf('approve', 'x'.repeat(1001))).toEqual({ error: 'Keep it to 1,000 characters.' })
  })
})

describe('the RMA approval queue in the URL', () => {
  it('opens on the requested RMAs, first page, by default', () => {
    expect(queueOf(new URLSearchParams())).toEqual({ status: 'REQUESTED', page: 0 })
  })

  it('reads a tab and a page counted from 1, ignoring what it doesn’t know', () => {
    expect(queueOf(new URLSearchParams('status=REJECTED&page=3'))).toEqual({ status: 'REJECTED', page: 2 })
    expect(queueOf(new URLSearchParams('status=LOST&page=-1'))).toEqual({ status: 'REQUESTED', page: 0 })
    expect(queueOf(new URLSearchParams('page=1.5'))).toEqual({ status: 'REQUESTED', page: 0 })
  })

  it('writes it back, leaving out the defaults', () => {
    expect(queueSearchOf({ status: 'REQUESTED', page: 0 })).toBe('')
    expect(queueSearchOf({ status: 'APPROVED', page: 0 })).toBe('?status=APPROVED')
    expect(queueSearchOf({ status: 'REQUESTED', page: 1 })).toBe('?page=2')
  })

  it('asks Returns & Warranty for that status’s page, oldest first being its order', () => {
    expect(rmasQueryOf({ status: 'APPROVED', page: 2 }, 20)).toBe('?status=APPROVED&page=2&size=20')
  })
})

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
