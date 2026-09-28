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
