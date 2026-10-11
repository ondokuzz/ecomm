import { useQueries, useQuery } from '@tanstack/react-query'
import type { Category, Product, VariantDetail } from '../domain/catalog'
import { type LookupFailure, isNotFound, lookupFailure } from './failure'
import { api } from './http'

/** One Category; null data once loaded means Catalog doesn't have it. */
export function useCategory(slug: string) {
  return useQuery({
    queryKey: ['category', slug],
    queryFn: () => api<Category>('catalog', `/categories/${encodeURIComponent(slug)}`).catch(orNullOn404),
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

/** The prefix of every Stock query, so a checkout can refresh them all. */
export const stockKey = ['stock']

/** The most Variants Inventory's batch read takes at once. */
const stockBatchSize = 50

/** Up to 50 Variants' available Stock in one request, by Variant ID; a Variant Inventory doesn't stock is left out. */
function stockBatchQuery(variantIds: string[]) {
  return {
    queryKey: [...stockKey, ...variantIds],
    queryFn: async () => {
      const query = new URLSearchParams({ variantIds: variantIds.join(',') })
      const stocks = await api<{ variantId: string; quantity: number }[]>('inventory', `/stock?${query}`)
      return Object.fromEntries(stocks.map((stock) => [stock.variantId, stock.quantity])) as Record<string, number>
    },
  }
}

/**
 * Several Variants' available Stock, read in batches; an ID maps to undefined until known, or when Inventory has
 * no record. `isPending` until every batch has answered; `failure` says when Inventory couldn't be asked.
 */
export function useStocks(variantIds: string[]): {
  stocks: Record<string, number | undefined>
  isPending: boolean
  failure?: LookupFailure
} {
  const unique = [...new Set(variantIds)]
  const batches = Array.from({ length: Math.ceil(unique.length / stockBatchSize) }, (_, i) =>
    unique.slice(i * stockBatchSize, (i + 1) * stockBatchSize),
  )
  const results = useQueries({ queries: batches.map(stockBatchQuery) })
  const known = Object.assign({}, ...results.map((result) => result.data ?? {})) as Record<string, number>
  return {
    stocks: Object.fromEntries(unique.map((variantId) => [variantId, known[variantId]])),
    isPending: results.some((result) => result.isPending),
    failure: lookupFailure(results),
  }
}

// Null rather than undefined: TanStack Query treats a query that resolves to undefined as failed.
function orNullOn404(error: unknown): null {
  if (isNotFound(error)) return null
  throw error
}
