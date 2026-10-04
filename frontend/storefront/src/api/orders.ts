import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { type Order, ordersPageSize } from '../domain/order'
import type { Page } from '../domain/paging'
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

/** A page of the Customer's Orders, newest first, counting from 0, with how many they have in all. */
export function useOrders(page: number) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...ordersKey, 'page', page],
    queryFn: () => api<Page<Order>>('order-management', `/orders?page=${page}&size=${ordersPageSize}`, { token }),
    enabled: token !== undefined,
    placeholderData: keepPreviousData,
  })
}
