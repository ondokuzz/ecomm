import { type FormEvent, type ReactNode, useEffect, useId, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCart } from '../api/cart'
import { useVariants } from '../api/catalog'
import {
  couponRejectionOf,
  isSessionOver,
  latestAttempt,
  payFailureOf,
  rememberPaymentReference,
  rememberedPaymentReference,
  type StartedAgain,
  useCheckoutSession,
  usePayCheckoutSession,
  usePaymentAttempt,
  useSessionCoupon,
  useStartAgain,
} from '../api/checkout'
import { useFormatMoney } from '../api/currencies'
import { failureOf } from '../api/failure'
import { ApiError } from '../api/http'
import { CheckoutShipping } from '../components/CheckoutShipping'
import { CheckoutSteps } from '../components/CheckoutSteps'
import { EmptyCart } from '../components/EmptyCart'
import { ProductLines } from '../components/ProductLines'
import { CheckoutSkeleton } from '../components/PageSkeletons'
import { EmptyState, ErrorMessage, ErrorState, LookupError, SupportReference } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { Input } from '../components/ui/Input'
import { type CartLine, itemCountLabel } from '../domain/cart'
import { variantName } from '../domain/catalog'
import {
  type CheckoutSession,
  carriedOver,
  checkoutProblem,
  hasShipping,
  sessionCountdown,
  sessionSummaryRows,
} from '../domain/checkout'
import { appliedCoupon } from '../domain/order'
import {
  type PaymentFailure,
  type TestCard,
  confirmation,
  defaultTestCard,
  testCards,
  whenConfirmingPayment,
} from '../domain/payment'
import { formatClock } from '../domain/dates'

/**
 * Starts a Checkout Session for the Cart, or resumes the one that already holds it, and shows its
 * lines at their held Prices and when the hold ends, counting down once under five minutes are left.
 * An expired session's "Start again" starts a new one with its Shipping address and Coupon put back,
 * each if it still applies. The Customer gives a Shipping
 * address and chooses a Shipping option, whose cost joins the total; Pay waits for both. Paying it with the chosen test
 * card starts the checkout Saga. A Pay still unanswered after 2 seconds, as one the bank confirms
 * later is, shows "Confirming your payment…". When it hasn't finished within Checkout's wait (a
 * 202), the page goes on showing it and asks how it stands every 1.5 seconds, for up to 2 minutes;
 * reloading the page while one is being confirmed resumes asking. A paid Order opens its page; a
 * declined or failed payment says why, and the Customer can pay again.
 */
