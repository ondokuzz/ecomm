import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Notification } from '../domain/notification'
import type { Page } from '../domain/paging'
import { api } from './http'

/** The Notifications sent about an Order, newest first: an Order has a handful, so one page of 100. Staff only. */
export function useOrderNotifications(orderId: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['notifications', 'order', orderId],
    queryFn: () =>
      api<Page<Notification>>('notifications', `/notifications?${new URLSearchParams({ orderId, size: '100' })}`, {
        token,
      }),
  })
}

/** The Notifications sent about an RMA, newest first: a handful at most, so one page of 100. Staff only. */
export function useRmaNotifications(rmaId: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['notifications', 'rma', rmaId],
    queryFn: () =>
      api<Page<Notification>>('notifications', `/notifications?${new URLSearchParams({ rmaId, size: '100' })}`, {
        token,
      }),
  })
}
