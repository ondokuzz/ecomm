import { describe, expect, it } from 'vitest'
import type { Rma } from './rma'
import {
  decisionOf,
  historyRowsOf,
  inspectionOf,
  outcomesOf,
  queueOf,
  queueSearchOf,
  queueTabs,
  rmaKindLabel,
  rmaStatusLabel,
  receiveOf,
  receiveRefusalsOf,
  receivedChecksOf,
  resolutionOf,
  rmasQueryOf,
  sendBackOf,
  windowOf,
} from './rma'

describe('historyRowsOf', () => {
  it('lists every step, newest first, with who took it, when, and its note', () => {
    const rows = historyRowsOf(
      {
        history: [
          { status: 'REQUESTED', by: 'CUSTOMER', at: '2026-10-15T02:00:00Z' },
          { status: 'APPROVED', by: 'staff-1', at: '2026-10-15T03:00:00Z', note: 'Send it to Berlin' },
          { status: 'ON_HOLD', by: 'ORCHESTRATION', at: '2026-10-16T03:00:00Z', note: 'Refund declined' },
        ],
      },
      'en-GB',
    )
    expect(rows).toEqual([
      {
        status: 'On hold',
        by: 'Returns Saga',
        at: '2026-10-16T03:00:00Z',
        time: '16 Oct 2026, 12:00:00',
        note: 'Refund declined',
      },
      {
        status: 'Approved',
        by: 'staff-1',
        staff: true,
        at: '2026-10-15T03:00:00Z',
        time: '15 Oct 2026, 12:00:00',
        note: 'Send it to Berlin',
      },
      { status: 'Requested', by: 'Customer', at: '2026-10-15T02:00:00Z', time: '15 Oct 2026, 11:00:00' },
    ])
  })
})

