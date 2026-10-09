import { describe, expect, it } from 'vitest'
import { attemptFailure, confirmation, defaultTestCard, paymentFailure, pollFor, testCards } from './payment'

describe('testCards', () => {
  it('offers one card per mock gateway outcome, approving first', () => {
    expect(testCards.map((card) => card.token)).toEqual([
      'tok_approve',
      'tok_decline',
      'tok_insufficient_funds',
      'tok_gateway_error',
    ])
    expect(defaultTestCard).toBe(testCards[0])
  })

  it('gives each card its own number', () => {
    expect(new Set(testCards.map((card) => card.last4)).size).toBe(testCards.length)
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
