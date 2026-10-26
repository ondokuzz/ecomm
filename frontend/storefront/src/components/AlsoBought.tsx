import { useQueries } from '@tanstack/react-query'
import { productQuery } from '../api/catalog'
import { useAlsoBought } from '../api/recommendations'
import { useRatingSummaries } from '../api/reviews'
import { type Product, productCardOf } from '../domain/catalog'
import { alsoBoughtHeading, recommendationLabel } from '../domain/recommendations'
import { ProductCard } from './ProductCard'

/**
 * "Customers also bought": a row of cards for the Products bought most often with this one, filled
 * up with its Category's best sellers, each of those labelled "Popular in <Category>", from
 * Catalog's data and Reviews' Rating summaries. When none was bought together, the row is headed
 * "Popular in <Category>" instead. Nothing shows until there is something to show, so it is hidden
 * while it loads, when there are no Recommendations, and when Recommendations or Catalog can't be
 * asked.
 */
export function AlsoBought({ sku }: { sku: string }) {
  const items = useAlsoBought(sku).data?.items ?? []
  const skus = items.map((item) => item.sku)
  const products = useQueries({ queries: skus.map(productQuery) })
  const summaries = useRatingSummaries(skus)
  // In Recommendations' order; one Catalog doesn't have, or couldn't be asked about, is left out.
  const shown = items.flatMap((item, i) => {
    const product: Product | null | undefined = products[i]?.data
    return product ? [{ item, product }] : []
  })

  if (shown.length === 0) return null
  const heading = alsoBoughtHeading(shown.map(({ item }) => item))
  return (
    <section className="also-bought" aria-labelledby="also-bought-heading">
      <h2 id="also-bought-heading">{heading}</h2>
      <ul className="product-grid" aria-label={heading}>
        {shown.map(({ item, product }) => (
          <ProductCard
            key={product.sku}
            product={productCardOf(product)}
            summary={summaries.data?.[product.sku]}
            label={recommendationLabel(item)}
          />
        ))}
      </ul>
    </section>
  )
}
