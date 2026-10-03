import { useId } from 'react'
import type { Currencies } from '../domain/money'
import type { DiscountType, MoneyForm, TermsForm } from '../domain/promotion'
import { FieldError } from './Status'
import { Input } from './ui/Input'
import type { FieldErrorsState } from './useFieldErrors'

/**
 * The fields a Coupon and a Campaign share: the discount, the optional minimum subtotal, the
 * validity window in local time and whether it is switched on. Money is typed as a decimal in one
 * of the currencies Catalog prices in, as Prices are.
 */
export function TermsFields({
  form,
  onChange,
  currencies,
  errors: fieldErrors,
}: {
  form: TermsForm
  onChange: (change: Partial<TermsForm>) => void
  currencies: Currencies
  errors: FieldErrorsState
}) {
  const { errors, errorId, field, clearError } = fieldErrors
  const id = useId()
  const setType = (discountType: DiscountType) => {
    onChange({ discountType })
    clearError('discount', 'discount.type', 'discount.percentOff', 'discount.amountOff')
  }
  return (
    <>
      <section className="panel" aria-labelledby={`${id}-discount`}>
        <h2 id={`${id}-discount`}>Discount</h2>
        <fieldset className="choice-row">
          <legend className="visually-hidden">Discount type</legend>
          <label>
            <input
              type="radio"
              name={`${id}-type`}
              checked={form.discountType === 'PERCENT_OFF'}
              onChange={() => setType('PERCENT_OFF')}
            />
            Percentage off
          </label>
          <label>
            <input
              type="radio"
              name={`${id}-type`}
              checked={form.discountType === 'AMOUNT_OFF'}
              onChange={() => setType('AMOUNT_OFF')}
            />
            Fixed amount off
          </label>
        </fieldset>
        <FieldError id={errorId('discount')} message={errors.discount ?? errors['discount.type']} />
        <div className="form-grid">
          {form.discountType === 'PERCENT_OFF' ? (
            <label className="field">
              Percentage
              <Input
                inputMode="numeric"
                value={form.percentOff}
                onChange={(e) => {
                  onChange({ percentOff: e.target.value })
                  clearError('discount.percentOff')
                }}
                {...field('discount.percentOff')}
              />
              <span className="hint">A whole number from 1 to 100. It is rounded down to the currency's minor unit.</span>
              <FieldError id={errorId('discount.percentOff')} message={errors['discount.percentOff']} />
            </label>
          ) : (
            <MoneyField
              label="Amount off"
              name="discount.amountOff"
              value={form.amountOff}
              onChange={(amountOff) => onChange({ amountOff })}
              currencies={currencies}
              errors={fieldErrors}
              hint="Never more than the subtotal it is taken off."
              placeholder="25.00"
            />
          )}
          <MoneyField
            label="Minimum subtotal"
            name="minimumSubtotal"
            value={form.minimumSubtotal}
            onChange={(minimumSubtotal) => onChange({ minimumSubtotal })}
            currencies={currencies}
            errors={fieldErrors}
            hint="Optional. Leave the amount blank for no minimum."
            placeholder="None"
          />
        </div>
      </section>

      <section className="panel" aria-labelledby={`${id}-validity`}>
        <h2 id={`${id}-validity`}>Validity</h2>
        <div className="form-grid">
          <label className="field">
            Valid from
            <Input
              type="datetime-local"
              value={form.validFrom}
              onChange={(e) => {
                onChange({ validFrom: e.target.value })
                clearError('validFrom', 'validUntil')
              }}
              {...field('validFrom')}
            />
            <FieldError id={errorId('validFrom')} message={errors.validFrom} />
          </label>
          <label className="field">
            Valid until
            <Input
              type="datetime-local"
              value={form.validUntil}
              onChange={(e) => {
                onChange({ validUntil: e.target.value })
                clearError('validUntil')
              }}
              {...field('validUntil')}
            />
            <span className="hint">Up to, but not including, this minute. Times are in your own time zone.</span>
            <FieldError id={errorId('validUntil')} message={errors.validUntil} />
          </label>
        </div>
        <label className="check">
          <input
            type="checkbox"
            checked={form.active}
            onChange={(e) => {
              onChange({ active: e.target.checked })
              clearError('active')
            }}
            {...field('active')}
          />
          Switched on
        </label>
        <FieldError id={errorId('active')} message={errors.active} />
      </section>
    </>
  )
}

/**
 * An amount typed as a decimal and the currency it is in, which must be one Catalog prices in.
 * Promotions names the amount as `name` or `name.amountMinor`, and the currency as `name.currency`.
 */
function MoneyField({
  label,
  name,
  value,
  onChange,
  currencies,
  errors: { errors, errorId, field, clearError },
  hint,
  placeholder,
}: {
  label: string
  name: string
  value: MoneyForm
  onChange: (value: MoneyForm) => void
  currencies: Currencies
  errors: FieldErrorsState
  hint: string
  placeholder: string
}) {
  const listId = useId()
  const amountError = errors[name] ?? errors[`${name}.amountMinor`]
  const amountErrorName = errors[name] ? name : `${name}.amountMinor`
  const currencyName = `${name}.currency`
  return (
    <div className="field">
      <span aria-hidden="true">{label}</span>
      <div className="money-field">
        <Input
          aria-label={label}
          inputMode="decimal"
          placeholder={placeholder}
          value={value.amount}
          onChange={(e) => {
            onChange({ ...value, amount: e.target.value })
            clearError(name, `${name}.amountMinor`)
          }}
          {...field(name, `${name}.amountMinor`)}
        />
        <Input
          aria-label={`${label} currency`}
          className="currency-input"
          value={value.currency}
          onChange={(e) => {
            onChange({ ...value, currency: e.target.value.toUpperCase() })
            clearError(currencyName, name, `${name}.amountMinor`)
          }}
          spellCheck={false}
          autoCapitalize="characters"
          list={`${listId}-currencies`}
          {...field(currencyName)}
        />
        <datalist id={`${listId}-currencies`}>
          {[...currencies.keys()].map((code) => (
            <option key={code} value={code} />
          ))}
        </datalist>
      </div>
      <span className="hint">{hint}</span>
      <FieldError id={errorId(amountErrorName)} message={amountError} />
      <FieldError id={errorId(currencyName)} message={errors[currencyName]} />
    </div>
  )
}
