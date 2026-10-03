import { describe, expect, it } from 'vitest'
import { type Campaign, type CampaignForm, campaignRequest, formOf, switched } from './campaign'
import { currenciesOf } from './money'

const currencies = currenciesOf([
  { code: 'EUR', minorDigits: 2 },
  { code: 'JPY', minorDigits: 0 },
  { code: 'BHD', minorDigits: 3 },
])

// The tests run in Asia/Tokyo, nine hours ahead of UTC (see vite.config.ts).
const now = new Date('2026-10-03T08:15:42Z')

const springSale: Campaign = {
  id: '7f1c2a3b-0000-4000-8000-000000000001',
  name: 'Spring sale',
  discount: { type: 'AMOUNT_OFF', percentOff: null, amountOff: { amountMinor: 2500, currency: 'EUR' } },
  categories: ['phones', 'audio'],
  minimumSubtotal: { amountMinor: 10000, currency: 'EUR' },
  validFrom: '2027-03-01T00:00:00Z',
  validUntil: '2027-06-01T00:00:00Z',
  active: true,
  priority: 20,
  state: 'scheduled',
}

const audioWeek: Campaign = {
  id: '7f1c2a3b-0000-4000-8000-000000000002',
  name: 'Audio week',
  discount: { type: 'PERCENT_OFF', percentOff: 15, amountOff: null },
  categories: ['audio'],
  minimumSubtotal: null,
  validFrom: '2026-10-01T09:30:00Z',
  validUntil: '2027-10-01T09:30:00Z',
  active: true,
  priority: 10,
  state: 'running',
}

const blankForm = (): CampaignForm => formOf(undefined, currencies, now)

describe('formOf', () => {
  it('lays a fixed-amount Campaign out for editing: Money as decimals in its currency, times in local time', () => {
    expect(formOf(springSale, currencies, now)).toEqual({
      name: 'Spring sale',
      discountType: 'AMOUNT_OFF',
      percentOff: '',
      amountOff: { amount: '25.00', currency: 'EUR' },
      minimumSubtotal: { amount: '100.00', currency: 'EUR' },
      validFrom: '2027-03-01T09:00',
      validUntil: '2027-06-01T09:00',
      active: true,
      categories: ['phones', 'audio'],
      priority: '20',
    })
  })

  it('lays a percentage Campaign with no minimum out with blank amounts', () => {
    expect(formOf(audioWeek, currencies, now)).toMatchObject({
      discountType: 'PERCENT_OFF',
      percentOff: '15',
      amountOff: { amount: '', currency: 'EUR' },
      minimumSubtotal: { amount: '', currency: 'EUR' },
      validFrom: '2026-10-01T18:30',
      categories: ['audio'],
    })
  })

  it('reads each amount by the minor unit Catalog gives its own currency', () => {
    const yen: Campaign = {
      ...springSale,
      discount: { type: 'AMOUNT_OFF', percentOff: null, amountOff: { amountMinor: 500, currency: 'JPY' } },
      minimumSubtotal: { amountMinor: 12345, currency: 'BHD' },
    }
    expect(formOf(yen, currencies, now)).toMatchObject({
      amountOff: { amount: '500', currency: 'JPY' },
      minimumSubtotal: { amount: '12.345', currency: 'BHD' },
    })
  })

  it('starts a new Campaign as a percentage off everything, active from this minute for a week', () => {
    expect(blankForm()).toEqual({
      name: '',
      discountType: 'PERCENT_OFF',
      percentOff: '',
      amountOff: { amount: '', currency: 'EUR' },
      minimumSubtotal: { amount: '', currency: 'EUR' },
      validFrom: '2026-10-03T17:15',
      validUntil: '2026-10-10T17:15',
      active: true,
      categories: [],
      priority: '',
    })
  })
})

describe('campaignRequest', () => {
  it('turns the form back into the Campaign it was laid out from', () => {
    for (const campaign of [springSale, audioWeek]) {
      const { id: _, state: __, ...request } = campaign
      expect(campaignRequest(formOf(campaign, currencies, now), currencies)).toEqual({ request, errors: {} })
    }
  })

  it('sends decimals as Money in their currency, digit by digit', () => {
    const form: CampaignForm = {
      ...blankForm(),
      name: '  Big spend  ',
      discountType: 'AMOUNT_OFF',
      amountOff: { amount: ' 4.35 ', currency: 'eur' },
      minimumSubtotal: { amount: '1000', currency: 'JPY' },
      priority: ' 7 ',
    }
    expect(campaignRequest(form, currencies).request).toMatchObject({
      name: 'Big spend',
      discount: { type: 'AMOUNT_OFF', percentOff: null, amountOff: { amountMinor: 435, currency: 'EUR' } },
      minimumSubtotal: { amountMinor: 1000, currency: 'JPY' },
      priority: 7,
    })
  })

  it('sends a percentage with no amount, and no minimum when it is blank', () => {
    const form: CampaignForm = {
      ...blankForm(),
      name: 'Ten off',
      percentOff: '10',
      amountOff: { amount: '5.00', currency: 'EUR' },
      priority: '3',
    }
    expect(campaignRequest(form, currencies).request).toMatchObject({
      discount: { type: 'PERCENT_OFF', percentOff: 10, amountOff: null },
      minimumSubtotal: null,
    })
  })

  it('sends local times as instants', () => {
    const form: CampaignForm = { ...blankForm(), name: 'Night', percentOff: '5', priority: '1' }
    form.validFrom = '2027-01-01T00:00'
    form.validUntil = '2027-01-02T08:59'
    expect(campaignRequest(form, currencies).request).toMatchObject({
      validFrom: '2026-12-31T15:00:00Z',
      validUntil: '2027-01-01T23:59:00Z',
    })
  })

  it('names each field it can’t send, as Promotions would', () => {
    const form: CampaignForm = {
      ...blankForm(),
      discountType: 'AMOUNT_OFF',
      amountOff: { amount: '25.001', currency: 'EUR' },
      minimumSubtotal: { amount: '100', currency: 'XYZ' },
      validFrom: '',
      validUntil: 'soon',
      priority: '1.5',
    }
    expect(campaignRequest(form, currencies)).toEqual({
      errors: {
        'discount.amountOff': 'must be an amount such as 25.00',
        'minimumSubtotal.currency': 'must be a currency Catalog prices in, such as EUR',
        validFrom: 'must be a date and time',
        validUntil: 'must be a date and time',
        priority: 'must be a whole number, 0 or more',
      },
    })
  })

  it('refuses a percentage that isn’t a whole number from 1 to 100', () => {
    for (const percentOff of ['', '0', '101', '12.5', 'ten']) {
      expect(campaignRequest({ ...blankForm(), percentOff, priority: '1' }, currencies).errors).toEqual({
        'discount.percentOff': 'must be a whole number from 1 to 100',
      })
    }
  })

  it('leaves the rest for Promotions to name, such as a blank name or a window that ends before it starts', () => {
    const form: CampaignForm = { ...blankForm(), name: ' ', percentOff: '10', priority: '1' }
    form.validUntil = form.validFrom
    expect(campaignRequest(form, currencies).errors).toEqual({})
  })
})

describe('switched', () => {
  it('is the Campaign as Staff send it, switched on or off', () => {
    const { id: _, state: __, ...request } = audioWeek
    expect(switched(audioWeek, false)).toEqual({ ...request, active: false })
  })
})