export function CheckoutPage() {
  const formatMoney = useFormatMoney()
  const cart = useCart()
  // Each "Start again" is a new attempt, whose fresh session it starts itself.
  const [attempt, setAttempt] = useState(0)
  const session = useCheckoutSession(cart.data, attempt)
  const restart = useStartAgain()
  const pay = usePayCheckoutSession()
  const coupon = useSessionCoupon()
  const [card, setCard] = useState(defaultTestCard)
  const now = useNow()
  const navigate = useNavigate()
  const shippingHintId = useId()
  // When the payment being confirmed was made: null when there is none, undefined until the session's
  // latest payment has been read, in case one was still being confirmed when the page was left.
  const [confirmingSince, setConfirmingSince] = useState<number | null>()
  // When the Pay waiting for its answer was sent; undefined when none is.
  const [payingSince, setPayingSince] = useState<number>()
  // The Pay that has waited long enough to be one being confirmed, by when it was sent. A timer from
  // the moment it was sent says so, rather than the next tick of the page's clock.
  const [confirmingPay, setConfirmingPay] = useState<number>()
  useEffect(() => {
    if (payingSince === undefined) return
    return whenConfirmingPayment(payingSince, () => setConfirmingPay(payingSince))
  }, [payingSince])
  const [newReference, setReference] = useState<string>()
  const reference = newReference ?? rememberedPaymentReference(session.data?.id)
  const payment = usePaymentAttempt(session.data?.id, confirmingSince ?? undefined)
  if (confirmingSince === undefined && payment.data !== undefined) {
    setConfirmingSince(payment.data?.status === 'PROCESSING' ? now : null)
  }
  const confirming = confirmingSince == null ? undefined : confirmation(latestAttempt(payment), confirmingSince, now)

  const paidOrderId = confirming?.kind === 'paid' ? confirming.orderId : undefined
  useEffect(() => {
    if (paidOrderId) navigate(`/orders/${encodeURIComponent(paidOrderId)}?placed`, { replace: true })
  }, [paidOrderId, navigate])

  const startAgain = () => {
    const next = attempt + 1
    restart.mutate(
      { attempt: next, carried: carriedOver(session.data) },
      {
        onSuccess: () => {
          pay.reset()
          coupon.reset()
          setConfirmingSince(undefined)
          setReference(undefined)
          setAttempt(next)
        },
      },
    )
  }

  if (cart.isPending) return <CheckoutSkeleton />
  if (cart.error) {
    return (
      <ErrorState
        title="We couldn't load your cart"
        error={cart.error}
        retrying={cart.isFetching}
        onRetry={() => cart.refetch()}
      />
    )
  }
  if (cart.data.items.length === 0) return <EmptyCart>There is nothing to check out yet.</EmptyCart>
  if (restart.error) return <StartError error={restart.error} retrying={restart.isPending} onRetry={startAgain} />
  if (session.isPending) return <CheckoutSkeleton />
  if (session.error) return <StartError error={session.error} retrying={session.isFetching} onRetry={startAgain} />

  const countdown = sessionCountdown(session.data.expiresAt, now)
  if (countdown.expired || isSessionOver(pay.error) || isSessionOver(coupon.error))
    return <SessionExpired onStartAgain={startAgain} starting={restart.isPending} />

  const payNow = () => {
    setConfirmingSince(null)
    setPayingSince(Date.now())
    pay.mutate(
      { sessionId: session.data.id, paymentMethod: card.token },
      {
        onSettled: () => setPayingSince(undefined),
        onSuccess: ({ attempt, reference }) => {
          setReference(reference)
          rememberPaymentReference(session.data.id, reference)
          if (attempt.status === 'PAID' && attempt.orderId) {
            navigate(`/orders/${encodeURIComponent(attempt.orderId)}?placed`, { replace: true })
          } else {
            setConfirmingSince(Date.now())
          }
        },
      },
    )
  }
  // Starting again leaves the old session shown until the new one is ready; nothing acts on it meanwhile.
  const busy = pay.isPending || paidOrderId !== undefined || confirming?.kind === 'confirming' || restart.isPending
  const confirmingPayment =
    (pay.isPending && payingSince !== undefined && confirmingPay === payingSince) || confirming?.kind === 'confirming'
  const payFailure = payFailureOf(pay.error)
  const shippable = hasShipping(session.data)

  return (
    <section>
      <CheckoutSteps current="Payment" />
      <h1>Checkout</h1>
      <HeldUntilNotice expiresAt={session.data.expiresAt} left={countdown.nearlyOver ? countdown.label : undefined} />
      <div className="cart-layout">
        <div className="cart-main stack">
          <CheckoutShipping
            key={session.data.id}
            session={session.data}
            disabled={busy}
            onChange={() => {
              pay.reset()
              setConfirmingSince(null)
            }}
          />
          <Card className="payment">
            <h2>Payment</h2>
            <MockCard card={card} />
            <TestCardPicker
              selected={card}
              disabled={busy}
              onSelect={(picked) => {
                setCard(picked)
                pay.reset()
                setConfirmingSince(null)
              }}
            />
            <p className="muted payment-note">
              <Icon name="lock" size={16} />
              This is a demo: each test card shows one way a payment can go. Nothing is charged.
            </p>
            {confirmingPayment && (
              <p className="alert alert-info confirming-payment" role="status">
                <Icon name="lock" size={18} />
                <span>Confirming your payment… This can take a little while; please keep this page open.</span>
              </p>
            )}
            {confirming?.kind === 'stillConfirming' && (
              <div className="alert alert-info" role="status">
                <Icon name="lock" size={18} />
                <div className="alert-body">
                  <strong>We're still confirming your payment</strong>
                  <span>
                    You won't be charged twice. Check again in a moment, or look for the order under My Orders.
                  </span>
                </div>
                <Button size="sm" className="alert-action" onClick={() => setConfirmingSince(Date.now())}>
                  Check again
                </Button>
              </div>
            )}
            {confirming?.kind === 'failed' && (
              <PaymentFailureAlert
                failure={confirming.failure}
                reference={reference}
                onStartAgain={startAgain}
                starting={restart.isPending}
              />
            )}
            {pay.error &&
              (payFailure ? (
                <PaymentFailureAlert
                  failure={payFailure}
                  reference={failureOf(pay.error).reference}
                  onStartAgain={startAgain}
                  starting={restart.isPending}
                />
              ) : (
                <ErrorMessage error={pay.error} />
              ))}
            {!shippable && (
              <p id={shippingHintId} className="muted shipping-hint">
                Enter your address and choose a shipping option to pay.
              </p>
            )}
            <Button
              variant="primary"
              size="lg"
              className="pay-button"
              onClick={payNow}
              loading={busy}
              disabled={coupon.isPending || !shippable}
              aria-describedby={shippable ? undefined : shippingHintId}
            >
              {confirmingPayment
                ? 'Confirming your payment…'
                : pay.isPending
                  ? 'Paying…'
                  : `Pay ${formatMoney(session.data.total)}`}
            </Button>
          </Card>
        </div>
        <SessionSummary session={session.data}>
          <CouponField
            key={session.data.id}
            session={session.data}
            carried={restart.data?.session.id === session.data.id ? restart.data : undefined}
            coupon={coupon}
            disabled={busy}
            onChange={() => {
              pay.reset()
              setConfirmingSince(null)
            }}
          />
        </SessionSummary>
      </div>
    </section>
  )
}

