import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { type Order, ordersPageSize } from '../domain/order'
import type { Page } from '../domain/paging'
import { type Shipment, trackingRefreshInterval } from '../domain/shipment'
import { api } from './http'
import { shipmentKey } from './shipments'

export const ordersKey = ['orders']

/**
 * One of the Customer's Orders, asked again every 10 s while its Shipment is being prepared or it or
 * its Shipment is `Shipped`, so `Shipped` and `Delivered` show, until the Saga's outcome is final
 * (see `trackingRefreshInterval`).
 */
export function useOrder(id: string) {
  const token = useAuth().user?.access_token
  const client = useQueryClient()
  return useQuery({
    queryKey: [...ordersKey, id],
    queryFn: () => api<Order>('order-management', `/orders/${encodeURIComponent(id)}`, { token }),
    enabled: token !== undefined,
    refetchInterval: (query) =>
      trackingRefreshInterval(query.state.data, client.getQueryData<Shipment>(shipmentKey(id))?.status),
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
