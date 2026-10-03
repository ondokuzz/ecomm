import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { type Coupon, switched } from '../domain/coupon'
import { api } from './http'

const couponsKey = ['coupons'] as const

/** Every Coupon, by code. Staff only, so the token goes with every read too. */
export function useCoupons() {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: couponsKey,
    queryFn: () => api<Coupon[]>('promotions', '/coupons', { token }),
  })
}

export function useCoupon(code: string | undefined) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...couponsKey, code],
    queryFn: () => api<Coupon>('promotions', `/coupons/${encodeURIComponent(code!)}`, { token }),
    enabled: code !== undefined,
  })
}

/** Creates a Coupon, or replaces every field of the one with `code` but its code. */
export function useSaveCoupon(code: string | undefined) {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (coupon: Coupon) =>
      code === undefined
        ? api<Coupon>('promotions', '/coupons', { method: 'POST', body: coupon, token })
        : api<Coupon>('promotions', `/coupons/${encodeURIComponent(code)}`, { method: 'PUT', body: coupon, token }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: couponsKey }),
  })
}

export function useDeleteCoupon() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (code: string) =>
      api<void>('promotions', `/coupons/${encodeURIComponent(code)}`, { method: 'DELETE', token }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: couponsKey }),
  })
}

/** Switches a Coupon on or off, leaving the rest of it as it is. */
export function useSwitchCoupon() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ coupon, active }: { coupon: Coupon; active: boolean }) =>
      api<Coupon>('promotions', `/coupons/${encodeURIComponent(coupon.code)}`, {
        method: 'PUT',
        body: switched(coupon, active),
        token,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: couponsKey }),
  })
}
