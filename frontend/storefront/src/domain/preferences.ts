/**
 * A Customer's Notification preferences, as Notifications keeps them: email is always on; SMS needs a
 * mobile number in E.164 form; push needs nothing.
 */
export interface Preferences {
  email: true
  sms: boolean
  phoneNumber: string | null
  push: boolean
  /** When they were last saved; null until the Customer first saves them. */
  updatedAt: string | null
}

/** The preferences form as the Customer fills it in. */
export interface PreferencesForm {
  phoneNumber: string
  sms: boolean
  push: boolean
}

export interface PreferencesRequest {
  phoneNumber: string | null
  sms: boolean
  push: boolean
}

/** E.164: a plus, a country code that doesn't start with 0, and at most 15 digits in all. */
const e164 = /^\+[1-9]\d{1,14}$/

export function preferencesFormOf(preferences: Preferences): PreferencesForm {
  return { phoneNumber: preferences.phoneNumber ?? '', sms: preferences.sms, push: preferences.push }
}

/** The number as Notifications takes it: spaces dropped, and a blank one none. */
function phoneNumberOf(form: PreferencesForm): string | null {
  const number = form.phoneNumber.replaceAll(' ', '')
  return number === '' ? null : number
}

/** What's wrong with the form before it is sent, by field; Notifications checks the same again. */
export function preferencesErrorsOf(form: PreferencesForm): { phoneNumber?: string } {
  const number = phoneNumberOf(form)
  if (number === null) return form.sms ? { phoneNumber: 'Enter your mobile number to get text messages.' } : {}
  if (!e164.test(number)) {
    return {
      phoneNumber:
        'Enter it in international form: a plus, the country code, then the number, such as +49 151 12345678.',
    }
  }
  return {}
}

export function preferencesRequestOf(form: PreferencesForm): PreferencesRequest {
  return { phoneNumber: phoneNumberOf(form), sms: form.sms, push: form.push }
}
