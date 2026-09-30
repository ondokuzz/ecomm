import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Order } from '../domain/order'
import { api } from './http'

export const ordersKey = ['orders']

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
