import { useQuery } from '@tanstack/react-query'
import type { Category, Product } from '../domain/catalog'
import { ApiError, api } from './http'

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: () => api<Category[]>('catalog', '/categories') })
}

export function useProducts(category?: string) {
  return useQuery({
    queryKey: ['products', { category }],
    queryFn: () =>
      api<Product[]>('catalog', category ? `/products?category=${encodeURIComponent(category)}` : '/products'),
  })
}

/** One Product; undefined data once loaded means Catalog doesn't have it. */
export function productQuery(sku: string) {
  return {
    queryKey: ['product', sku],
    queryFn: () => api<Product>('catalog', `/products/${encodeURIComponent(sku)}`).catch(orUndefinedOn404),
  }
}

export function useProduct(sku: string) {
  return useQuery(productQuery(sku))
}

/** How many units of a Variant Inventory has; undefined data once loaded means it has no Stock record. */
export const stockKey = ['stock']

export function useStock(variantId: string | undefined) {
  return useQuery({
    queryKey: [...stockKey, variantId],
    queryFn: () =>
      api<{ quantity: number }>('inventory', `/stock/${encodeURIComponent(variantId!)}`)
        .then((stock) => stock.quantity)
        .catch(orUndefinedOn404),
    enabled: variantId !== undefined,
  })
}

function orUndefinedOn404(error: unknown): undefined {
  if (error instanceof ApiError && error.status === 404) return undefined
  throw error
}