/** The time now, in epoch milliseconds, updated every second. */
function useNow(): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return now
}

/**
 * When the session ends, as a clock time, and, once it is nearly over, a live countdown to it: the
 * time `left`. The countdown is a timer, which screen readers don't announce every second; the clock
 * time says the same once.
 */
function HeldUntilNotice({ expiresAt, left }: { expiresAt: string; left: string | undefined }) {
  const until = formatClock(expiresAt)
  return (
    <p className={`alert ${left === undefined ? 'alert-info' : 'alert-warning'} held-until`}>
      <Icon name="lock" size={18} />
      <span>
        Your items and their prices are held until <strong>{until}</strong>.
        {left !== undefined && (
          <>
            {' '}
            <span className="session-countdown" role="timer" aria-label="Time left">
              {left}
            </span>{' '}
            left
          </>
        )}
      </span>
    </p>
  )
}

/** What the session comes to, with its lines at their held Prices, and `children` below the breakdown. */
function SessionSummary({ session, children }: { session: CheckoutSession; children?: ReactNode }) {
  const formatMoney = useFormatMoney()
  const headingId = useId()
  const { variants, failure } = useVariants(session.lines.map((line) => line.variantId))
  const lines = session.lines.map((line) => {
    const variant = variants[line.variantId]
    return variant ? { ...line, variant, name: variantName(variant) } : line
  })
  return (
    <Card className="order-summary" aria-labelledby={headingId} role="region">
      <h2 id={headingId}>Order summary</h2>
      <LookupError failure={failure} title="We couldn't load your items' names" />
      <ProductLines lines={lines} label="Checkout lines" compact />
      <dl>
        <div>
          <dt>Items</dt>
          <dd>{itemCountLabel({ items: session.lines })}</dd>
        </div>
        {sessionSummaryRows(session).map((row) => (
          <div key={row.label} className={row.isTotal ? 'order-summary-total' : undefined}>
            <dt>{row.label}</dt>
            <dd>{formatMoney(row.amount)}</dd>
          </div>
        ))}
      </dl>
      {children}
      <Link to="/cart" className="order-summary-edit">
        Edit cart
      </Link>
    </Card>
  )
}

