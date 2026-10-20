import { describe, expect, it } from 'vitest'
import { type StockMovement, balanceOf, movementRowsOf } from './stock'

const committed: StockMovement = {
  kind: 'COMMITTED',
  onHandChange: -2,
  reservedChange: -2,
  reservationId: '9c0e4b8a-0000-4000-8000-000000000001',
  reason: null,
  reference: null,
  at: '2026-10-05T10:00:00Z',
}

const adjusted: StockMovement = {
  kind: 'ADJUSTED',
  onHandChange: 25,
  reservedChange: 0,
  reservationId: null,
  reason: 'opening balance',
  reference: null,
  at: '2026-10-04T09:00:00Z',
}

const restocked: StockMovement = {
  kind: 'RESTOCKED',
  onHandChange: 1,
  reservedChange: 0,
  reservationId: null,
  reason: null,
  reference: 'RMA-3F2A9C1B',
  at: '2026-10-06T10:00:00Z',
}

const committedForAReplacement: StockMovement = {
  kind: 'COMMITTED',
  onHandChange: -1,
  reservedChange: -1,
  reservationId: '9c0e4b8a-0000-4000-8000-000000000002',
  reason: null,
  reference: 'rma:5d1c7e2a-1111-4000-8000-000000000003',
  at: '2026-10-07T10:00:00Z',
}

describe('movementRowsOf', () => {
  it('lists each movement as Staff read it, in the order Inventory gives them', () => {
    expect(movementRowsOf([committed, adjusted], 'en-GB')).toEqual([
      {
        kind: 'Committed',
        onHandChange: '−2',
        reservedChange: '−2',
        reservation: '9c0e4b8a-0000-4000-8000-000000000001',
        reason: '—',
        reference: '—',
        rmaId: null,
        at: '2026-10-05T10:00:00Z',
        time: '5 Oct 2026, 19:00:00',
      },
      {
        kind: 'Adjusted',
        onHandChange: '+25',
        reservedChange: '0',
        reservation: '—',
        reason: 'opening balance',
        reference: '—',
        rmaId: null,
        at: '2026-10-04T09:00:00Z',
        time: '4 Oct 2026, 18:00:00',
      },
    ])
  })
})

describe('movementRowsOf, for returns', () => {
  it('shows the RMA reference a restock names', () => {
    const [row] = movementRowsOf([restocked], 'en-GB')
    expect(row).toMatchObject({ kind: 'Restocked', onHandChange: '+1', reference: 'RMA-3F2A9C1B', rmaId: null })
  })

  it("shows the RMA a replacement's commit was for, by its reference, linked to it", () => {
    const [row] = movementRowsOf([committedForAReplacement], 'en-GB')
    expect(row).toMatchObject({
      kind: 'Committed',
      reference: 'RMA-5D1C7E2A',
      rmaId: '5d1c7e2a-1111-4000-8000-000000000003',
    })
  })
})

describe('balanceOf', () => {
  it('says On-hand equals the sum of the movements', () => {
    expect(balanceOf({ onHand: 23, onHandFromMovements: 23, balanced: true })).toEqual({
      label: 'Balanced',
      detail: 'On-hand, 23, equals the sum of every movement.',
    })
  })

  it('says by how much On-hand and the movements disagree', () => {
    expect(balanceOf({ onHand: 23, onHandFromMovements: 21, balanced: false })).toEqual({
      label: 'Out of balance',
      detail: 'On-hand is 23, but every movement adds up to 21.',
    })
  })
})
