import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Shipment } from '../domain/shipment'
import { api } from './http'

/**
 * The Customer's own Order's Shipment, from Fulfillment & Shipping, asked again every
 * `refetchInterval` ms when it is a number. An Order without one is a 404, which isn't retried.
 */
export function useShipment(orderId: string, refetchInterval: number | false) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['shipments', orderId],
    queryFn: () =>
      api<Shipment>('fulfillment-shipping', `/shipments?orderId=${encodeURIComponent(orderId)}`, { token }),
    enabled: token !== undefined,
    refetchInterval,
  })
}
