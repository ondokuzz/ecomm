import { describe, expect, it } from 'vitest'
import { type Payment, paymentStatusLabel, paymentStatuses, transactionRowsOf } from './payment'

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
    },
  ],
}

describe('paymentStatusLabel', () => {
  it('names each Payment status as Staff read it', () => {
    expect(paymentStatuses.map(paymentStatusLabel)).toEqual(['Pending', 'Authorized', 'Declined', 'Voided'])
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
