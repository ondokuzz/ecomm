import type { Money } from './money'

/**
 * Where an Order goes, as Checkout validated and normalised it: the optional fields are absent when
 * the Customer left them out.
 */
export interface ShippingAddress {
  recipientName: string
  line1: string
  line2?: string
  postcode: string
  city: string
  region?: string
  /** An ISO 3166-1 alpha-2 code, such as DE. */
  country: string
  phone?: string
}

/** A Shipping option as an Order records it: its ID, its Carrier and its Shipping cost. */
export interface OrderShippingOption {
  id: string
  carrier: string
  cost: Money
}

/** A Shipping option quoted for a Checkout Session's address, with how many days it should take. */
export interface ShippingOption extends OrderShippingOption {
  estimatedDays: number
}

export type AddressField = 'recipientName' | 'line1' | 'line2' | 'postcode' | 'city' | 'region' | 'country' | 'phone'

/** The address form as the Customer types it: every field a string, maybe blank. */
export type AddressForm = Record<AddressField, string>

export const emptyAddressForm: AddressForm = {
  recipientName: '',
  line1: '',
  line2: '',
  postcode: '',
  city: '',
  region: '',
  country: '',
  phone: '',
}

/** The form filled from an address the session already has, to change it. */
export function addressFormOf(address: ShippingAddress | null | undefined): AddressForm {
  if (!address) return emptyAddressForm
  return { ...emptyAddressForm, ...address }
}

/** The body Checkout takes: blank optional fields left out, the rest as typed, since Checkout normalises. */
export function shippingRequest(form: AddressForm): Partial<ShippingAddress> {
  const body: Record<string, string> = {}
  for (const [field, value] of Object.entries(form)) {
    if (value.trim() !== '') body[field] = value
  }
  return body
}

/** Why an address was refused, as the Customer is told: against each field, or once for the address. */
export type AddressProblem =
  | { kind: 'fields'; errors: Partial<Record<AddressField, string>> }
  | { kind: 'undeliverable'; message: string }
  | { kind: 'unavailable'; message: string }

const capitalised = (text: string) => text.charAt(0).toUpperCase() + text.slice(1)

/** Reads Checkout's answer to a refused address; undefined for any other failure. */
export function addressProblem(status: number, problem: Record<string, unknown>): AddressProblem | undefined {
  if (status === 503 && problem.reason === 'shippingUnavailable') {
    return { kind: 'unavailable', message: "We can't work out shipping right now. Please try again in a moment." }
  }
  if (status !== 422) return undefined
  if (problem.reason === 'undeliverableCountry') {
    return { kind: 'undeliverable', message: `Sorry, we don't ship to ${String(problem.country ?? 'that country')}.` }
  }
  if (problem.reason !== 'invalidShippingAddress') return undefined
  const errors: Partial<Record<AddressField, string>> = {}
  for (const error of Array.isArray(problem.errors) ? problem.errors : []) {
    if (error && typeof error === 'object' && typeof error.field === 'string' && typeof error.message === 'string') {
      errors[error.field as AddressField] = capitalised(error.message)
    }
  }
  return { kind: 'fields', errors }
}

/** A Shipping option's ID in words: "Express" for EXPRESS. */
export function shippingOptionName(id: string): string {
  return capitalised(id.toLowerCase().replaceAll('_', ' '))
}

/** What a Shipping option is, beside its name: "Mock Carrier · 2 days". */
export function shippingOptionLabel(option: ShippingOption): string {
  return `${option.carrier} · ${option.estimatedDays === 1 ? '1 day' : `${option.estimatedDays} days`}`
}

/** An address as a label would show it, one line each, leaving out what it lacks. */
export function addressLines(address: ShippingAddress): string[] {
  return [
    address.recipientName,
    address.line1,
    address.line2,
    `${address.postcode} ${address.city}`,
    address.region,
    address.country,
    address.phone,
  ].filter((line): line is string => Boolean(line))
}
