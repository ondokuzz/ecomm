import { describe, expect, it } from 'vitest'
import { summaryRows } from './order'
import {
  addressLines,
  addressProblem,
  emptyAddressForm,
  shippingOptionLabel,
  shippingOptionName,
  shippingRequest,
} from './shipping'

const eur = (amountMinor: number) => ({ amountMinor, currency: 'EUR' })

describe('addressProblem', () => {
  it('names each field the validator refused', () => {
    expect(
      addressProblem(422, {
        reason: 'invalidShippingAddress',
        errors: [
          { field: 'postcode', message: "isn't a postcode in DE, such as 10115" },
          { field: 'line1', message: 'is required' },
        ],
      }),
    ).toEqual({
      kind: 'fields',
      errors: { postcode: "Isn't a postcode in DE, such as 10115", line1: 'Is required' },
    })
  })

  it('says plainly when the store does not ship to the country', () => {
    expect(addressProblem(422, { reason: 'undeliverableCountry', country: 'US' })).toEqual({
      kind: 'undeliverable',
      message: "Sorry, we don't ship to US.",
    })
  })

  it('says shipping cannot be quoted when Fulfillment & Shipping is down', () => {
    expect(addressProblem(503, { reason: 'shippingUnavailable' })).toEqual({
      kind: 'unavailable',
      message: "We can't work out shipping right now. Please try again in a moment.",
    })
  })

  it('leaves any other problem alone', () => {
    expect(addressProblem(502, {})).toBeUndefined()
    expect(addressProblem(422, { reason: 'somethingElse' })).toBeUndefined()
  })

  it('ignores errors that name no field', () => {
    expect(addressProblem(422, { reason: 'invalidShippingAddress', errors: [{ message: 'x' }, 'y'] })).toEqual({
      kind: 'fields',
      errors: {},
    })
  })
})

describe('shippingRequest', () => {
  it('sends optional fields left blank as absent', () => {
    expect(
      shippingRequest({
        ...emptyAddressForm,
        recipientName: 'Erika Mustermann',
        line1: 'Hauptstraße 5',
        line2: '  ',
        postcode: '10115',
        city: 'Berlin',
        country: 'DE',
      }),
    ).toEqual({
      recipientName: 'Erika Mustermann',
      line1: 'Hauptstraße 5',
      postcode: '10115',
      city: 'Berlin',
      country: 'DE',
    })
  })
})

describe('shipping options', () => {
  const express = { id: 'EXPRESS', carrier: 'Mock Carrier', cost: eur(999), estimatedDays: 1 }

  it('names an option by its ID in words', () => {
    expect(shippingOptionName('EXPRESS')).toBe('Express')
    expect(shippingOptionName('STANDARD')).toBe('Standard')
  })

  it('labels an option with its Carrier and how long it takes', () => {
    expect(shippingOptionLabel(express)).toBe('Mock Carrier · 1 day')
    expect(shippingOptionLabel({ ...express, estimatedDays: 4 })).toBe('Mock Carrier · 4 days')
  })
})

describe('addressLines', () => {
  it('writes an address as a label would, leaving out what it lacks', () => {
    expect(
      addressLines({
        recipientName: 'Jan Jansen',
        line1: 'Damrak 1',
        line2: '3e verdieping',
        postcode: '1012 LG',
        city: 'Amsterdam',
        country: 'NL',
        phone: '+31 20 1234567',
      }),
    ).toEqual(['Jan Jansen', 'Damrak 1', '3e verdieping', '1012 LG Amsterdam', 'NL', '+31 20 1234567'])
    expect(
      addressLines({
        recipientName: 'Erika',
        line1: 'Hauptstraße 5',
        postcode: '10115',
        city: 'Berlin',
        country: 'DE',
      }),
    ).toEqual(['Erika', 'Hauptstraße 5', '10115 Berlin', 'DE'])
  })
})

describe('summaryRows with Shipping', () => {
  it('shows the chosen option and its cost between the Discounts and the tax', () => {
    const rows = summaryRows({
      subtotal: eur(159800),
      discounts: [],
      tax: eur(0),
      shippingOption: { id: 'EXPRESS', carrier: 'Mock Carrier', cost: eur(999) },
      total: eur(160799),
    })
    expect(rows.map((row) => `${row.label}:${row.amount.amountMinor}`)).toEqual([
      'Subtotal:159800',
      'Shipping (Express):999',
      'Tax:0',
      'Total:160799',
    ])
  })

  it('shows no shipping row until an option is chosen, or for an Order placed before Shipping', () => {
    const rows = summaryRows({ subtotal: eur(100), discounts: [], tax: eur(0), shippingOption: null, total: eur(100) })
    expect(rows.map((row) => row.label)).toEqual(['Subtotal', 'Tax', 'Total'])
  })
})
