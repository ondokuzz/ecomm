import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Order } from '../domain/order'
import { type Shipment, trackingRefreshInterval } from '../domain/shipment'
import { api } from './http'

/** The query key of an Order's Shipment. */
export function shipmentKey(orderId: string) {
  return ['shipments', orderId]
}

/**
 * The Customer's own Order's Shipment, from Fulfillment & Shipping, asked again every 10 s while it
 * is being prepared or it or its Order is `Shipped`, until the Saga's outcome is final (see
 * `trackingRefreshInterval`). An Order without one is a 404, which isn't retried.
 */
export function useShipment(orderId: string) {
  const token = useAuth().user?.access_token
  const client = useQueryClient()
  return useQuery({
    queryKey: shipmentKey(orderId),
    queryFn: () =>
      api<Shipment>('fulfillment-shipping', `/shipments?orderId=${encodeURIComponent(orderId)}`, { token }),
    enabled: token !== undefined,
    // The Order's key is spelled out rather than imported, since orders.ts imports this module.
    refetchInterval: (query) =>
      trackingRefreshInterval(client.getQueryData<Order>(['orders', orderId]), query.state.data?.status),
  })
}
