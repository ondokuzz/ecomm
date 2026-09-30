import { useEffect, useId, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCart } from '../api/cart'
import { useVariants } from '../api/catalog'
import { isSessionOver, useCheckoutSession, usePayCheckoutSession } from '../api/checkout'
import { ApiError } from '../api/http'
import { CheckoutSteps } from '../components/CheckoutSteps'
import { EmptyCart } from '../components/EmptyCart'
import { ProductLines } from '../components/ProductLines'
import { EmptyState, ErrorMessage, ErrorState, Loading } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { type CartLine, itemCountLabel } from '../domain/cart'
import { variantName } from '../domain/catalog'
import { type CheckoutSession, checkoutProblem, sessionCountdown, sessionSummaryRows } from '../domain/checkout'
import { formatMoney } from '../domain/money'

/**
 * Starts a Checkout Session for the Cart, or resumes the one that already holds it, and shows its
 * lines at their held Prices with a countdown to when the hold ends. Paying it places the Order.
 */
export function CheckoutPage() {
  const cart = useCart()
  // Each "Start again" is a new attempt, which always starts a fresh session.
  const [attempt, setAttempt] = useState(0)
  const session = useCheckoutSession(cart.data, attempt)
  const pay = usePayCheckoutSession()
  const now = useNow()
  const navigate = useNavigate()

  const startAgain = () => {
    pay.reset()
    setAttempt((n) => n + 1)
  }

  if (cart.isPending) return <Loading />
  if (cart.error) return <ErrorMessage error={cart.error} />
  if (cart.data.items.length === 0) return <EmptyCart>There is nothing to check out yet.</EmptyCart>
  if (session.isPending) return <Loading />
  if (session.error) return <StartError error={session.error} retrying={session.isFetching} onRetry={startAgain} />

  const countdown = sessionCountdown(session.data.expiresAt, now)
  if (countdown.expired || isSessionOver(pay.error)) return <SessionExpired onStartAgain={startAgain} />

  const payNow = () =>
    pay.mutate(session.data.id, {
      onSuccess: (result) => navigate(`/orders/${encodeURIComponent(result.orderId)}?placed`, { replace: true }),
    })

  return (
    <section>
      <CheckoutSteps current="Payment" />
      <h1>Checkout</h1>
      <HeldUntilNotice expiresAt={session.data.expiresAt} left={countdown.label} />
      <div className="cart-layout">
        <div className="cart-main">
          <Card className="payment">
            <h2>Payment</h2>
            <MockCard />
            <p className="muted payment-note">
              <Icon name="lock" size={16} />
              This is a demo: the payment is mocked and always succeeds.
            </p>
            {pay.error && <ErrorMessage error={pay.error} />}
            <Button
              variant="primary"
              size="lg"
              className="pay-button"
              onClick={payNow}
              loading={pay.isPending || pay.isSuccess}
            >
              {pay.isPending ? 'Paying…' : `Pay ${formatMoney(session.data.total)}`}
            </Button>
          </Card>
        </div>
        <SessionSummary session={session.data} />
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
 * When the session ends, as a clock time, and a live countdown to it. The countdown is a timer, which
 * screen readers don't announce every second; the clock time says the same once.
 */
function HeldUntilNotice({ expiresAt, left }: { expiresAt: string; left: string }) {
  const until = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date(expiresAt))
  return (
    <p className="alert alert-info held-until">
      <Icon name="lock" size={18} />
      <span>
        Your items and their prices are held until <strong>{until}</strong>.{' '}
        <span className="session-countdown" role="timer" aria-label="Time left">
          {left}
        </span>{' '}
        left
      </span>
    </p>
  )
}

/** What the session comes to, with its lines at their held Prices. */
function SessionSummary({ session }: { session: CheckoutSession }) {
  const headingId = useId()
  const variants = useVariants(session.lines.map((line) => line.variantId))
  const lines = session.lines.map((line) => {
    const variant = variants[line.variantId]
    return variant ? { ...line, variant, name: variantName(variant) } : line
  })
  return (
    <Card className="order-summary" aria-labelledby={headingId} role="region">
      <h2 id={headingId}>Order summary</h2>
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
      <Link to="/cart" className="order-summary-edit">
        Edit cart
      </Link>
    </Card>
  )
}

/** The session expired before the Customer paid: nothing was bought, and they can start over. */
function SessionExpired({ onStartAgain }: { onStartAgain: () => void }) {
  return (
    <section>
      <CheckoutSteps current="Payment" />
      <EmptyState
        title="Your hold expired"
        focusTitle
        action={
          <Button variant="primary" size="lg" onClick={onStartAgain}>
            Start again
          </Button>
        }
      >
        We held your items for 15 minutes. Nothing was charged; start again to check the prices and stock.
      </EmptyState>
    </section>
  )
}

/** Why a session couldn't start. Products out of Stock or gone from Catalog are listed, with a way back to the Cart. */
function StartError({ error, retrying, onRetry }: { error: unknown; retrying: boolean; onRetry: () => void }) {
  const cart = useCart()
  const variants = useVariants((cart.data?.items ?? []).map((item) => item.variantId))
  const lines: CartLine[] = (cart.data?.items ?? []).map((item) => {
    const variant = variants[item.variantId]
    return variant ? { ...item, variant, name: variantName(variant) } : item
  })
  const problem = error instanceof ApiError ? checkoutProblem(error.problem, lines) : undefined
  if (!problem) return <ErrorState title="Checkout couldn't start" error={error} retrying={retrying} onRetry={onRetry} />
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

/** A payment card drawn for show: there is nothing to type, since the payment is mocked. */
function MockCard() {
  return (
    <div className="mock-card" aria-label="Demo payment card" role="img">
      <div className="mock-card-top">
        <span className="mock-card-chip" />
        <span className="mock-card-brand">DEMO</span>
      </div>
      <span className="mock-card-number">•••• •••• •••• 4242</span>
      <div className="mock-card-bottom">
        <span>Demo Customer</span>
        <span>12/30</span>
      </div>
    </div>
  )
}
