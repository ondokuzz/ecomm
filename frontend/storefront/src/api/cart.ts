import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { type Cart, type PricedCart, priceCart } from '../domain/cart'
import type { Product } from '../domain/catalog'
import { productQuery } from './catalog'
import { api } from './http'

export const cartKey = ['cart']

/** The signed-in Customer's Cart; disabled until they sign in. */
export function useCart() {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: cartKey,
    queryFn: () => api<Cart>('cart', '/cart', { token }),
    enabled: token !== undefined,
  })
}

/** The Cart priced from Catalog's current Prices, while each line's Product loads. */
export function usePricedCart(): { cart: ReturnType<typeof useCart>; priced?: PricedCart } {
  const cart = useCart()
  // Until multi-Variant Products arrive, a Variant ID is its Product's SKU.
  const products = useQueries({
    queries: (cart.data?.items ?? []).map((item) => productQuery(item.variantId)),
  })
  if (!cart.data) return { cart }
  const productsByVariantId: Record<string, Product | undefined> = {}
  cart.data.items.forEach((item, i) => (productsByVariantId[item.variantId] = products[i]?.data ?? undefined))
  return { cart, priced: priceCart(cart.data, productsByVariantId) }
}

export function useSetQuantity() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ variantId, quantity }: { variantId: string; quantity: number }) =>
      api<Cart>('cart', `/cart/items/${encodeURIComponent(variantId)}`, { method: 'PUT', body: { quantity }, token }),
    onSuccess: (cart) => queryClient.setQueryData(cartKey, cart),
  })
}

export function useRemoveFromCart() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (variantId: string) =>
      api<Cart>('cart', `/cart/items/${encodeURIComponent(variantId)}`, { method: 'DELETE', token }),
    onSuccess: (cart) => queryClient.setQueryData(cartKey, cart),
  })
}
