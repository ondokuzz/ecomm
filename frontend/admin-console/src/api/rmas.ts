import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Page } from '../domain/paging'
import type { Rma } from '../domain/rma'
import { api } from './http'

const rmasKey = ['rmas'] as const

/**
 * A page of every Customer's RMAs, oldest first, for `query` (see `rmasQueryOf`): the RMA approval
 * queue. The page on show stays until the next arrives. Staff only.
 */
export function useRmas(query: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...rmasKey, 'list', query],
    queryFn: () => api<Page<Rma>>('returns-warranty', `/rmas${query}`, { token }),
    placeholderData: keepPreviousData,
  })
}

/** The most RMAs one Order's page lists. */
const orderRmasSize = 100

/** An Order's RMAs, oldest first, whatever their status. Staff only. */
export function useOrderRmas(orderId: string) {
  const token = useAuth().user?.access_token
  const query = new URLSearchParams({ orderId, size: String(orderRmasSize) })
  return useQuery({
    queryKey: [...rmasKey, 'order', orderId],
    queryFn: () => api<Page<Rma>>('returns-warranty', `/rmas?${query}`, { token }),
  })
}

/** Any Customer's RMA, with the window it was checked against and its decision history. Staff only. */
export function useRma(id: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...rmasKey, id],
    queryFn: () => api<Rma>('returns-warranty', `/rmas/${encodeURIComponent(id)}`, { token }),
  })
}

/**
 * Approves or rejects a requested RMA, with the body `decisionOf` made. A decided RMA is a 409
 * `rmaAlreadyDecided`: another Staff member got there first.
 */
export function useDecideRma(id: string) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ decision, body }: { decision: 'approve' | 'reject'; body: object }) =>
      api<Rma>('returns-warranty', `/rmas/${encodeURIComponent(id)}/${decision}`, { method: 'POST', body, token }),
    onSuccess: (rma) => {
      queryClient.setQueryData([...rmasKey, id], rma)
      return queryClient.invalidateQueries({ queryKey: rmasKey })
    },
    // The RMA was decided elsewhere: show it as it is now.
    onError: () => queryClient.invalidateQueries({ queryKey: [...rmasKey, id] }),
  })
}