describe('rmaStatusLabel', () => {
  it('reads each status as a word or two', () => {
    expect(rmaStatusLabel('APPROVED')).toBe('Approved')
    expect(rmaStatusLabel('ON_HOLD')).toBe('On hold')
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

describe('the RMA queue in the URL', () => {
  it('opens on the requested RMAs, first page, by default', () => {
    expect(queueOf(new URLSearchParams())).toEqual({ tab: 'requested', page: 0 })
  })

  it('reads a tab and a page counted from 1, ignoring what it doesn’t know', () => {
    expect(queueOf(new URLSearchParams('tab=closed&page=3'))).toEqual({ tab: 'closed', page: 2 })
    expect(queueOf(new URLSearchParams('tab=lost&page=-1'))).toEqual({ tab: 'requested', page: 0 })
    expect(queueOf(new URLSearchParams('page=1.5'))).toEqual({ tab: 'requested', page: 0 })
  })

  it('writes it back, leaving out the defaults', () => {
    expect(queueSearchOf({ tab: 'requested', page: 0 })).toBe('')
    expect(queueSearchOf({ tab: 'awaiting-units', page: 0 })).toBe('?tab=awaiting-units')
    expect(queueSearchOf({ tab: 'requested', page: 1 })).toBe('?page=2')
  })

  it('asks Returns & Warranty for every status a tab holds', () => {
    expect(rmasQueryOf({ tab: 'awaiting-units', page: 2 }, 20)).toBe('?status=APPROVED&page=2&size=20')
    expect(rmasQueryOf({ tab: 'received', page: 0 }, 1)).toBe('?status=RECEIVED%2CINSPECTED&page=0&size=1')
    expect(rmasQueryOf({ tab: 'closed', page: 0 }, 20)).toBe(
      '?status=REJECTED%2CEXPIRED%2CREFUNDED%2CEXCHANGED%2CCLOSED&page=0&size=20',
    )
  })
})

describe('the RMA queue’s tabs', () => {
  it('follow an RMA from request to resolution, each named for its stage', () => {
    expect(queueTabs.map((tab) => tab.label)).toEqual(['Requested', 'Awaiting units', 'Received', 'On hold', 'Closed'])
  })

  it('hold every status between them, each once', () => {
    expect(queueTabs.flatMap((tab) => tab.statuses).sort()).toEqual(
      [
        'APPROVED',
        'CLOSED',
        'EXCHANGED',
        'EXPIRED',
        'INSPECTED',
        'ON_HOLD',
        'RECEIVED',
        'REFUNDED',
        'REJECTED',
        'REQUESTED',
      ].sort(),
    )
  })
})

describe('rmaStatusLabel', () => {
  it('reads each RMA status as Staff say it', () => {
    expect(rmaStatusLabel('REQUESTED')).toBe('Requested')
    expect(rmaStatusLabel('APPROVED')).toBe('Approved')
    expect(rmaStatusLabel('REJECTED')).toBe('Rejected')
    expect(rmaStatusLabel('RECEIVED')).toBe('Received')
    expect(rmaStatusLabel('EXPIRED')).toBe('Expired')
    expect(rmaStatusLabel('INSPECTED')).toBe('Inspected')
    expect(rmaStatusLabel('REFUNDED')).toBe('Refunded')
    expect(rmaStatusLabel('EXCHANGED')).toBe('Exchanged')
    expect(rmaStatusLabel('CLOSED')).toBe('Closed')
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

describe('receivedChecksOf', () => {
  const imeis = ['490154203237518', '356938035643809']

  it('accepts each of the RMA’s Serial numbers, whatever its case, as the RMA has it', () => {
    expect(receivedChecksOf(['A1B2-C3'], [' a1b2-c3 '])).toEqual([{ state: 'valid', serialNumber: 'A1B2-C3' }])
    expect(receivedChecksOf(imeis, [imeis[1], imeis[0]])).toEqual([
      { state: 'valid', serialNumber: imeis[1] },
      { state: 'valid', serialNumber: imeis[0] },
    ])
  })

  it('leaves an empty input or the start of one of its Serial numbers unflagged, but incomplete', () => {
    expect(receivedChecksOf(imeis, ['', '4901542'])).toEqual([
      { state: 'incomplete', message: 'Enter the Serial number of a unit received.' },
      { state: 'incomplete', message: 'Enter the Serial number of a unit received.' },
    ])
  })

  it('refuses one that isn’t the RMA’s as soon as it can’t become one', () => {
    expect(receivedChecksOf(imeis, ['490154203237519', '35'])).toEqual([
      { state: 'invalid', message: 'Not one of this RMA’s Serial numbers.' },
      { state: 'incomplete', message: 'Enter the Serial number of a unit received.' },
    ])
  })

  it('refuses a Serial number typed twice, on its second input', () => {
    expect(receivedChecksOf(imeis, [imeis[0], imeis[0].toLowerCase()])).toEqual([
      { state: 'valid', serialNumber: imeis[0] },
      { state: 'invalid', message: 'Typed twice: each unit has its own.' },
    ])
  })
})

describe('receiveOf', () => {
  it('sends the RMA’s own Serial numbers once each input is one of them', () => {
    expect(
      receiveOf({ serialNumbers: ['AB-12', 'CD-34'], quantity: 2 }, { serialNumbers: ['cd-34', 'AB-12'] }),
    ).toEqual({
      body: { serialNumbers: ['CD-34', 'AB-12'] },
    })
  })

  it('names each input that holds back the receipt, as Returns & Warranty names it', () => {
    expect(receiveOf({ serialNumbers: ['AB-12', 'CD-34'], quantity: 2 }, { serialNumbers: ['AB-12', ''] })).toEqual({
      errors: { 'serialNumbers[1]': 'Enter the Serial number of a unit received.' },
    })
  })

  it('confirms the quantity of a Variant without Serial tracking', () => {
    expect(receiveOf({ serialNumbers: [], quantity: 3 }, { quantity: ' 3 ' })).toEqual({ body: { quantity: 3 } })
    expect(receiveOf({ serialNumbers: [], quantity: 3 }, { quantity: '2' })).toEqual({
      errors: { quantity: 'Confirm the 3 units this RMA is for.' },
    })
    expect(receiveOf({ serialNumbers: [], quantity: 1 }, { quantity: '' })).toEqual({
      errors: { quantity: 'Confirm the 1 unit this RMA is for.' },
    })
  })
})

describe('sendBackOf', () => {
  const now = new Date('2026-10-20T12:00:00Z')

  it('shows the Send-back deadline in Staff’s time zone, unflagged while more than 2 days off', () => {
    expect(sendBackOf('2026-10-22T12:00:01Z', now, 'en-GB')).toEqual({
      at: '2026-10-22T12:00:01Z',
      time: '22 Oct 2026, 21:00:01',
    })
  })

  it('flags one within 2 days', () => {
    expect(sendBackOf('2026-10-22T12:00:00Z', now, 'en-GB').flag).toBe('Due within 2 days')
    expect(sendBackOf('2026-10-20T12:00:01Z', now, 'en-GB').flag).toBe('Due within 2 days')
  })

  it('flags one passed, which the returns Saga is about to expire', () => {
    expect(sendBackOf('2026-10-20T12:00:00Z', now, 'en-GB').flag).toBe('Passed')
  })
})

describe('the Inspection', () => {
  it('offers the outcomes for the RMA’s kind', () => {
    expect(outcomesOf('RETURN')).toEqual([
      { outcome: 'ACCEPTED', label: 'Accepted', refusing: false },
      { outcome: 'REFUSED', label: 'Refused', refusing: true },
    ])
    expect(outcomesOf('WARRANTY_CLAIM')).toEqual([
      { outcome: 'FAULT_CONFIRMED', label: 'Fault confirmed', refusing: false },
      { outcome: 'NO_FAULT_FOUND', label: 'No fault found', refusing: true },
    ])
  })

  it('needs an outcome', () => {
    expect(inspectionOf(undefined, '')).toEqual({ errors: { outcome: 'Choose what the inspection found.' } })
  })

  it('records a note only when one is typed', () => {
    expect(inspectionOf('ACCEPTED', '  ')).toEqual({ body: { outcome: 'ACCEPTED' } })
    expect(inspectionOf('FAULT_CONFIRMED', ' Port worn ')).toEqual({
      body: { outcome: 'FAULT_CONFIRMED', note: 'Port worn' },
    })
  })

  it('needs a note for a refusing outcome', () => {
    expect(inspectionOf('REFUSED', ' ')).toEqual({ errors: { note: 'Say why: a refusing outcome needs a note.' } })
    expect(inspectionOf('NO_FAULT_FOUND', 'Works as designed')).toEqual({
      body: { outcome: 'NO_FAULT_FOUND', note: 'Works as designed' },
    })
  })

  it('takes a note of up to 1,000 characters', () => {
    expect(inspectionOf('ACCEPTED', 'x'.repeat(1001))).toEqual({ errors: { note: 'Keep it to 1,000 characters.' } })
  })
})

describe('resolutionOf', () => {
  const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })
  const rma = (fields: Partial<Rma>): Rma =>
    ({ kind: 'RETURN', status: 'APPROVED', history: [], refundAmount: eur(39950), ...fields }) as Rma

  it('has none before the Inspection', () => {
    expect(resolutionOf(rma({ status: 'RECEIVED' }))).toBeUndefined()
  })

  it('awaits the returns Saga once inspected', () => {
    expect(resolutionOf(rma({ status: 'INSPECTED', inspection: { outcome: 'ACCEPTED' } }))).toEqual({
      kind: 'resolving',
    })
  })

  it('follows a replacement from a confirmed fault, before and after the RMA is exchanged', () => {
    const inspection = { outcome: 'FAULT_CONFIRMED' as const }
    expect(resolutionOf(rma({ kind: 'WARRANTY_CLAIM', status: 'INSPECTED', inspection }))).toEqual({
      kind: 'replacement',
    })
    expect(
      resolutionOf(
        rma({
          kind: 'WARRANTY_CLAIM',
          status: 'EXCHANGED',
          inspection,
          resolution: { replacementShipmentId: 'shp-1' },
        }),
      ),
    ).toEqual({ kind: 'replacement', shipmentId: 'shp-1' })
  })

  it('shows a Refund’s amount and its gateway reference', () => {
    expect(
      resolutionOf(rma({ status: 'REFUNDED', resolution: { paymentTransactionReference: 'mock_refund_1' } })),
    ).toEqual({ kind: 'refund', amount: eur(39950), reference: 'mock_refund_1' })
  })

  it('closes a refused one with the Inspection’s note, and holds one with the latest hold’s reason', () => {
    expect(resolutionOf(rma({ status: 'CLOSED', inspection: { outcome: 'REFUSED', note: 'Water damage' } }))).toEqual({
      kind: 'closed',
      note: 'Water damage',
    })
    expect(
      resolutionOf(
        rma({
          status: 'ON_HOLD',
          history: [{ status: 'ON_HOLD', by: 'ORCHESTRATION', at: '2026-10-16T03:00:00Z', note: 'Refund declined' }],
        }),
      ),
    ).toEqual({ kind: 'hold', reason: 'Refund declined' })
  })
})

describe('receiveRefusalsOf', () => {
  it('names each input holding a Serial number Returns & Warranty refused, whatever its case', () => {
    expect(
      receiveRefusalsOf({ reason: 'serialMismatch', serialNumbers: ['ZZ-99'] }, { serialNumbers: ['AB-12', ' zz-99'] }),
    ).toEqual({ 'serialNumbers[1]': 'Returns & Warranty says it isn’t one of this RMA’s Serial numbers.' })
  })

  it('puts a quantity mismatch beside the quantity, or beside the units of a serial-tracked RMA', () => {
    expect(receiveRefusalsOf({ reason: 'quantityMismatch', expected: 3 }, { quantity: '2' })).toEqual({
      quantity: 'Returns & Warranty expects 3.',
    })
    expect(receiveRefusalsOf({ reason: 'quantityMismatch', expected: 2 }, { serialNumbers: ['AB-12'] })).toEqual({
      serialNumbers: 'Returns & Warranty expects 2.',
    })
  })

  it('names nothing for any other refusal', () => {
    expect(receiveRefusalsOf({ reason: 'transitionRefused' }, { quantity: '1' })).toEqual({})
  })
})
