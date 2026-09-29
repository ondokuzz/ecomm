import { useQueries, useQuery } from '@tanstack/react-query'
import type { Category, Product } from '../domain/catalog'
import { ApiError, api } from './http'

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

/**
 * The Products some Variants belong to, as each loads; a Variant maps to undefined while its
 * Product loads, or when Catalog doesn't have it.
 */
export function useProductsByVariantId(variantIds: string[]): Record<string, Product | undefined> {
  const unique = [...new Set(variantIds)]
  // Until multi-Variant Products arrive, a Variant ID is its Product's SKU.
  const products = useQueries({ queries: unique.map((variantId) => productQuery(variantId)) })
  return Object.fromEntries(unique.map((variantId, i) => [variantId, products[i]?.data ?? undefined]))
}

/** How many units of a Variant Inventory has; null data once loaded means it has no Stock record. */
export const stockKey = ['stock']

export function useStock(variantId: string | undefined) {
  return useQuery({
    queryKey: [...stockKey, variantId],
    queryFn: () =>
      api<{ quantity: number }>('inventory', `/stock/${encodeURIComponent(variantId!)}`)
        .then((stock) => stock.quantity)
        .catch(orNullOn404),
    enabled: variantId !== undefined,
  })
}

// Null rather than undefined: TanStack Query treats a query that resolves to undefined as failed.
function orNullOn404(error: unknown): null {
  if (error instanceof ApiError && error.status === 404) return null
  throw error
}
