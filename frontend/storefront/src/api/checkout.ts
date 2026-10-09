import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Cart } from '../domain/cart'
import { type CheckoutSession, sessionHoldsCart } from '../domain/checkout'
import { type CouponRejection, couponRejection } from '../domain/coupon'
import { type PaymentAttempt, type PaymentFailure, confirmation, paymentFailure, pollEvery } from '../domain/payment'
import { cartKey } from './cart'
import { stockKey } from './catalog'
import { isNotFound } from './failure'
import { ApiError, api, apiResponse } from './http'
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

/** Paying a Checkout Session's answer: the attempt as it stands, and the reference of the Pay request. */
export interface PayResult {
  attempt: PaymentAttempt
  /** The Pay request's Correlation ID, which the checkout Saga's calls carry too. */
  reference: string | undefined
}

/**
 * Pays a Checkout Session with a payment method: Checkout starts the checkout Saga, which places the
 * Order, authorizes payment and takes the held Stock, and answers how it ended, or that it is still
 * going (202). A decline or a failure cancels that Order but keeps the session, so it can be paid
 * again; those come back as errors, which {@link payFailureOf} reads.
 */
export function usePayCheckoutSession() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ sessionId, paymentMethod }: { sessionId: string; paymentMethod: string }) => {
      const response = await apiResponse<{ orderId?: string; status: string }>(
        'checkout-pricing',
        `/checkout/sessions/${encodeURIComponent(sessionId)}/pay`,
        { method: 'POST', token, body: { paymentMethod } },
      )
      const attempt: PaymentAttempt =
        response.status === 202
          ? { status: 'PROCESSING', orderId: null, declineReason: null }
          : { status: 'PAID', orderId: response.body.orderId ?? null, declineReason: null }
      return { attempt, reference: response.correlationId } satisfies PayResult
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: cartKey })
      queryClient.invalidateQueries({ queryKey: ordersKey })
      queryClient.invalidateQueries({ queryKey: stockKey })
    },
  })
}

/**
 * The latest attempt to pay a Checkout Session, null when it was never paid. While a payment made
 * at `confirmingSince` is still being confirmed, it is read again every {@link pollEvery}
 * milliseconds, for up to two minutes.
 */
export function usePaymentAttempt(sessionId: string | undefined, confirmingSince: number | undefined) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: ['payment-attempt', sessionId],
    queryFn: async () => {
      try {
        return await api<PaymentAttempt>(
          'checkout-pricing',
          `/checkout/sessions/${encodeURIComponent(sessionId ?? '')}/payment`,
          { token },
        )
      } catch (error) {
        if (isNotFound(error)) return null
        throw error
      }
    },
    enabled: token !== undefined && sessionId !== undefined,
    refetchInterval: (query) =>
      confirmingSince !== undefined &&
      confirmation(latestAttempt(query.state), confirmingSince, Date.now()).kind === 'confirming'
        ? pollEvery
        : false,
    refetchOnWindowFocus: false,
    gcTime: 0,
  })
}

/**
 * The reference of the Pay request that started a session's latest payment, kept in the tab's
 * sessionStorage so that a page reloaded while the payment is confirmed can still show it with a
 * failure. Storage may be unavailable, in which case it is simply not kept.
 */
export function rememberPaymentReference(sessionId: string, reference: string | undefined) {
  try {
    if (reference) sessionStorage.setItem(`payment-reference:${sessionId}`, reference)
  } catch {
    // Not kept: a reloaded page shows a failure without its reference.
  }
}

export function rememberedPaymentReference(sessionId: string | undefined): string | undefined {
  try {
    return (sessionId && sessionStorage.getItem(`payment-reference:${sessionId}`)) || undefined
  } catch {
    return undefined
  }
}

/** The attempt a payment query last read, and when; undefined until it has read one. */
export function latestAttempt(state: { data?: PaymentAttempt | null; dataUpdatedAt: number }) {
  return state.data ? { attempt: state.data, readAt: state.dataUpdatedAt } : undefined
}

/** Why paying failed, as the Customer is told: a declined card, a hold that ran out, or a payment that failed. */
export function payFailureOf(error: unknown): PaymentFailure | undefined {
  return error instanceof ApiError ? paymentFailure(error.status, error.problem) : undefined
}

/**
 * Whether paying, or changing its Coupon, failed because the session is over: expired (410), or gone
 * altogether (404). A hold that ran out while paying is a 410 too, but a payment failure with its own
 * message.
 */
export function isSessionOver(error: unknown): boolean {
  if (!(error instanceof ApiError)) return false
  return error.status === 404 || (error.status === 410 && error.problem.reason !== 'holdExpired')
}
