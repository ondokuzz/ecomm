import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Payment } from '../domain/payment'
import { api } from './http'

/** Every Payment for an Order, newest first, each with its transactions; empty when none was recorded. Staff only. */
export function usePayments(orderId: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['payments', orderId],
    queryFn: () => api<Payment[]>('payment', `/staff/payments?orderId=${encodeURIComponent(orderId)}`, { token }),
  })
}
