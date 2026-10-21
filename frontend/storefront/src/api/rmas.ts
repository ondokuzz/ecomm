import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { useAuth } from 'react-oidc-context'
import type { Page } from '../domain/paging'
import {
  type OrderEligibility,
  type ReturnWarehouse,
  type Rma,
  type RmaRequestBody,
  rmaRefreshInterval,
  rmasPageSize,
} from '../domain/rma'
import { type Shipment, trackingRefreshInterval } from '../domain/shipment'
import { api } from './http'

const rmasKey = ['rmas']

/**
 * What each line of the Customer's delivered Order is still eligible for. Only asked for a delivered
 * Order: any other is a 404.
 */
export function useEligibility(orderId: string, delivered: boolean) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...rmasKey, 'eligibility', orderId],
    queryFn: () =>
      api<OrderEligibility>('returns-warranty', `/rmas/eligibility?${new URLSearchParams({ orderId })}`, { token }),
    enabled: token !== undefined && delivered,
  })
}

/** A page of the Customer's RMAs, newest first, counting from 0. */
export function useRmas(page: number) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...rmasKey, 'page', page],
    queryFn: () => api<Page<Rma>>('returns-warranty', `/rmas?page=${page}&size=${rmasPageSize}`, { token }),
    enabled: token !== undefined,
    placeholderData: keepPreviousData,
  })
}

/** One of the Customer's RMAs, asked again every 10 s while its units are received or inspected. */
export function useRma(id: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...rmasKey, id],
    queryFn: () => api<Rma>('returns-warranty', `/rmas/${encodeURIComponent(id)}`, { token }),
    enabled: token !== undefined,
    refetchInterval: (query) => rmaRefreshInterval(query.state.data?.status),
  })
}

/**
 * Requests an RMA. The form keeps one `Idempotency-Key` for as long as it is open, so a request sent
 * again after an answer that never came is replayed rather than made twice; Returns & Warranty
 * refuses a key reused with another body. A request it refused may be changed and sent again under
 * a new key.
 */
export function useRequestRma() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  const key = useRef<{ sent: string; key: string }>(undefined)
  return useMutation({
    mutationFn: (body: RmaRequestBody) => {
      const sent = JSON.stringify(body)
      if (key.current?.sent !== sent) key.current = { sent, key: crypto.randomUUID() }
      return api<Rma>('returns-warranty', '/rmas', { method: 'POST', body, token, idempotencyKey: key.current.key })
    },
    onSuccess: (rma) => {
      queryClient.setQueryData([...rmasKey, rma.id], rma)
      return queryClient.invalidateQueries({ queryKey: rmasKey })
    },
  })
}

/** The Warehouse an RMA's units go back to, with its Return address. */
export function useReturnWarehouse(id: string | undefined) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['warehouses', id],
    queryFn: () => api<ReturnWarehouse>('fulfillment-shipping', `/warehouses/${encodeURIComponent(id!)}`, { token }),
    enabled: token !== undefined && id !== undefined,
    staleTime: Infinity,
  })
}

/**
 * An RMA's Replacement Shipment, asked again every 10 s while it is being prepared or on its way.
 * One not created yet is a 404, which isn't retried.
 */
export function useReplacementShipment(rmaId: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['shipments', 'rma', rmaId],
    queryFn: () => api<Shipment>('fulfillment-shipping', `/shipments?${new URLSearchParams({ rmaId })}`, { token }),
    enabled: token !== undefined,
    refetchInterval: (query) => trackingRefreshInterval(undefined, query.state.data?.status),
  })
}
