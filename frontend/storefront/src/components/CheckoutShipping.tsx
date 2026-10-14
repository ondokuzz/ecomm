import { type FormEvent, useId, useState } from 'react'
import { addressProblemOf, isSessionOver, useSessionShippingAddress, useSessionShippingOption } from '../api/checkout'
import { useFormatMoney } from '../api/currencies'
import type { CheckoutSession } from '../domain/checkout'
import {
  type AddressField,
  type AddressForm,
  addressFormOf,
  addressLines,
  shippingOptionLabel,
  shippingOptionName,
} from '../domain/shipping'
import { ErrorMessage } from './Status'
import { Button } from './ui/Button'
import { Card } from './ui/Card'
import { Icon } from './ui/Icon'
import { Input } from './ui/Input'

/** The countries the store ships to: the EU's 27, as the mock Carrier and address validator serve. */
const countries = [
  'AT',
  'BE',
  'BG',
  'HR',
  'CY',
  'CZ',
  'DK',
  'EE',
  'FI',
  'FR',
  'DE',
  'GR',
  'HU',
  'IE',
  'IT',
  'LV',
  'LT',
  'LU',
  'MT',
  'NL',
  'PL',
  'PT',
  'RO',
  'SK',
  'SI',
  'ES',
  'SE',
]

const countryNames = new Intl.DisplayNames(undefined, { type: 'region' })

/**
 * Where the Order goes and how: the Shipping address form, each field's problem shown against it,
 * then, once Checkout has validated the address, the normalised address and the Shipping options
 * quoted for it, each with its Carrier, days and cost.
 */
export function CheckoutShipping({
  session,
  disabled,
  onChange,
}: {
  session: CheckoutSession
  disabled: boolean
  /** Called before the address or option changes, since the amount to pay does too. */
  onChange: () => void
}) {
  const address = useSessionShippingAddress()
  const option = useSessionShippingOption()
  const [editing, setEditing] = useState(session.shippingAddress === null)
  const formatMoney = useFormatMoney()
  const optionsName = useId()

  return (
    <Card className="shipping">
      <h2>Shipping</h2>
      {editing || session.shippingAddress === null ? (
        <AddressFormFields
          initial={addressFormOf(session.shippingAddress)}
          disabled={disabled}
          mutation={address}
          canCancel={session.shippingAddress !== null}
          onCancel={() => setEditing(false)}
          onSubmit={(form) => {
            onChange()
            address.mutate({ sessionId: session.id, form }, { onSuccess: () => setEditing(false) })
          }}
        />
      ) : (
        <div className="shipping-address">
          <address aria-label="Shipping address">
            {addressLines(session.shippingAddress).map((line) => (
              <span key={line}>{line}</span>
            ))}
          </address>
          <Button variant="ghost" size="sm" onClick={() => setEditing(true)} disabled={disabled}>
            Change address
          </Button>
        </div>
      )}
      {!editing && session.shippingOptions.length > 0 && (
        <fieldset className="test-cards shipping-options" disabled={disabled || option.isPending}>
          <legend>Shipping option</legend>
          {session.shippingOptions.map((quoted) => (
            <label key={quoted.id} className="test-card">
              <input
                type="radio"
                name={optionsName}
                value={quoted.id}
                checked={session.shippingOption?.id === quoted.id}
                onChange={() => {
                  onChange()
                  option.mutate({ sessionId: session.id, optionId: quoted.id })
                }}
              />
              <span className="test-card-label">{shippingOptionName(quoted.id)}</span>
              <span className="shipping-option-cost">{formatMoney(quoted.cost)}</span>
              <span className="test-card-outcome muted">{shippingOptionLabel(quoted)}</span>
            </label>
          ))}
        </fieldset>
      )}
      {option.error && !isSessionOver(option.error) && <ErrorMessage error={option.error} />}
    </Card>
  )
}

const fields: { field: AddressField; label: string; optional?: boolean; autoComplete: string }[] = [
  { field: 'recipientName', label: 'Full name', autoComplete: 'name' },
  { field: 'line1', label: 'Address', autoComplete: 'address-line1' },
  { field: 'line2', label: 'Address line 2', optional: true, autoComplete: 'address-line2' },
  { field: 'postcode', label: 'Postcode', autoComplete: 'postal-code' },
  { field: 'city', label: 'City', autoComplete: 'address-level2' },
  { field: 'region', label: 'Region', optional: true, autoComplete: 'address-level1' },
  { field: 'phone', label: 'Phone', optional: true, autoComplete: 'tel' },
]

function AddressFormFields({
  initial,
  disabled,
  mutation,
  canCancel,
  onCancel,
  onSubmit,
}: {
  initial: AddressForm
  disabled: boolean
  mutation: ReturnType<typeof useSessionShippingAddress>
  canCancel: boolean
  onCancel: () => void
  onSubmit: (form: AddressForm) => void
}) {
  const [form, setForm] = useState<AddressForm>({ ...initial, country: initial.country || 'DE' })
  const id = useId()
  const problem = addressProblemOf(mutation.error)
  const errors = problem?.kind === 'fields' ? problem.errors : {}
  const otherError = mutation.error && !problem && !isSessionOver(mutation.error) ? mutation.error : undefined

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit(form)
  }
  const set = (field: AddressField, value: string) => setForm((current) => ({ ...current, [field]: value }))
  const describedBy = (field: AddressField) => (errors[field] ? `${id}-${field}-error` : undefined)

  return (
    <form className="address-form" onSubmit={submit} noValidate aria-label="Shipping address">
      {fields.map(({ field, label, optional, autoComplete }) => (
        <div key={field} className={`field address-field address-${field}`}>
          <label htmlFor={`${id}-${field}`}>
            {label}
            {optional && <span className="muted"> (optional)</span>}
          </label>
          <Input
            id={`${id}-${field}`}
            value={form[field]}
            onChange={(event) => set(field, event.target.value)}
            autoComplete={autoComplete}
            disabled={disabled || mutation.isPending}
            aria-invalid={errors[field] ? true : undefined}
            aria-describedby={describedBy(field)}
          />
          {errors[field] && (
            <span id={`${id}-${field}-error`} className="address-error">
              {errors[field]}
            </span>
          )}
        </div>
      ))}
      <div className="field address-field address-country">
        <label htmlFor={`${id}-country`}>Country</label>
        <select
          id={`${id}-country`}
          className="input"
          value={form.country}
          onChange={(event) => set('country', event.target.value)}
          disabled={disabled || mutation.isPending}
          aria-invalid={errors.country ? true : undefined}
          aria-describedby={describedBy('country')}
        >
          {countries.map((code) => (
            <option key={code} value={code}>
              {countryNames.of(code) ?? code}
            </option>
          ))}
        </select>
        {errors.country && (
          <span id={`${id}-country-error`} className="address-error">
            {errors.country}
          </span>
        )}
      </div>
      {(problem?.kind === 'undeliverable' || problem?.kind === 'unavailable') && (
        <p className="alert alert-danger address-problem" role="alert">
          <Icon name="alert" size={18} />
          <span>{problem.message}</span>
        </p>
      )}
      {otherError && <ErrorMessage error={otherError} />}
      <div className="address-actions">
        <Button type="submit" variant="primary" loading={mutation.isPending} disabled={disabled}>
          Use this address
        </Button>
        {canCancel && (
          <Button variant="ghost" onClick={onCancel} disabled={mutation.isPending}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}
