import { describe, expect, it } from 'vitest'
import { preferencesErrorsOf, preferencesFormOf, preferencesRequestOf } from './preferences'

describe('preferencesErrorsOf', () => {
  it('takes a mobile number in international form, spaces and all', () => {
    expect(
      preferencesErrorsOf({ phoneNumber: '+49 151 12345678', sms: true, push: false, cartReminders: false }),
    ).toEqual({})
  })

  it('refuses one without a plus and country code, or too long', () => {
    for (const phoneNumber of ['015112345678', '+0151123', '+49 151 abc', '+4915112345678901']) {
      expect(preferencesErrorsOf({ phoneNumber, sms: false, push: false, cartReminders: false }).phoneNumber).toMatch(
        /international form/,
      )
    }
  })

  it('asks for a mobile number when SMS is on', () => {
    expect(preferencesErrorsOf({ phoneNumber: ' ', sms: true, push: true, cartReminders: false }).phoneNumber).toBe(
      'Enter your mobile number to get text messages.',
    )
  })

  it('needs no number with SMS off', () => {
    expect(preferencesErrorsOf({ phoneNumber: '', sms: false, push: true, cartReminders: false })).toEqual({})
  })
})

describe('preferencesRequestOf', () => {
  it('sends the number without spaces, and none when it is blank', () => {
    expect(
      preferencesRequestOf({ phoneNumber: '+49 151 12345678', sms: true, push: false, cartReminders: false }),
    ).toEqual({
      phoneNumber: '+4915112345678',
      sms: true,
      push: false,
      cartReminders: false,
    })
    expect(preferencesRequestOf({ phoneNumber: '  ', sms: false, push: true, cartReminders: false })).toEqual({
      phoneNumber: null,
      sms: false,
      push: true,
      cartReminders: false,
    })
  })
})

describe('cart reminders', () => {
  it('are off for a Customer who never saved their preferences', () => {
    expect(
      preferencesFormOf({
        email: true,
        sms: false,
        phoneNumber: null,
        push: false,
        cartReminders: false,
        updatedAt: null,
      }).cartReminders,
    ).toBe(false)
  })

  it('are sent as the Customer set them', () => {
    expect(preferencesRequestOf({ phoneNumber: '', sms: false, push: false, cartReminders: true }).cartReminders).toBe(
      true,
    )
  })
})
