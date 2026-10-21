import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  type Payment,
  type PaymentStatus,
  type PaymentTransaction,
  attemptFailure,
  cancellationReason,
  confirmation,
  confirmingAfter,
  defaultTestCard,
  paymentFailure,
  paymentStatusLabel,
  paymentStatusNote,
  paymentStatuses,
  paymentTransactionsOf,
  pollFor,
  refundedOf,
  testCards,
  whenConfirmingPayment,
} from './payment'

describe('testCards', () => {
  it('offers one card per mock gateway outcome, approving first', () => {
    expect(testCards.map((card) => card.token)).toEqual([
      'tok_approve',
      'tok_decline',
      'tok_insufficient_funds',
      'tok_gateway_error',
      'tok_async_approve',
      'tok_async_decline',
      'tok_capture_decline',
      'tok_refund_decline',
    ])
    expect(defaultTestCard).toBe(testCards[0])
  })

  it('offers a card that pays but whose charge is refused at shipping', () => {
    expect(testCards.find((card) => card.token === 'tok_capture_decline')).toMatchObject({
      label: 'Charge refused',
      outcome: 'The payment goes through, but the charge is refused when the order ships.',
    })
  })

  it('offers a card that pays but whose refunds are refused', () => {
    expect(testCards.find((card) => card.token === 'tok_refund_decline')).toMatchObject({
      label: 'Refund refused',
      outcome: 'The payment goes through, but any refund of it is refused.',
    })
  })

  it('gives each card its own number', () => {
    expect(new Set(testCards.map((card) => card.last4)).size).toBe(testCards.length)
  })
})

describe('testCards the bank confirms later', () => {
  it('names them by what the bank does', () => {
    expect(testCards.filter((card) => card.token.startsWith('tok_async_')).map((card) => card.label)).toEqual([
      'Bank confirms, approves',
      'Bank confirms, declines',
    ])
  })
})

describe('whenConfirmingPayment', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(10_000)
  })
  afterEach(() => vi.useRealTimers())

  it('says a Pay is being confirmed exactly 2 seconds after it was sent', () => {
    expect(confirmingAfter).toBe(2000)
    const onConfirming = vi.fn()
    whenConfirmingPayment(10_000, onConfirming)
    vi.advanceTimersByTime(1999)
    expect(onConfirming).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(onConfirming).toHaveBeenCalledOnce()
  })

  it('counts from when the Pay was sent, not from when it is asked', () => {
    vi.setSystemTime(11_500)
    const onConfirming = vi.fn()
    whenConfirmingPayment(10_000, onConfirming)
    vi.advanceTimersByTime(499)
    expect(onConfirming).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(onConfirming).toHaveBeenCalledOnce()
  })

  it('says nothing of a Pay answered first', () => {
    const onConfirming = vi.fn()
    const cancel = whenConfirmingPayment(10_000, onConfirming)
    vi.advanceTimersByTime(1500)
    cancel()
    vi.advanceTimersByTime(10_000)
    expect(onConfirming).not.toHaveBeenCalled()
  })
})

describe('paymentFailure', () => {
  it('reads a 402 as a decline, explained by its reason', () => {
    expect(paymentFailure(402, { declineReason: 'insufficient_funds' })).toEqual({
      kind: 'declined',
      reason: 'insufficient_funds',
      title: 'Your card was declined',
      message: "The card doesn't have enough funds. Try another card.",
    })
    expect(paymentFailure(402, { declineReason: 'card_declined' })?.message).toBe(
      'Your bank declined the payment. Try another card.',
    )
    expect(paymentFailure(402, { declineReason: 'unknown_payment_method' })?.message).toBe(
      "That card isn't one the payment provider knows. Try another card.",
    )
  })

  it('still reads a 402 with a reason it has no words for, or none, as a decline', () => {
    expect(paymentFailure(402, { declineReason: 'lost_card' })).toMatchObject({
      kind: 'declined',
      reason: 'lost_card',
      message: 'Try another card.',
    })
    expect(paymentFailure(402, {})).toMatchObject({ kind: 'declined', reason: undefined })
  })

  it('reads a 410 that says the hold ran out as nothing charged, to start again', () => {
    expect(paymentFailure(410, { reason: 'holdExpired' })).toEqual({
      kind: 'holdExpired',
      title: 'Your hold ran out',
      message:
        'Your items stopped being held while you paid, so the card authorization was released and nothing was charged. Start again to check the prices and stock.',
    })
  })

  it('reads a 502 as the payment failing, nothing charged', () => {
    expect(paymentFailure(502, {})).toEqual({
      kind: 'failed',
      title: "The payment didn't go through",
      message: 'Something went wrong while taking the payment, and nothing was charged. Please try again.',
    })
  })

  it('is undefined for any other failure, an expired session included', () => {
    expect(paymentFailure(400, { detail: 'paying needs a paymentMethod' })).toBeUndefined()
    expect(paymentFailure(410, {})).toBeUndefined()
    expect(paymentFailure(503, { reason: 'checkoutUnavailable' })).toBeUndefined()
  })
})

