import { describe, expect, it } from 'vitest'
import {
  packRequestOf,
  queueOf,
  queueSearchOf,
  serialCheckOf,
  serialFieldOf,
  shipmentStatusLabel,
  shipmentsQueryOf,
  trackingStatusLabel,
  unitsOf,
} from './shipment'

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

describe('shipmentStatusLabel', () => {
  it('reads each Shipment status as Staff say it', () => {
    expect(shipmentStatusLabel('AWAITING_PICK')).toBe('Awaiting pick')
    expect(shipmentStatusLabel('PICKED')).toBe('Picked')
    expect(shipmentStatusLabel('PACKED')).toBe('Packed')
    expect(shipmentStatusLabel('SHIPPED')).toBe('Shipped')
    expect(shipmentStatusLabel('DELIVERED')).toBe('Delivered')
    expect(shipmentStatusLabel('ON_HOLD')).toBe('On hold')
  })
})

describe('trackingStatusLabel', () => {
  it('reads each Tracking event as the Carrier means it', () => {
    expect(trackingStatusLabel('IN_TRANSIT')).toBe('In transit')
    expect(trackingStatusLabel('OUT_FOR_DELIVERY')).toBe('Out for delivery')
    expect(trackingStatusLabel('DELIVERED')).toBe('Delivered')
  })
})

describe('the fulfillment queue in the URL', () => {
  it('opens on the Shipments awaiting pick, first page, by default', () => {
    expect(queueOf(new URLSearchParams())).toEqual({ status: 'AWAITING_PICK', page: 0 })
  })

  it('reads a queued status and a page counted from 1, ignoring what it doesn’t know', () => {
    expect(queueOf(new URLSearchParams('status=ON_HOLD&page=2'))).toEqual({ status: 'ON_HOLD', page: 1 })
    // Shipped and delivered Shipments are out of Staff's hands: no tab of their own.
    expect(queueOf(new URLSearchParams('status=SHIPPED&page=0'))).toEqual({ status: 'AWAITING_PICK', page: 0 })
  })

  it('writes it back, leaving out the defaults', () => {
    expect(queueSearchOf({ status: 'AWAITING_PICK', page: 0 })).toBe('')
    expect(queueSearchOf({ status: 'PACKED', page: 2 })).toBe('?status=PACKED&page=3')
  })

  it('asks for that status’s page from the chosen Warehouse, or from every one', () => {
    expect(shipmentsQueryOf({ status: 'PICKED', page: 1 }, 'WH-AMS', 20)).toBe(
      '?status=PICKED&page=1&size=20&warehouseId=WH-AMS',
    )
    expect(shipmentsQueryOf({ status: 'PICKED', page: 0 }, undefined, 1)).toBe('?status=PICKED&page=0&size=1')
  })
})

describe('unitsOf', () => {
  it('counts every unit on every line', () => {
    expect(unitsOf({ lines: [{ quantity: 2 }, { quantity: 1 }] })).toBe(3)
  })
})
