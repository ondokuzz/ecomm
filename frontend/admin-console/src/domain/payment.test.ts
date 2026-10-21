import { describe, expect, it } from 'vitest'
import {
  type Payment,
  paymentStatusLabel,
  paymentStatuses,
  refundRowsOf,
  refundedOf,
  transactionRowsOf,
} from './payment'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

const payment: Payment = {
  id: 'b7d1e1c4-0000-4000-8000-000000000001',
  orderId: '3f2a9c1b-5d4e-4f00-8a00-0123456789ab',
  amount: eur(79900),
  status: 'VOIDED',
  declineReason: null,
  gatewayReference: 'mock_auth_1',
  transactions: [
    {
      kind: 'AUTHORIZATION',
      amount: eur(79900),
      outcome: 'PENDING',
      gatewayReference: 'mock_auth_1',
      declineReason: null,
      gatewayEventId: null,
      at: '2026-10-03T12:00:00Z',
      backfilled: false,
      reference: null,
    },
    {
      kind: 'AUTHORIZATION',
      amount: eur(79900),
      outcome: 'APPROVED',
      gatewayReference: 'mock_auth_1',
      declineReason: null,
      gatewayEventId: 'evt_1',
      at: '2026-10-03T12:00:03Z',
      backfilled: false,
      reference: null,
    },
    {
      kind: 'VOID',
      amount: eur(79900),
      outcome: 'APPROVED',
      gatewayReference: 'mock_void_1',
      declineReason: null,
      gatewayEventId: null,
      at: '2026-10-03T12:00:09Z',
      backfilled: false,
      reference: null,
    },
  ],
}

describe('paymentStatusLabel', () => {
  it('names each Payment status as Staff read it', () => {
    expect(paymentStatuses.map(paymentStatusLabel)).toEqual([
      'Pending',
      'Authorized',
      'Declined',
      'Voided',
      'Charged',
      'Partly refunded',
      'Refunded',
    ])
  })
})

describe('transactionRowsOf', () => {
  it('lists each Payment transaction, oldest first, by its kind and outcome, with its time', () => {
    const rows = transactionRowsOf(payment, 'en-GB')

    expect(rows.map((row) => [row.kind, row.outcome, row.reference, row.at])).toEqual([
      ['Authorization', 'Pending', 'mock_auth_1', '2026-10-03T12:00:00Z'],
      ['Authorization', 'Approved', 'mock_auth_1', '2026-10-03T12:00:03Z'],
      ['Void', 'Approved', 'mock_void_1', '2026-10-03T12:00:09Z'],
    ])
    expect(rows[0].time).toBe(
      new Date('2026-10-03T12:00:00Z').toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'medium' }),
    )
    expect(rows.every((row) => row.note === undefined && row.declineReason === undefined)).toBe(true)
  })

  it('lists a capture, approved or declined with its reason', () => {
    const rows = transactionRowsOf({
      transactions: [
        payment.transactions[1],
        {
          ...payment.transactions[2],
          kind: 'CAPTURE',
          outcome: 'DECLINED',
          gatewayReference: 'mock_capture_1',
          declineReason: 'authorization_expired',
        },
        { ...payment.transactions[2], kind: 'CAPTURE', gatewayReference: 'mock_capture_2' },
      ],
    })

    expect(rows.map((row) => [row.kind, row.outcome, row.reference, row.declineReason])).toEqual([
      ['Authorization', 'Approved', 'mock_auth_1', undefined],
      ['Capture', 'Declined', 'mock_capture_1', 'authorization_expired'],
      ['Capture', 'Approved', 'mock_capture_2', undefined],
    ])
  })

  it('lists a refund, approved or declined, with what it was for', () => {
    const refund = {
      ...payment.transactions[2],
      kind: 'REFUND' as const,
      amount: eur(30000),
      gatewayReference: 'mock-refund-1',
      reference: 'RMA-3F2A9C1B',
    }
    const rows = transactionRowsOf({
      transactions: [refund, { ...refund, outcome: 'DECLINED', declineReason: 'refund_window_closed' }],
    })

    expect(rows.map((row) => [row.kind, row.outcome, row.refundFor, row.declineReason])).toEqual([
      ['Refund', 'Approved', 'RMA-3F2A9C1B', undefined],
      ['Refund', 'Declined', 'RMA-3F2A9C1B', 'refund_window_closed'],
    ])
    expect(transactionRowsOf(payment)[0].refundFor).toBeUndefined()
  })

  it('gives a declined transaction its reason', () => {
    const [row] = transactionRowsOf({
      transactions: [{ ...payment.transactions[0], outcome: 'DECLINED', declineReason: 'insufficient_funds' }],
    })
    expect([row.outcome, row.declineReason]).toEqual(['Declined', 'insufficient_funds'])
  })

  it('marks a backfilled transaction, whose time is only approximate', () => {
    const [row] = transactionRowsOf({ transactions: [{ ...payment.transactions[1], backfilled: true }] })
    expect(row.note).toBe('Backfilled from before the ledger: the time is approximate')
  })

  it('shows a reference the gateway never gave as missing', () => {
    const [row] = transactionRowsOf({ transactions: [{ ...payment.transactions[0], gatewayReference: null }] })
    expect(row.reference).toBe('—')
  })
})

