import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Page } from '../domain/paging'
import {
  type PackRequest,
  type QueuedStatus,
  type Shipment,
  type Warehouse,
  queuedStatuses,
  shipmentsQueryOf,
} from '../domain/shipment'
import { isNotFound } from './failure'
import { api } from './http'

const shipmentsKey = ['shipments'] as const

/**
 * How often a Shipment's page asks again while the fulfillment Saga or the Carrier has it: packed,
 * until the Saga dispatches or holds it; shipped, as Tracking events arrive.
 */
const followingInterval = 3_000

/** The Warehouses Shipments ship from, as configured. Staff only. */
export function useWarehouses() {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['warehouses'],
    queryFn: () => api<Warehouse[]>('fulfillment-shipping', '/warehouses', { token }),
    staleTime: Infinity,
  })
}

/**
 * A page of the fulfillment queue, oldest first, for `query` (see `shipmentsQueryOf`). The page on
 * show stays until the next arrives. Staff only.
 */
export function useShipments(query: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...shipmentsKey, 'list', query],
    queryFn: () => api<Page<Shipment>>('fulfillment-shipping', `/shipments${query}`, { token }),
    placeholderData: keepPreviousData,
  })
}

/** How many Shipments each tab of the queue holds, from one Warehouse or all; a count still loading is absent. */
export function useQueueCounts(warehouseId: string | undefined): Partial<Record<QueuedStatus, number>> {
  const token = useAuth().user?.access_token
  const counts = useQueries({
    queries: queuedStatuses.map((status) => {
      const query = shipmentsQueryOf({ status, page: 0 }, warehouseId, 1)
      return {
        queryKey: [...shipmentsKey, 'list', query],
        queryFn: () => api<Page<Shipment>>('fulfillment-shipping', `/shipments${query}`, { token }),
      }
    }),
  })
  return Object.fromEntries(
    queuedStatuses.flatMap((status, i) => {
      const total = counts[i].data?.total
      return total === undefined ? [] : [[status, total]]
    }),
  )
}

/** Any Shipment, followed while the fulfillment Saga or the Carrier is moving it on. Staff only. */
export function useShipment(id: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...shipmentsKey, id],
    queryFn: () => api<Shipment>('fulfillment-shipping', `/shipments/${encodeURIComponent(id)}`, { token }),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'PACKED' || status === 'SHIPPED' ? followingInterval : false
    },
  })
}

/** An Order's Shipment, or null when it has none: placed before Sprint 5, or not paid yet. Staff only. */
export function useOrderShipment(orderId: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...shipmentsKey, 'order', orderId],
    queryFn: () =>
      api<Shipment>('fulfillment-shipping', `/shipments?${new URLSearchParams({ orderId })}`, { token }).catch(
        (error: unknown) => {
          if (isNotFound(error)) return null
          throw error
        },
      ),
  })
}

/**
 * The replacement Shipment the returns Saga created for an RMA, or null while it has none, asked
 * again until it has. Staff only.
 */
export function useReplacement(rmaId: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    ...replacementQuery(rmaId, token),
    refetchInterval: (query) => (query.state.data === null ? followingInterval : false),
  })
}

/** The replacements of several RMAs, in their order. */
export function useReplacements(rmaIds: readonly string[]) {
  const token = useAuth().user?.access_token
  return useQueries({ queries: rmaIds.map((rmaId) => replacementQuery(rmaId, token)) })
}

function replacementQuery(rmaId: string, token: string | undefined) {
  return {
    queryKey: [...shipmentsKey, 'rma', rmaId],
    queryFn: () =>
      api<Shipment>('fulfillment-shipping', `/shipments?${new URLSearchParams({ rmaId })}`, { token }).catch(
        (error: unknown) => {
          if (isNotFound(error)) return null
          throw error
        },
      ),
  }
}

/**
 * Picks or packs a Shipment as the signed-in Staff member. Another status than the one it moves
 * from is a 409 `illegalTransition`: someone else moved it first. A Pack's refused Serial numbers
 * are a 422 naming each field, or a 409 `serialAlreadyShipped`.
 */
export function useMoveShipment(id: string) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (move: { step: 'pick' } | { step: 'pack'; body: PackRequest }) =>
      api<Shipment>('fulfillment-shipping', `/shipments/${encodeURIComponent(id)}/${move.step}`, {
        method: 'POST',
        body: move.step === 'pack' ? move.body : undefined,
        token,
      }),
    onSuccess: (shipment) => {
      queryClient.setQueryData([...shipmentsKey, id], shipment)
      return queryClient.invalidateQueries({ queryKey: shipmentsKey })
    },
    // Moved elsewhere: show it as it is now.
    onError: () => queryClient.invalidateQueries({ queryKey: [...shipmentsKey, id] }),
  })
}
