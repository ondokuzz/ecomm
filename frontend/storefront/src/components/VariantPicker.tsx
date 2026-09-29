import { useId } from 'react'
import type { Variant, VariantAxis } from '../domain/catalog'
import { cx } from './ui/cx'

/**
 * One group of radio buttons per Variant axis. A value no Variant has alongside the chosen one's other
 * axis values is disabled; one whose Variant is sold out stays pickable but is marked.
 */
export function VariantPicker({ axes, onChoose }: { axes: VariantAxis[]; onChoose: (variant: Variant) => void }) {
  const id = useId()
  return (
    <div className="variant-picker">
      {axes.map((axis) => (
        <fieldset key={axis.name} className="variant-axis">
          <legend className="capitalize">{axis.name}</legend>
          <div className="variant-options">
            {axis.options.map((option) => (
              <label
                key={option.value}
                className={cx('variant-option', option.outOfStock && 'out-of-stock', !option.variant && 'unavailable')}
                title={option.variant ? undefined : 'Not available with the other options you picked'}
              >
                <input
                  type="radio"
                  name={`${id}-${axis.name}`}
                  value={option.value}
                  checked={option.selected}
                  disabled={!option.variant}
                  onChange={() => option.variant && onChoose(option.variant)}
                  className="variant-option-input"
                />
                {option.value}
                {option.outOfStock && <span className="variant-option-note">Sold out</span>}
              </label>
            ))}
          </div>
        </fieldset>
      ))}
    </div>
  )
}
