/** At or below this many units, a Product page warns that Stock is running low. */
export const lowStockThreshold = 5

export type StockLevel = 'in' | 'low' | 'out' | 'unknown'

/** How a Variant's Stock reads to a Customer; `quantity` is undefined when Inventory has no Stock record. */
export function stockLevel(quantity: number | undefined): { level: StockLevel; label: string } {
  if (quantity === undefined) return { level: 'unknown', label: 'Stock unknown' }
  if (quantity <= 0) return { level: 'out', label: 'Out of stock' }
  if (quantity <= lowStockThreshold) return { level: 'low', label: `Only ${quantity} left` }
  return { level: 'in', label: 'In stock' }
}

/** How a Cart line's Stock reads; `short` when the Cart holds more than is available, so checkout would refuse it. */
export interface LineAvailability {
  level: 'low' | 'out'
  label: string
  short: boolean
}

/**
 * What a Cart line holding `wanted` units says about its Variant's Stock: out of stock, or how many are left when
 * that is fewer than the Cart holds or Stock runs low. Undefined, saying nothing, with plenty left or while unknown.
 */
export function lineAvailability(wanted: number, available: number | undefined): LineAvailability | undefined {
  if (available === undefined) return undefined
  if (available <= 0) return { level: 'out', label: 'Out of stock', short: true }
  if (available < wanted || available <= lowStockThreshold) {
    return { level: 'low', label: `Only ${available} left`, short: available < wanted }
  }
  return undefined
}

/** The lines, in order, whose quantity is more than their Variant has available; unknown Stock counts as enough. */
export function shortLines<L extends { variantId: string; quantity: number }>(
  lines: L[],
  available: Record<string, number | undefined>,
): L[] {
  return lines.filter((line) => lineAvailability(line.quantity, available[line.variantId])?.short)
}
