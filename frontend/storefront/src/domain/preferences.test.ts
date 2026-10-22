import { describe, expect, it } from 'vitest'
import { preferencesErrorsOf, preferencesRequestOf } from './preferences'

describe('preferencesErrorsOf', () => {
  it('takes a mobile number in international form, spaces and all', () => {
    expect(preferencesErrorsOf({ phoneNumber: '+49 151 12345678', sms: true, push: false })).toEqual({})
  })

  it('refuses one without a plus and country code, or too long', () => {
    for (const phoneNumber of ['015112345678', '+0151123', '+49 151 abc', '+4915112345678901']) {
      expect(preferencesErrorsOf({ phoneNumber, sms: false, push: false }).phoneNumber).toMatch(/international form/)
    }
  })

  it('asks for a mobile number when SMS is on', () => {
    expect(preferencesErrorsOf({ phoneNumber: ' ', sms: true, push: true }).phoneNumber).toBe(
      'Enter your mobile number to get text messages.',
    )
  })

  it('needs no number with SMS off', () => {
    expect(preferencesErrorsOf({ phoneNumber: '', sms: false, push: true })).toEqual({})
  })
})

describe('preferencesRequestOf', () => {
  it('sends the number without spaces, and none when it is blank', () => {
    expect(preferencesRequestOf({ phoneNumber: '+49 151 12345678', sms: true, push: false })).toEqual({
      phoneNumber: '+4915112345678',
      sms: true,
      push: false,
    })
    expect(preferencesRequestOf({ phoneNumber: '  ', sms: false, push: true })).toEqual({
      phoneNumber: null,
      sms: false,
      push: true,
    })
  })
})
