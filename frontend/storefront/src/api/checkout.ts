import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Cart } from '../domain/cart'
import { type CheckoutSession, sessionHoldsCart } from '../domain/checkout'
import { type CouponRejection, couponRejection } from '../domain/coupon'
import type { CheckoutResult } from '../domain/order'
import { type PaymentFailure, paymentFailure } from '../domain/payment'
import { cartKey } from './cart'
import { stockKey } from './catalog'
import { isNotFound } from './failure'
import { ApiError, api } from './http'
import { ordersKey } from './orders'

const sessionKey = ['checkout-session']

/**
 * The Customer's Checkout Session for `cart`. The first attempt resumes the one they have when it
 * still holds this Cart, and starts a new one otherwise; each later `attempt` always starts anew,
 * which replaces the old session and releases its Reservation.
 */
export function useCheckoutSession(cart: Cart | undefined, attempt: number) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...sessionKey, attempt],
    queryFn: async () => {
      const current = attempt === 0 ? await currentSession(token) : undefined
      if (current && cart && sessionHoldsCart(current, cart)) return current
      return api<CheckoutSession>('checkout-pricing', '/checkout/sessions', { method: 'POST', token })
    },
    enabled: token !== undefined && cart !== undefined && cart.items.length > 0,
    // Starting one has side effects (a Reservation), so never behind the Customer's back.
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
}

async function currentSession(token: string | undefined): Promise<CheckoutSession | undefined> {
  try {
    return await api<CheckoutSession>('checkout-pricing', '/checkout/sessions/current', { token })
  } catch (error) {
    if (isNotFound(error)) return undefined
    throw error
  }
}

/**
 * Applies a Coupon to a Checkout Session, or takes it off with `code` null. Checkout works the
 * discount, tax and total out again, and the session it answers replaces the one shown.
 */
export function useSessionCoupon() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, code }: { sessionId: string; code: string | null }) =>
      api<CheckoutSession>('checkout-pricing', `/checkout/sessions/${encodeURIComponent(sessionId)}/coupon`, {
        method: code === null ? 'DELETE' : 'PUT',
        token,
        body: code === null ? undefined : { code },
      }),
    onSuccess: (session) => queryClient.setQueriesData({ queryKey: sessionKey }, session),
  })
}

/** Why a Coupon doesn't apply, when that is why applying it failed. */
export function couponRejectionOf(error: unknown): CouponRejection | undefined {
  return error instanceof ApiError ? couponRejection(error.status, error.problem) : undefined
}

/**
 * Pays a Checkout Session with a payment method: Checkout places the Order, authorizes payment and
 * takes the held Stock. A declined or failed payment cancels that Order but keeps the session, so it
 * can be paid again.
 */
export function usePayCheckoutSession() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, paymentMethod }: { sessionId: string; paymentMethod: string }) =>
      api<CheckoutResult>('checkout-pricing', `/checkout/sessions/${encodeURIComponent(sessionId)}/pay`, {
        method: 'POST',
        token,
        body: { paymentMethod },
      }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: cartKey })
      queryClient.invalidateQueries({ queryKey: ordersKey })
      queryClient.invalidateQueries({ queryKey: stockKey })
    },
  })
}

/** Why paying failed, when paying again can fix it: a declined card, or a payment that didn't go through. */
export function payFailureOf(error: unknown): PaymentFailure | undefined {
  return error instanceof ApiError ? paymentFailure(error.status, error.problem) : undefined
}

/** Whether paying, or changing its Coupon, failed because the session is over: expired (410), or gone altogether (404). */
export function isSessionOver(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 410 || error.status === 404)
}