describe('refundedOf', () => {
  it('adds up the approved refunds, leaving out refused ones, in the Payment’s Currency', () => {
    const refund = (amountMinor: number, outcome: 'APPROVED' | 'DECLINED') => ({
      ...payment.transactions[2],
      kind: 'REFUND' as const,
      outcome,
      amount: eur(amountMinor),
    })

    expect(
      refundedOf({ amount: eur(79900), transactions: [refund(30000, 'APPROVED'), refund(100, 'DECLINED')] }),
    ).toEqual(eur(30000))
    expect(refundedOf(payment)).toEqual(eur(0))
  })
})

describe('refundRowsOf', () => {
  const transaction = {
    declineReason: null,
    gatewayEventId: null,
    backfilled: false,
  }

  it('lists every Payment’s Refunds, oldest first, with what each was for', () => {
    const captured = (id: string, at: string) => ({
      ...transaction,
      kind: 'CAPTURE' as const,
      amount: eur(79900),
      outcome: 'APPROVED' as const,
      gatewayReference: `mock_capture_${id}`,
      at,
      reference: null,
    })
    const rows = refundRowsOf(
      [
        {
          transactions: [
            captured('2', '2026-10-05T12:00:00Z'),
            {
              ...transaction,
              kind: 'REFUND',
              amount: eur(12900),
              outcome: 'DECLINED',
              gatewayReference: null,
              declineReason: 'card_closed',
              at: '2026-10-07T12:00:00Z',
              reference: 'RMA-0B8E6F2A',
            },
          ],
        },
        {
          transactions: [
            captured('1', '2026-10-03T12:00:00Z'),
            {
              ...transaction,
              kind: 'REFUND',
              amount: eur(39950),
              outcome: 'APPROVED',
              gatewayReference: 'mock_refund_1',
              at: '2026-10-06T12:00:00Z',
              reference: 'RMA-3F2A9C1B',
            },
          ],
        },
      ],
      'en-GB',
    )
    expect(rows).toEqual([
      {
        amount: eur(39950),
        outcome: 'Approved',
        reference: 'mock_refund_1',
        rmaReference: 'RMA-3F2A9C1B',
        at: '2026-10-06T12:00:00Z',
        time: '6 Oct 2026, 21:00:00',
      },
      {
        amount: eur(12900),
        outcome: 'Declined',
        reference: '—',
        declineReason: 'card_closed',
        rmaReference: 'RMA-0B8E6F2A',
        at: '2026-10-07T12:00:00Z',
        time: '7 Oct 2026, 21:00:00',
      },
    ])
  })
})
