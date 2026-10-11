import { useQuery } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Payment } from '../domain/payment'
import { api } from './http'

/** The Customer's Payments for one of their Orders, newest first; empty when none was recorded. */
export function usePayments(orderId: string) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['payments', orderId],
    queryFn: () => api<Payment[]>('payment', `/payments?orderId=${encodeURIComponent(orderId)}`, { token }),
    enabled: token !== undefined,
  })
}