/**
 * The Coupon field: a code to apply, or the one applied with a way to remove it. A code that doesn't
 * apply says why, next to the field, and leaves the session as it was. A Coupon carried over from an
 * expired session that doesn't apply to the new one is shown in the field, with why, as applying it
 * would have, until the Customer changes the code. It is meant to be keyed by the session.
 */
function CouponField({
  session,
  carried,
  coupon,
  disabled,
  onChange,
}: {
  session: CheckoutSession
  carried: StartedAgain | undefined
  coupon: ReturnType<typeof useSessionCoupon>
  disabled: boolean
  /** Called before the Coupon changes, since the amount to pay does too. */
  onChange: () => void
}) {
  const refused = carried?.couponError !== undefined
  const [code, setCode] = useState(refused ? (carried.couponCode ?? '') : '')
  const [carriedError, setCarriedError] = useState(refused ? carried.couponError : undefined)
  const inputId = useId()
  const errorId = useId()
  const change = (next: string | null) => {
    onChange()
    setCarriedError(undefined)
    coupon.mutate({ sessionId: session.id, code: next }, { onSuccess: () => setCode('') })
  }
  const error = coupon.error ?? carriedError
  const rejection = couponRejectionOf(error)
  const otherError = error && !rejection && !isSessionOver(error) ? error : undefined

  const applied = appliedCoupon(session.discounts)
  if (applied) {
    return (
      <div className="coupon coupon-applied">
        <Icon name="check" size={18} />
        <span>
          <strong className="coupon-code">{applied.couponCode}</strong> applied
        </span>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => change(null)}
          loading={coupon.isPending}
          disabled={disabled}
          aria-label={`Remove coupon ${applied.couponCode}`}
        >
          Remove
        </Button>
        {otherError && <ErrorMessage error={otherError} />}
      </div>
    )
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (code.trim()) change(code.trim())
  }
  return (
    <form className="coupon" onSubmit={submit} noValidate>
      <label htmlFor={inputId} className="coupon-label">
        Coupon code
      </label>
      <div className="coupon-row">
        <Input
          id={inputId}
          value={code}
          onChange={(event) => {
            setCode(event.target.value)
            setCarriedError(undefined)
            coupon.reset()
          }}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          maxLength={64}
          disabled={disabled || coupon.isPending}
          aria-invalid={rejection ? true : undefined}
          aria-describedby={rejection ? errorId : undefined}
        />
        <Button type="submit" loading={coupon.isPending} disabled={disabled || !code.trim()}>
          Apply
        </Button>
      </div>
      {rejection && (
        <p id={errorId} className="coupon-error" role="alert">
          <Icon name="alert" size={16} />
          {rejection.message}
        </p>
      )}
      {otherError && <ErrorMessage error={otherError} />}
    </form>
  )
}

/**
 * The session expired before the Customer paid: nothing was bought, and they can start over, keeping
 * their Shipping address and Coupon, each if it still applies.
 */
function SessionExpired({ onStartAgain, starting }: { onStartAgain: () => void; starting: boolean }) {
  return (
    <section>
      <CheckoutSteps current="Payment" />
      <EmptyState
        title="Your hold expired"
        heading="h1"
        focusTitle
        action={
          <Button variant="primary" size="lg" onClick={onStartAgain} loading={starting}>
            Start again
          </Button>
        }
      >
        We held your items for 15 minutes. Nothing was charged; start again to check the prices and stock. Your address
        and coupon come with you, each if it still applies.
      </EmptyState>
    </section>
  )
}

