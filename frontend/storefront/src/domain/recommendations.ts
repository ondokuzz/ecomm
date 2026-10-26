/**
 * Why a Product is recommended with another: counting Orders bought both, or it is one of the
 * other's Category's best sellers, filling up when too few were bought together.
 */
export type RecommendationReason = 'BOUGHT_TOGETHER' | 'POPULAR_IN_CATEGORY'

/**
 * A Recommendation: a Product, with how many counting Orders bought it, with the other or, popular
 * in its Category, at all. `category` is the name of the Category it is popular in. Recommendations
 * from before Sprint 6 have no reason, and were bought together.
 */
export interface Recommendation {
  sku: string
  orders: number
  reason?: RecommendationReason
  category?: string
}

/** What Customers also bought with a Product, most bought together first, then its Category's best sellers. */
export interface AlsoBought {
  sku: string
  items: Recommendation[]
}

/** The label on a card filled from the Category's best sellers, "Popular in Audio"; none on one bought together. */
export function recommendationLabel(item: Recommendation): string | undefined {
  return item.reason === 'POPULAR_IN_CATEGORY' ? `Popular in ${item.category}` : undefined
}

/** The row's heading: "Customers also bought" while any was bought together, "Popular in <Category>" when none was. */
export function alsoBoughtHeading(items: Recommendation[]): string {
  const label =
    items.length > 0 && items.every((item) => recommendationLabel(item)) ? recommendationLabel(items[0]) : undefined
  return label ?? 'Customers also bought'
}
