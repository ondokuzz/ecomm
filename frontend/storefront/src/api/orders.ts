import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { CheckoutResult, Order } from '../domain/order'
import { cartKey } from './cart'
import { stockKey } from './catalog'
import { api } from './http'

const ordersKey = ['orders']

/** Checks out the Customer's Cart: Checkout prices it, takes Stock, authorizes payment and places the Order. */
export function useCheckout() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<CheckoutResult>('checkout-pricing', '/checkout', { method: 'POST', token }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: cartKey })
      queryClient.invalidateQueries({ queryKey: ordersKey })
      queryClient.invalidateQueries({ queryKey: stockKey })
    },
  })
}

export function useOrder(id: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...ordersKey, id],
    queryFn: () => api<Order>('order-management', `/orders/${encodeURIComponent(id)}`, { token }),
    enabled: token !== undefined,
  })
}

/** The Customer's Orders, newest first. */
export function useOrders() {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ordersKey,
    queryFn: () => api<Order[]>('order-management', '/orders', { token }),
    enabled: token !== undefined,
  })
}