describe('attemptFailure', () => {
  it('words each failed outcome as the answer to paying does', () => {
    expect(attemptFailure({ status: 'DECLINED', orderId: 'o-1', declineReason: 'insufficient_funds' })).toEqual(
      paymentFailure(402, { declineReason: 'insufficient_funds' }),
    )
    expect(attemptFailure({ status: 'HOLD_EXPIRED', orderId: 'o-1', declineReason: null })).toEqual(
      paymentFailure(410, { reason: 'holdExpired' }),
    )
    expect(attemptFailure({ status: 'FAILED', orderId: null, declineReason: null })).toEqual(paymentFailure(502, {}))
  })

  it('is undefined while it goes on, and once it is paid', () => {
    expect(attemptFailure({ status: 'PROCESSING', orderId: null, declineReason: null })).toBeUndefined()
    expect(attemptFailure({ status: 'PAID', orderId: 'o-1', declineReason: null })).toBeUndefined()
  })
})

describe('confirmation', () => {
  const since = 1_000_000
  const paid = { status: 'PAID', orderId: 'o-1', declineReason: null } as const
  const processing = { status: 'PROCESSING', orderId: null, declineReason: null } as const

  it('is confirming until an answer read since paying says otherwise', () => {
    expect(confirmation(undefined, since, since + 1500)).toEqual({ kind: 'confirming' })
    expect(confirmation({ attempt: processing, readAt: since + 1500 }, since, since + 1500)).toEqual({
      kind: 'confirming',
    })
  })

  it('ignores an answer read before paying, such as an earlier decline', () => {
    const declined = { status: 'DECLINED', orderId: 'o-0', declineReason: 'card_declined' } as const
    expect(confirmation({ attempt: declined, readAt: since - 1 }, since, since + 10)).toEqual({ kind: 'confirming' })
  })

  it('is the paid Order once the Saga pays it', () => {
    expect(confirmation({ attempt: paid, readAt: since + 3000 }, since, since + 3000)).toEqual({
      kind: 'paid',
      orderId: 'o-1',
    })
  })

  it('is the failure once the Saga ends without paying', () => {
    const held = { status: 'HOLD_EXPIRED', orderId: 'o-1', declineReason: null } as const
    expect(confirmation({ attempt: held, readAt: since + 3000 }, since, since + 3000)).toEqual({
      kind: 'failed',
      failure: paymentFailure(410, { reason: 'holdExpired' }),
    })
  })

  it('stops after two minutes still confirming', () => {
    expect(pollFor).toBe(120_000)
    expect(confirmation({ attempt: processing, readAt: since + 119_000 }, since, since + 119_999).kind).toBe(
      'confirming',
    )
    expect(confirmation({ attempt: processing, readAt: since + 119_000 }, since, since + 120_000)).toEqual({
      kind: 'stillConfirming',
    })
  })
})

describe('paymentStatusLabel', () => {
  it('names each Payment status as the Customer reads it', () => {
    expect(paymentStatuses.map(paymentStatusLabel)).toEqual([
      'Being confirmed',
      'Authorized',
      'Declined',
      'Released',
      'Charged',
      'Partly refunded',
      'Refunded',
    ])
  })
})

describe('paymentStatusNote', () => {
  it('says what each Payment status means for the Customer’s money', () => {
    expect(paymentStatusNote(payment('PENDING'))).toBe('Your bank is still confirming this payment.')
    expect(paymentStatusNote(payment('AUTHORIZED'))).toBe('The amount is held on your card.')
    expect(paymentStatusNote(payment('VOIDED'))).toBe('The hold on your card was released. Nothing was charged.')
    expect(paymentStatusNote(payment('CAPTURED'))).toBe('The amount was charged to your card.')
    expect(paymentStatusNote(payment('PARTIALLY_REFUNDED'))).toBe('Part of the amount was refunded to your card.')
    expect(paymentStatusNote(payment('REFUNDED'))).toBe('The whole amount was refunded to your card.')
  })

  it('says why a declined Payment was declined, in words for the reasons it knows', () => {
    expect(paymentStatusNote(payment('DECLINED', 'card_declined'))).toBe('Your bank declined the card.')
    expect(paymentStatusNote(payment('DECLINED', 'insufficient_funds'))).toBe("The card didn't have enough funds.")
    expect(paymentStatusNote(payment('DECLINED', 'unknown_payment_method'))).toBe(
      "The payment provider didn't know the card.",
    )
  })

  it('still gives the reason of a decline it has no words for, or says none was given', () => {
    expect(paymentStatusNote(payment('DECLINED', 'lost_card'))).toBe('The payment was declined (lost_card).')
    expect(paymentStatusNote(payment('DECLINED'))).toBe('The payment was declined.')
  })
})

