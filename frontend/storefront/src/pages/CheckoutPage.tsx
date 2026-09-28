import { Link, useNavigate } from 'react-router'
import { usePricedCart } from '../api/cart'
import { ApiError } from '../api/http'
import { useCheckout } from '../api/orders'
import { CartLines } from '../components/CartLines'
import { CheckoutSteps } from '../components/CheckoutSteps'
import { EmptyCart } from '../components/EmptyCart'
import { OrderSummary } from '../components/OrderSummary'
import { ErrorMessage, Loading } from '../components/Status'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import type { CartLine } from '../domain/cart'
import { checkoutProblem } from '../domain/checkout'
import { formatMoney } from '../domain/money'

export function CheckoutPage() {
  const { cart, priced } = usePricedCart()
  const checkout = useCheckout()
  const navigate = useNavigate()

  if (cart.isPending) return <Loading />
  if (cart.error) return <ErrorMessage error={cart.error} />
  if (!priced || priced.lines.length === 0) return <EmptyCart>There is nothing to check out yet.</EmptyCart>

  const pay = () =>
    checkout.mutate(undefined, {
      onSuccess: (result) => navigate(`/orders/${encodeURIComponent(result.orderId)}?placed`, { replace: true }),
    })

  return (
    <section>
      <CheckoutSteps current="Payment" />
      <h1>Checkout</h1>
      <div className="cart-layout">
        <div className="cart-main">
          <Card className="payment">
            <h2>Payment</h2>
            <MockCard />
            <p className="muted payment-note">
              <Icon name="lock" size={16} />
              This is a demo: the payment is mocked and always succeeds.
            </p>
            {checkout.error && <CheckoutError error={checkout.error} lines={priced.lines} />}
            <Button variant="primary" size="lg" className="pay-button" onClick={pay} loading={checkout.isPending}>
              {checkout.isPending ? 'Paying…' : priced.total ? `Pay ${formatMoney(priced.total)}` : 'Pay'}
            </Button>
          </Card>
        </div>
        <OrderSummary cart={cart.data} priced={priced}>
          <CartLines priced={priced} compact />
          <Link to="/cart" className="order-summary-edit">
            Edit cart
          </Link>
        </OrderSummary>
      </div>
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

/** Why checkout failed; when Checkout names the Products that stopped it, lists them by name. */
function CheckoutError({ error, lines }: { error: unknown; lines: CartLine[] }) {
  const problem = error instanceof ApiError ? checkoutProblem(error.problem, lines) : undefined
  if (!problem) return <ErrorMessage error={error} />
  return (
    <div className="alert alert-danger" role="alert">
      <Icon name="alert" size={18} />
      <div>
        <strong>
          {problem.reason === 'outOfStock'
            ? 'Some items are out of stock'
            : "Some items aren't available any more"}
        </strong>
        <ul className="alert-list">
          {problem.products.map((product) => (
            <li key={product.variantId}>{product.name}</li>
          ))}
        </ul>
        <span>
          Change them in your <Link to="/cart">cart</Link>, then try again.
        </span>
      </div>
    </div>
  )
}
