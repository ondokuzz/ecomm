import { useState } from 'react'
import { type QuantityBounds, clampQuantity, parseQuantity } from '../../domain/quantity'
import { Button } from './Button'
import { Icon } from './Icon'

/** A quantity with − and + buttons; typing is allowed and settles when the field loses focus. */
export function QuantityStepper({
  value,
  onChange,
  min = 1,
  max,
  disabled,
  label = 'Quantity',
}: {
  value: number
  onChange: (quantity: number) => void
  disabled?: boolean
  /** Names the field for screen readers, e.g. "Quantity of Google Pixel 9". */
  label?: string
} & Partial<QuantityBounds>) {
  const bounds = { min, max }
  // What the Customer is typing; undefined while the field shows `value`.
  const [draft, setDraft] = useState<string>()

  const commit = (quantity: number) => {
    setDraft(undefined)
    const next = clampQuantity(quantity, bounds)
    if (next !== value) onChange(next)
  }

  return (
    <div className="stepper" role="group" aria-label={label}>
      <Button
        variant="ghost"
        aria-label="Decrease quantity"
        onClick={() => commit(value - 1)}
        disabled={disabled || value <= min}
      >
        <Icon name="minus" size={16} />
      </Button>
      <input
        type="number"
        inputMode="numeric"
        aria-label={label}
        min={min}
        max={max}
        value={draft ?? value}
        disabled={disabled}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={() => {
          const typed = draft === undefined ? undefined : parseQuantity(draft, bounds)
          if (typed === undefined) setDraft(undefined)
          else commit(typed)
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur()
        }}
      />
      <Button
        variant="ghost"
        aria-label="Increase quantity"
        onClick={() => commit(value + 1)}
        disabled={disabled || (max !== undefined && value >= max)}
      >
        <Icon name="plus" size={16} />
      </Button>
    </div>
  )
}
