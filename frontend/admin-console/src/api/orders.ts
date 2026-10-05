import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Order } from '../domain/order'
import type { Page } from '../domain/paging'
import { api } from './http'

const ordersKey = ['orders'] as const

/**
 * A page of every Customer's Orders, newest first, that match `query` (see `ordersQueryOf`); none
 * is fetched while it is undefined. The page on show stays until the next arrives. Staff only.
 */
export function useOrders(query: string | undefined) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...ordersKey, 'list', query],
    queryFn: () => api<Page<Order>>('order-management', `/staff/orders${query}`, { token }),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  })
}

/** Any Customer's Order, with its Status history. Staff only. */
export function useOrder(id: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...ordersKey, id],
    queryFn: () => api<Order>('order-management', `/staff/orders/${encodeURIComponent(id)}`, { token }),
  })
}
