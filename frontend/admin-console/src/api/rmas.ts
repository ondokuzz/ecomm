import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { useAuth } from 'react-oidc-context'
import type { Page } from '../domain/paging'
import { type InspectBody, type QueueTabId, type ReceiveBody, type Rma, queueTabs, rmasQueryOf } from '../domain/rma'
import { api } from './http'

const rmasKey = ['rmas'] as const

/**
 * A page of every Customer's RMAs for `query` (see `rmasQueryOf`): a tab of the RMA queue. The page
 * on show stays until the next arrives. Staff only.
 */
export function useRmas(query: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...rmasKey, 'list', query],
    queryFn: () => api<Page<Rma>>('returns-warranty', `/rmas${query}`, { token }),
    placeholderData: keepPreviousData,
  })
}

/** How many RMAs each tab of the queue holds; a count still loading is absent. */
export function useQueueCounts(): Partial<Record<QueueTabId, number>> {
  const token = useAuth().user?.access_token
  const counts = useQueries({
    queries: queueTabs.map((tab) => {
      const query = rmasQueryOf({ tab: tab.id, page: 0 }, 1)
      return {
        queryKey: [...rmasKey, 'list', query],
        queryFn: () => api<Page<Rma>>('returns-warranty', `/rmas${query}`, { token }),
      }
    }),
  })
  return Object.fromEntries(
    queueTabs.flatMap((tab, i) => {
      const total = counts[i].data?.total
      return total === undefined ? [] : [[tab.id, total]]
    }),
  )
}

/** How often an RMA's page asks again while the returns Saga resolves it. */
const resolvingInterval = 3_000

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

/**
 * Any Customer's RMA, with the window it was checked against and its RMA history, followed while
 * the returns Saga resolves it once inspected. Staff only.
 */
export function useRma(id: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...rmasKey, id],
    queryFn: () => api<Rma>('returns-warranty', `/rmas/${encodeURIComponent(id)}`, { token }),
    refetchInterval: (query) => (query.state.data?.status === 'INSPECTED' ? resolvingInterval : false),
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

/** A step after an RMA decision, with its body. */
export type RmaStep = { step: 'receive'; body: ReceiveBody } | { step: 'inspect'; body: InspectBody }

/**
 * Receives an approved RMA's units, or records a received one's Inspection, as the signed-in Staff
 * member. A step sent again as it was, after an answer that never came, keeps its `Idempotency-Key`,
 * so Returns & Warranty replays its first answer; a step changed since, or sent after one that
 * succeeded, gets a new one. Another Staff member's step first answers 409 `transitionRefused` with
 * the status it is in. Refused units are a 422 `serialMismatch` or `quantityMismatch`, a malformed
 * body a 400 naming its fields.
 */
export function useStepRma(id: string) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  const last = useRef<{ sent: string; key: string }>(undefined)
  return useMutation({
    mutationFn: (step: RmaStep) => {
      const sent = JSON.stringify(step)
      if (last.current?.sent !== sent) last.current = { sent, key: crypto.randomUUID() }
      return api<Rma>('returns-warranty', `/rmas/${encodeURIComponent(id)}/${step.step}`, {
        method: 'POST',
        body: step.body,
        token,
        idempotencyKey: last.current.key,
      })
    },
    onSuccess: (rma) => {
      last.current = undefined
      queryClient.setQueryData([...rmasKey, id], rma)
      return queryClient.invalidateQueries({ queryKey: rmasKey })
    },
    // Moved on elsewhere: show it as it is now.
    onError: () => queryClient.invalidateQueries({ queryKey: [...rmasKey, id] }),
  })
}
