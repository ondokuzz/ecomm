import { useQueries, useQuery } from '@tanstack/react-query'
import type { Category, Product, VariantDetail } from '../domain/catalog'
import { type LookupFailure, isNotFound, lookupFailure } from './failure'
import { api } from './http'

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: () => api<Category[]>('catalog', '/categories') })
}

/** One Category; null data once loaded means Catalog doesn't have it. */
export function useCategory(slug: string) {
  return useQuery({
    queryKey: ['category', slug],
    queryFn: () => api<Category>('catalog', `/categories/${encodeURIComponent(slug)}`).catch(orNullOn404),
  })
}

export function useProducts(category?: string) {
  return useQuery({
    queryKey: ['products', { category }],
    queryFn: () =>
      api<Product[]>('catalog', category ? `/products?category=${encodeURIComponent(category)}` : '/products'),
  })
}

/** One Product; null data once loaded means Catalog doesn't have it. */
export function productQuery(sku: string) {
  return {
    queryKey: ['product', sku],
    queryFn: () => api<Product>('catalog', `/products/${encodeURIComponent(sku)}`).catch(orNullOn404),
  }
}

export function useProduct(sku: string) {
  return useQuery(productQuery(sku))
}

/** One Variant with its Product's name and images; null data once loaded means Catalog doesn't have it. */
function variantQuery(variantId: string) {
  return {
    queryKey: ['variant', variantId],
    queryFn: () => api<VariantDetail>('catalog', `/variants/${encodeURIComponent(variantId)}`).catch(orNullOn404),
  }
}

/**
 * Some Variants, as each loads; an ID maps to undefined while its Variant loads, or when Catalog
 * doesn't have it. `failure` says when Catalog couldn't be asked, so the page can say so.
 */
export function useVariants(variantIds: string[]): {
  variants: Record<string, VariantDetail | undefined>
  failure?: LookupFailure
} {
  const unique = [...new Set(variantIds)]
  const results = useQueries({ queries: unique.map(variantQuery) })
  return {
    variants: Object.fromEntries(unique.map((variantId, i) => [variantId, results[i]?.data ?? undefined])),
    failure: lookupFailure(results),
  }
}

/** How many units of a Variant Inventory has; null data once loaded means it has no Stock record. */
export const stockKey = ['stock']

function stockQuery(variantId: string) {
  return {
    queryKey: [...stockKey, variantId],
    queryFn: () =>
      api<{ quantity: number }>('inventory', `/stock/${encodeURIComponent(variantId)}`)
        .then((stock) => stock.quantity)
        .catch(orNullOn404),
  }
}

export function useStock(variantId: string) {
  return useQuery(stockQuery(variantId))
}

/**
 * Several Variants' Stock, as each loads; an ID maps to undefined until known, or when Inventory has
 * no record. `failure` says when Inventory couldn't be asked.
 */
export function useStocks(variantIds: string[]): {
  stocks: Record<string, number | undefined>
  failure?: LookupFailure
} {
  const results = useQueries({ queries: variantIds.map(stockQuery) })
  return {
    stocks: Object.fromEntries(variantIds.map((variantId, i) => [variantId, results[i]?.data ?? undefined])),
    failure: lookupFailure(results),
  }
}

// Null rather than undefined: TanStack Query treats a query that resolves to undefined as failed.
function orNullOn404(error: unknown): null {
  if (isNotFound(error)) return null
  throw error
}
