import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { type Cart, type PricedCart, priceCart } from '../domain/cart'
import { useVariants } from './catalog'
import type { LookupFailure } from './failure'
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

/**
 * The Cart priced from Catalog's current Prices, while each line's Variant loads; `variantsFailure`
 * says when Catalog couldn't be asked, which leaves the lines unpriced.
 */
export function usePricedCart(): {
  cart: ReturnType<typeof useCart>
  priced?: PricedCart
  variantsFailure?: LookupFailure
} {
  const cart = useCart()
  const { variants, failure } = useVariants((cart.data?.items ?? []).map((item) => item.variantId))
  if (!cart.data) return { cart }
  return { cart, priced: priceCart(cart.data, variants), variantsFailure: failure }
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

/** Empties the Customer's whole Cart. */
export function useClearCart() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('cart', '/cart', { method: 'DELETE', token }),
    onSuccess: () => queryClient.setQueryData<Cart>(cartKey, { items: [] }),
  })
}
