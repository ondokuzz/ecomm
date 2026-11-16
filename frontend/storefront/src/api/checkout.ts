import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import type { Cart } from '../domain/cart'
import { type CarriedOver, type CheckoutSession, sessionHoldsCart } from '../domain/checkout'
import { type CouponRejection, couponRejection } from '../domain/coupon'
import {
  type AddressForm,
  type AddressProblem,
  addressFormOf,
  addressProblem,
  shippingRequest,
} from '../domain/shipping'
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
 * which replaces the old session and releases its Reservation, unless {@link useStartAgain} has
 * already started it.
 */
export function useCheckoutSession(cart: Cart | undefined, attempt: number) {
  const token = useAuth().user?.access_token
  return useQuery({
    queryKey: [...sessionKey, attempt],
    queryFn: async () => {
      const current = attempt === 0 ? await currentSession(token) : undefined
      if (current && cart && sessionHoldsCart(current, cart)) return current
      return startSession(token)
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

const startSession = (token: string | undefined) =>
  api<CheckoutSession>('checkout-pricing', '/checkout/sessions', { method: 'POST', token })

const setCoupon = (token: string | undefined, sessionId: string, code: string | null) =>
  api<CheckoutSession>('checkout-pricing', `/checkout/sessions/${encodeURIComponent(sessionId)}/coupon`, {
    method: code === null ? 'DELETE' : 'PUT',
    token,
    body: code === null ? undefined : { code },
  })

const putShippingAddress = (token: string | undefined, sessionId: string, form: AddressForm) =>
  api<CheckoutSession>('checkout-pricing', `/checkout/sessions/${encodeURIComponent(sessionId)}/shipping-address`, {
    method: 'PUT',
    token,
    body: shippingRequest(form),
  })

/** A session started again, with the Coupon carried over to it and, when it didn't apply, why. */
export interface StartedAgain {
  session: CheckoutSession
  couponCode?: string
  couponError?: unknown
}

/**
 * "Start again": starts a new Checkout Session from the Cart, as the given `attempt`, and puts back
 * what the old one `carried`, each as the Customer would: its Shipping address, which Checkout
 * validates and quotes again, then its Coupon. An address Checkout refuses now is left off, so the
 * Customer gives one again; a Coupon that doesn't apply now is left off with its error, which the
 * Coupon field shows as applying it would have. Only starting the session failing fails it.
 */
export function useStartAgain() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ carried }: { attempt: number; carried: CarriedOver }): Promise<StartedAgain> => {
      let session = await startSession(token)
      if (carried.address) {
        try {
          session = await putShippingAddress(token, session.id, addressFormOf(carried.address))
        } catch {
          // Left off: the Shipping card asks for an address, as for any session without one.
        }
      }
      if (!carried.couponCode) return { session }
      try {
        return { session: await setCoupon(token, session.id, carried.couponCode), couponCode: carried.couponCode }
      } catch (couponError) {
        return { session, couponCode: carried.couponCode, couponError }
      }
    },
    // The attempt's session query finds it there, and starts none of its own.
    onSuccess: ({ session }, { attempt }) => queryClient.setQueryData([...sessionKey, attempt], session),
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
    mutationFn: ({ sessionId, code }: { sessionId: string; code: string | null }) => setCoupon(token, sessionId, code),
    onSuccess: (session) => queryClient.setQueriesData({ queryKey: sessionKey }, session),
  })
}

/**
 * Sends a Checkout Session to a Shipping address. Checkout validates and normalises it, quotes the
 * Shipping options for it, and the session it answers replaces the one shown; a refusal changes
 * nothing, and {@link addressProblemOf} reads why.
 */
export function useSessionShippingAddress() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, form }: { sessionId: string; form: AddressForm }) =>
      putShippingAddress(token, sessionId, form),
    onSuccess: (session) => queryClient.setQueriesData({ queryKey: sessionKey }, session),
  })
}

/** Why an address was refused, when that is why setting it failed. */
export function addressProblemOf(error: unknown): AddressProblem | undefined {
  return error instanceof ApiError ? addressProblem(error.status, error.problem) : undefined
}

/** Chooses one of the Shipping options quoted for a Checkout Session; its cost joins the total. */
export function useSessionShippingOption() {
  const token = useAuth().user?.access_token
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ sessionId, optionId }: { sessionId: string; optionId: string }) =>
      api<CheckoutSession>('checkout-pricing', `/checkout/sessions/${encodeURIComponent(sessionId)}/shipping-option`, {
        method: 'PUT',
        token,
        body: { optionId },
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
