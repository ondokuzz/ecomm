/** How each unit of a Variant is identified when it ships, as Catalog's Serial tracking says. */
export type SerialTracking = 'NONE' | 'SERIAL' | 'IMEI'

/**
 * A Serial number as Staff type it, checked as Fulfillment & Shipping will: valid, with the value
 * it keeps; still being typed; or wrong already, whatever comes next.
 */
export type SerialCheck =
  | { state: 'valid'; serialNumber: string }
  | { state: 'incomplete'; message: string }
  | { state: 'invalid'; message: string }

const imeiLength = 15
const serialForm = /^[A-Za-z0-9-]*$/
const serialMin = 4
const serialMax = 40

/**
 * Checks one unit's Serial number as it is typed or scanned. An IMEI is 15 digits, the last a Luhn
 * check digit over the other 14; a serial number is 4 to 40 letters, digits and hyphens, kept
 * upper-cased. Something that can't become one is invalid at once; something too short is only
 * incomplete, which a Pack refuses but typing doesn't flag.
 */
export function serialCheckOf(tracking: Exclude<SerialTracking, 'NONE'>, entered: string): SerialCheck {
  const typed = entered.trim()
  if (tracking === 'IMEI') {
    if (!/^\d*$/.test(typed) || typed.length > imeiLength) return { state: 'invalid', message: 'An IMEI is 15 digits.' }
    if (typed.length < imeiLength) return { state: 'incomplete', message: 'Enter the IMEI: 15 digits.' }
    if (!luhn(typed)) {
      return { state: 'invalid', message: 'Not a valid IMEI: its check digit is wrong. Check for a typo.' }
    }
    return { state: 'valid', serialNumber: typed }
  }
  if (!serialForm.test(typed) || typed.length > serialMax) {
    return { state: 'invalid', message: 'A serial number is 4 to 40 letters, digits and hyphens.' }
  }
  if (typed.length < serialMin) return { state: 'incomplete', message: 'Enter the serial number.' }
  return { state: 'valid', serialNumber: typed.toUpperCase() }
}

/** Whether the last digit is the Luhn check digit over the others. */
function luhn(digits: string): boolean {
  let sum = 0
  for (let i = 0; i < digits.length; i++) {
    let digit = Number(digits[digits.length - 1 - i])
    if (i % 2 === 1) {
      digit *= 2
      if (digit > 9) digit -= 9
    }
    sum += digit
  }
  return sum % 10 === 0
}
