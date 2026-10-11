import { useQueries } from '@tanstack/react-query'
import { productQuery } from '../api/catalog'
import { useAlsoBought } from '../api/recommendations'
import { useRatingSummaries } from '../api/reviews'
import { type Product, productCardOf } from '../domain/catalog'
import { ProductCard } from './ProductCard'

/**
 * "Customers also bought": a row of cards for the Products bought most often with this one, from
 * Catalog's data and Reviews' Rating summaries. Nothing shows until there is something to show, so
 * it is hidden while it loads, when nothing was bought with the Product, and when Recommendations
 * or Catalog can't be asked.
 */
export function AlsoBought({ sku }: { sku: string }) {
  const skus = useAlsoBought(sku).data?.items.map((item) => item.sku) ?? []
  const products = useQueries({ queries: skus.map(productQuery) })
  const summaries = useRatingSummaries(skus)
  // In Recommendations' order; one Catalog doesn't have, or couldn't be asked about, is left out.
  const shown = products.map((result) => result.data).filter((product): product is Product => !!product)

  if (shown.length === 0) return null
  return (
    <section className="also-bought" aria-labelledby="also-bought-heading">
      <h2 id="also-bought-heading">Customers also bought</h2>
      <ul className="product-grid" aria-label="Customers also bought">
        {shown.map((product) => (
          <ProductCard key={product.sku} product={productCardOf(product)} summary={summaries.data?.[product.sku]} />
        ))}
      </ul>
    </section>
  )
}
