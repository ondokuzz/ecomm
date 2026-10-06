import { describe, expect, it } from 'vitest'
import { defaultTestCard, paymentFailure, testCards } from './payment'

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

  it('reads a 502 as the payment failing to go through', () => {
    expect(paymentFailure(502, {})).toEqual({
      kind: 'gatewayError',
      title: "The payment didn't go through",
      message:
        'Something went wrong on the way to the payment provider. Your items are still held, so please try again.',
    })
  })

  it('is undefined for any other failure', () => {
    expect(paymentFailure(400, { detail: 'paying needs a paymentMethod' })).toBeUndefined()
    expect(paymentFailure(503, {})).toBeUndefined()
  })
})