describe('cancellationReason', () => {
  it('says a cancelled Order’s payment was declined, or its authorization released, by its latest Payment', () => {
    expect(cancellationReason('CANCELLED', [payment('DECLINED', 'card_declined')])).toBe('Payment declined')
    expect(cancellationReason('CANCELLED', [payment('VOIDED')])).toBe('Card authorization released')
    // Newest first, as Payment lists them.
    expect(cancellationReason('CANCELLED', [payment('VOIDED'), payment('DECLINED', 'card_declined')])).toBe(
      'Card authorization released',
    )
  })

  it('gives no reason for an Order that isn’t cancelled, or whose Payment doesn’t explain it', () => {
    expect(cancellationReason('PAID', [payment('AUTHORIZED')])).toBeUndefined()
    expect(cancellationReason('PLACED', [payment('DECLINED', 'card_declined')])).toBeUndefined()
    expect(cancellationReason('CANCELLED', [])).toBeUndefined()
    expect(cancellationReason('CANCELLED', [payment('AUTHORIZED')])).toBeUndefined()
  })
})

describe('paymentTransactionsOf', () => {
  const transaction = (
    kind: PaymentTransaction['kind'],
    outcome: PaymentTransaction['outcome'],
    at: string,
    declineReason: string | null = null,
  ): PaymentTransaction => ({
    kind,
    outcome,
    amount: { amountMinor: 79900, currency: 'EUR' },
    declineReason,
    at,
    reference: null,
  })

  it('says what each Payment transaction did to the Customer’s money, oldest first', () => {
    const rows = paymentTransactionsOf({
      transactions: [
        transaction('AUTHORIZATION', 'PENDING', '2026-10-03T12:00:00Z'),
        transaction('AUTHORIZATION', 'APPROVED', '2026-10-03T12:00:03Z'),
        transaction('CAPTURE', 'APPROVED', '2026-10-04T09:00:00Z'),
      ],
    })

    expect(rows.map((row) => [row.label, row.at])).toEqual([
      ['Sent to your bank', '2026-10-03T12:00:00Z'],
      ['Amount held', '2026-10-03T12:00:03Z'],
      ['Charged', '2026-10-04T09:00:00Z'],
    ])
    expect(rows[2].amount).toEqual({ amountMinor: 79900, currency: 'EUR' })
  })

  it('names declines, a refused charge and a released hold', () => {
    const rows = paymentTransactionsOf({
      transactions: [
        transaction('AUTHORIZATION', 'DECLINED', '2026-10-03T12:00:00Z', 'card_declined'),
        transaction('CAPTURE', 'DECLINED', '2026-10-03T12:00:01Z', 'authorization_expired'),
        transaction('VOID', 'APPROVED', '2026-10-03T12:00:02Z'),
      ],
    })

    expect(rows.map((row) => row.label)).toEqual(['Declined', 'Charge refused', 'Hold released'])
  })

  it('names a refund, and a refused one', () => {
    const rows = paymentTransactionsOf({
      transactions: [
        transaction('REFUND', 'APPROVED', '2026-10-20T09:00:00Z'),
        transaction('REFUND', 'DECLINED', '2026-10-20T09:01:00Z', 'refund_window_closed'),
      ],
    })

    expect(rows.map((row) => row.label)).toEqual(['Refunded', 'Refund refused'])
  })

  it('names the RMA a refund was for', () => {
    const rows = paymentTransactionsOf({
      transactions: [
        { ...transaction('REFUND', 'APPROVED', '2026-10-20T09:00:00Z'), reference: 'RMA-3F2A9C1B' },
        { ...transaction('REFUND', 'DECLINED', '2026-10-20T09:01:00Z'), reference: 'RMA-3F2A9C1B' },
      ],
    })

    expect(rows.map((row) => row.label)).toEqual(['Refunded for RMA-3F2A9C1B', 'Refund refused for RMA-3F2A9C1B'])
  })
})

describe('refundedOf', () => {
  const refund = (amountMinor: number, outcome: PaymentTransaction['outcome']): PaymentTransaction => ({
    kind: 'REFUND',
    outcome,
    amount: { amountMinor, currency: 'EUR' },
    declineReason: null,
    at: '2026-10-20T09:00:00Z',
    reference: null,
  })

  it('adds up the approved refunds, leaving out refused ones', () => {
    expect(
      refundedOf({
        amount: { amountMinor: 79900, currency: 'EUR' },
        transactions: [refund(30000, 'APPROVED'), refund(10000, 'DECLINED'), refund(5000, 'APPROVED')],
      }),
    ).toEqual({ amountMinor: 35000, currency: 'EUR' })
  })

  it('is nothing in the Payment’s Currency when nothing was refunded', () => {
    expect(refundedOf({ amount: { amountMinor: 79900, currency: 'EUR' }, transactions: [] })).toEqual({
      amountMinor: 0,
      currency: 'EUR',
    })
  })
})

function payment(status: PaymentStatus, declineReason: string | null = null): Payment {
  return {
    id: 'p-1',
    orderId: 'o-1',
    amount: { amountMinor: 79900, currency: 'EUR' },
    status,
    declineReason,
    transactions: [],
  }
}
