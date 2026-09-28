import { Link, useNavigate } from 'react-router'
import { usePricedCart } from '../api/cart'
import { ApiError } from '../api/http'
import { useCheckout } from '../api/orders'
import { CartLines } from '../components/CartLines'
import { EmptyState, ErrorMessage, Loading } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'

export function CheckoutPage() {
  const { cart, priced } = usePricedCart()
  const checkout = useCheckout()
  const navigate = useNavigate()

  if (cart.isPending) return <Loading />
  if (cart.error) return <ErrorMessage error={cart.error} />
  if (!priced || priced.lines.length === 0) {
    return (
      <EmptyState
        title="Your cart is empty"
        action={
          <ButtonLink variant="primary" to="/">
            Browse products
          </ButtonLink>
        }
      >
        There is nothing to check out yet.
      </EmptyState>
    )
  }

  const pay = () =>
    checkout.mutate(undefined, {
      onSuccess: (result) => navigate(`/orders/${encodeURIComponent(result.orderId)}?placed`, { replace: true }),
    })

  return (
    <section>
      <h1>Checkout</h1>
      <CartLines priced={priced} />
      <Card className="payment">
        <h2>Payment</h2>
        <p className="muted">This is a demo: the payment is mocked and always succeeds.</p>
        <Button variant="primary" size="lg" onClick={pay} loading={checkout.isPending}>
          {checkout.isPending ? 'Paying…' : 'Pay'}
        </Button>
        {checkout.error && <CheckoutError error={checkout.error} />}
      </Card>
    </section>
  )
}

/** Names the Variants that stopped checkout, when Checkout says which. */
function CheckoutError({ error }: { error: unknown }) {
  const variants = error instanceof ApiError ? (error.problem.outOfStock ?? error.problem.unknownVariants) : undefined
  return (
    <>
      <ErrorMessage error={error} />
      {Array.isArray(variants) && (
        <p>
          Check these in your <Link to="/cart">cart</Link>: {variants.join(', ')}
        </p>
      )}
    </>
  )
}
