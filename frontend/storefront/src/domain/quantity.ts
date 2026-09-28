/** The quantities a quantity stepper allows; `max` is left out when there is no known limit. */
export interface QuantityBounds {
  min: number
  max?: number
}

/** A whole quantity within the bounds; `min` wins when the bounds cross. */
export function clampQuantity(quantity: number, { min, max }: QuantityBounds): number {
  const whole = Math.floor(quantity)
  return Math.max(min, max === undefined ? whole : Math.min(whole, max))
}

/** What the Customer typed as a quantity, clamped; undefined when it isn't a number. */
export function parseQuantity(text: string, bounds: QuantityBounds): number | undefined {
  if (text.trim() === '') return undefined
  const quantity = Number(text)
  return Number.isFinite(quantity) ? clampQuantity(quantity, bounds) : undefined
}