/** Why a session couldn't start. Products out of Stock or gone from Catalog are listed, with a way back to the Cart. */
function StartError({ error, retrying, onRetry }: { error: unknown; retrying: boolean; onRetry: () => void }) {
  const cart = useCart()
  const { variants } = useVariants((cart.data?.items ?? []).map((item) => item.variantId))
  const lines: CartLine[] = (cart.data?.items ?? []).map((item) => {
    const variant = variants[item.variantId]
    return variant ? { ...item, variant, name: variantName(variant) } : item
  })
  const problem = error instanceof ApiError ? checkoutProblem(error.problem, lines) : undefined
  if (!problem)
    return <ErrorState title="Checkout couldn't start" error={error} retrying={retrying} onRetry={onRetry} />
  return (
    <section>
      <CheckoutSteps current="Payment" />
      <h1>Checkout</h1>
      <div className="alert alert-danger" role="alert">
        <Icon name="alert" size={18} />
        <div>
          <strong>
            {problem.reason === 'outOfStock' ? 'Some items are out of stock' : "Some items aren't available any more"}
          </strong>
          <ul className="alert-list">
            {problem.products.map((product) => (
              <li key={product.variantId}>{product.name}</li>
            ))}
          </ul>
          <span>Change them in your cart, then try again.</span>
        </div>
      </div>
      <ButtonLink variant="primary" to="/cart">
        Back to cart
      </ButtonLink>
    </section>
  )
}

/**
 * The test cards to pay with, one per way the mock gateway answers. Each radio is named by its card
 * alone and described by what it does, so the Customer knows a decline is coming.
 */
function TestCardPicker({
  selected,
  disabled,
  onSelect,
}: {
  selected: TestCard
  disabled: boolean
  onSelect: (card: TestCard) => void
}) {
  const name = useId()
  return (
    <fieldset className="test-cards" disabled={disabled}>
      <legend>Test card</legend>
      {testCards.map((card) => (
        <label key={card.token} className="test-card">
          <input
            type="radio"
            name={name}
            value={card.token}
            checked={card.token === selected.token}
            onChange={() => onSelect(card)}
            aria-labelledby={`${name}-${card.token}-label`}
            aria-describedby={`${name}-${card.token}-outcome`}
          />
          <span id={`${name}-${card.token}-label`} className="test-card-label">
            {card.label}
          </span>
          <span className="test-card-number" aria-hidden="true">
            •••• {card.last4}
          </span>
          <span id={`${name}-${card.token}-outcome`} className="test-card-outcome muted">
            {card.outcome}
          </span>
        </label>
      ))}
    </fieldset>
  )
}

/**
 * Why paying failed, with what the Customer can do: try another card after a decline, start again
 * once their hold ran out, or pay again after a failure. Each carries the payment's reference.
 */
function PaymentFailureAlert({
  failure,
  reference,
  onStartAgain,
  starting,
}: {
  failure: PaymentFailure
  reference: string | undefined
  onStartAgain: () => void
  starting: boolean
}) {
  return (
    <div className={`alert alert-danger pay-error pay-error-${failure.kind}`} role="alert">
      <Icon name="alert" size={18} />
      <div className="alert-body">
        <strong>{failure.title}</strong>
        <span>{failure.message}</span>
        {reference && <SupportReference reference={reference} />}
      </div>
      {failure.kind === 'holdExpired' && (
        <Button size="sm" className="alert-action" onClick={onStartAgain} loading={starting}>
          Start again
        </Button>
      )}
    </div>
  )
}

/** The chosen test card, drawn for show: there is nothing to type, since the payment is mocked. */
function MockCard({ card }: { card: TestCard }) {
  return (
    <div className="mock-card" aria-label={`Demo payment card ending ${card.last4}`} role="img">
      <div className="mock-card-top">
        <span className="mock-card-chip" />
        <span className="mock-card-brand">DEMO</span>
      </div>
      <span className="mock-card-number">•••• •••• •••• {card.last4}</span>
      <div className="mock-card-bottom">
        <span>Demo Customer</span>
        <span>12/30</span>
      </div>
    </div>
  )
}
