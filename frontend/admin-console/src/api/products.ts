import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Product, ProductRequest, Stock, StockChange } from '../domain/product'
import { isNotFound } from './failure'
import { api } from './http'

const productsKey = ['products'] as const
const stockKey = ['stock'] as const
// A Category's Product count changes when a Product is created, moved or deleted.
const categoriesKey = ['categories'] as const

/** Products ordered by name, only those of `category` when it is given. */
export function useProducts(category: string | undefined) {
  return useQuery({
    queryKey: [...productsKey, { category }],
    queryFn: () =>
      api<Product[]>('catalog', category ? `/products?category=${encodeURIComponent(category)}` : '/products'),
  })
}

export function useProduct(sku: string | undefined) {
  return useQuery({
    queryKey: [...productsKey, sku],
    queryFn: () => api<Product>('catalog', `/products/${encodeURIComponent(sku!)}`),
    enabled: sku !== undefined,
  })
}

/** Each Variant's Stock, in the order of `variantIds`; null for a Variant Inventory doesn't stock yet. */
export function useStocks(variantIds: string[]) {
  return useQueries({
    queries: variantIds.map((variantId) => ({
      queryKey: [...stockKey, variantId],
      queryFn: () =>
        api<Stock>('inventory', `/stock/${encodeURIComponent(variantId)}`).catch((error: unknown) => {
          if (isNotFound(error)) return null
          throw error
        }),
    })),
  })
}

/** What saving a Product came to: Catalog's answer, and each Stock change Inventory refused. */
export interface SavedProduct {
  product: Product
  stockFailures: { change: StockChange; error: unknown }[]
}

/**
 * Creates the Product, or replaces the one named `sku`, and then sets the On-hand count of each
 * changed Variant through Inventory. Catalog must have the Variants before their Stock is set, and
 * a refused Stock change doesn't undo the Product: it is reported in `stockFailures`.
 */
export function useSaveProduct() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      sku,
      request,
      changes,
    }: {
      sku: string | undefined
      request: ProductRequest
      changes: StockChange[]
    }): Promise<SavedProduct> => {
      const product =
        sku === undefined
          ? await api<Product>('catalog', '/products', { method: 'POST', body: request, token })
          : await api<Product>('catalog', `/products/${encodeURIComponent(sku)}`, {
              method: 'PUT',
              body: request,
              token,
            })
      const results = await Promise.allSettled(
        changes.map((change) =>
          api<Stock>('inventory', `/stock/${encodeURIComponent(change.variantId)}`, {
            method: 'PUT',
            body: { onHand: change.onHand },
            token,
          }),
        ),
      )
      const stockFailures = results.flatMap((result, i) =>
        result.status === 'rejected' ? [{ change: changes[i]!, error: result.reason as unknown }] : [],
      )
      return { product, stockFailures }
    },
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: productsKey }),
        queryClient.invalidateQueries({ queryKey: stockKey }),
        queryClient.invalidateQueries({ queryKey: categoriesKey }),
      ]),
  })
}

/** Deletes a Product with its Variants, which frees their IDs. Inventory keeps their Stock rows. */
export function useDeleteProduct() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sku: string) =>
      api<void>('catalog', `/products/${encodeURIComponent(sku)}`, { method: 'DELETE', token }),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: productsKey }),
        queryClient.invalidateQueries({ queryKey: categoriesKey }),
      ]),
  })
}
