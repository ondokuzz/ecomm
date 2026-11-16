import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { type Order, lastShippingAddress, ordersPageSize } from '../domain/order'
import type { ShippingAddress } from '../domain/shipping'
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
    queryKey: ordersPageKey(page),
    queryFn: () => ordersPage(token, page),
    enabled: token !== undefined,
    placeholderData: keepPreviousData,
  })
}

const ordersPageKey = (page: number) => [...ordersKey, 'page', page]

const ordersPage = (token: string | undefined, page: number) =>
  api<Page<Order>>('order-management', `/orders?page=${page}&size=${ordersPageSize}`, { token })

/**
 * The Shipping address of the Customer's latest Order with one, from the first page of their Orders,
 * as My Orders reads it, while `enabled`: undefined while it is read, null when no Order there has
 * one or it was never read, since checkout then simply asks for an address.
 */
export function useLastShippingAddress(enabled: boolean): ShippingAddress | null | undefined {
  const token = useAuth().user?.access_token
  const orders = useQuery({
    queryKey: ordersPageKey(0),
    queryFn: () => ordersPage(token, 0),
    enabled: enabled && token !== undefined,
    select: (page) => lastShippingAddress(page.items) ?? null,
  })
  if (!enabled) return null
  // A read that failed after one that answered keeps the address it had.
  if (orders.data !== undefined) return orders.data
  return orders.isError ? null : undefined
}
