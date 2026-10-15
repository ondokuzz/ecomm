import { describe, expect, it } from 'vitest'
import { packRequestOf, serialCheckOf, serialFieldOf } from './shipment'

describe('serialCheckOf, as Staff type a Serial number', () => {
  it('takes an IMEI whose last digit is its Luhn check digit', () => {
    expect(serialCheckOf('IMEI', '490154203237518')).toEqual({ state: 'valid', serialNumber: '490154203237518' })
  })

  it('refuses an IMEI whose check digit is wrong as soon as all 15 digits are in', () => {
    expect(serialCheckOf('IMEI', '490154203237517')).toEqual({
      state: 'invalid',
      message: 'Not a valid IMEI: its check digit is wrong. Check for a typo.',
    })
  })

  it('waits for the rest of an IMEI while it is being typed', () => {
    expect(serialCheckOf('IMEI', '')).toEqual({ state: 'incomplete', message: 'Enter the IMEI: 15 digits.' })
    expect(serialCheckOf('IMEI', '4901542')).toEqual({ state: 'incomplete', message: 'Enter the IMEI: 15 digits.' })
  })

  it('refuses at once an IMEI with anything but digits, or too many', () => {
    expect(serialCheckOf('IMEI', '49015420323751A')).toEqual({ state: 'invalid', message: 'An IMEI is 15 digits.' })
    expect(serialCheckOf('IMEI', '4901542032375180')).toEqual({ state: 'invalid', message: 'An IMEI is 15 digits.' })
  })

  it('ignores spaces around what is typed or scanned', () => {
    expect(serialCheckOf('IMEI', ' 490154203237518 ')).toEqual({ state: 'valid', serialNumber: '490154203237518' })
  })

  it('takes a serial number of 4 to 40 letters, digits and hyphens, upper-cased', () => {
    expect(serialCheckOf('SERIAL', 'c02-xk9')).toEqual({ state: 'valid', serialNumber: 'C02-XK9' })
    expect(serialCheckOf('SERIAL', 'A'.repeat(40))).toEqual({ state: 'valid', serialNumber: 'A'.repeat(40) })
  })

  it('refuses a serial number with other characters, or over 40, at once', () => {
    const message = 'A serial number is 4 to 40 letters, digits and hyphens.'
    expect(serialCheckOf('SERIAL', 'C02 XK9')).toEqual({ state: 'invalid', message })
    expect(serialCheckOf('SERIAL', 'A'.repeat(41))).toEqual({ state: 'invalid', message })
  })

  it('waits for a serial number shorter than 4 while it is being typed', () => {
    expect(serialCheckOf('SERIAL', 'C0')).toEqual({ state: 'incomplete', message: 'Enter the serial number.' })
  })
})

describe('packRequestOf', () => {
  const phone = { variantId: 'PHN-PIXEL-9-OBSIDIAN-128', quantity: 2, serialTracking: 'IMEI' as const }
  const laptop = { variantId: 'LPT-XPS-13-16', quantity: 1, serialTracking: 'SERIAL' as const }
  const case_ = { variantId: 'ACC-CASE', quantity: 3, serialTracking: 'NONE' as const }

  it('names each unit’s input as Fulfillment & Shipping names it in a violation', () => {
    expect(serialFieldOf('PHN-1', 0)).toBe('serialNumbers.PHN-1[0]')
  })

  it('sends one Serial number per unit of each serial-tracked line, as kept, and nothing for the others', () => {
    const entered = {
      [phone.variantId]: ['490154203237518', ' 356938035643809 '],
      [laptop.variantId]: ['c02-xk9'],
    }
    expect(packRequestOf([phone, laptop, case_], entered)).toEqual({
      body: {
        serialNumbers: {
          [phone.variantId]: ['490154203237518', '356938035643809'],
          [laptop.variantId]: ['C02-XK9'],
        },
      },
    })
  })

  it('packs a Shipment with no serial-tracked line with no Serial numbers', () => {
    expect(packRequestOf([case_], {})).toEqual({ body: { serialNumbers: {} } })
  })

  it('refuses each unit left empty, half typed or wrong, against its own input', () => {
    const entered = { [phone.variantId]: ['490154203237517'], [laptop.variantId]: ['C0'] }
    expect(packRequestOf([phone, laptop], entered)).toEqual({
      errors: {
        [`serialNumbers.${phone.variantId}[0]`]: 'Not a valid IMEI: its check digit is wrong. Check for a typo.',
        [`serialNumbers.${phone.variantId}[1]`]: 'Enter the IMEI: 15 digits.',
        [`serialNumbers.${laptop.variantId}[0]`]: 'Enter the serial number.',
      },
    })
  })

  it('refuses a Serial number entered twice in the Shipment, against the second', () => {
    const entered = { [phone.variantId]: ['490154203237518', '490154203237518'] }
    expect(packRequestOf([phone], entered)).toEqual({
      errors: { [`serialNumbers.${phone.variantId}[1]`]: 'Entered twice in this Shipment.' },
    })
  })
})
