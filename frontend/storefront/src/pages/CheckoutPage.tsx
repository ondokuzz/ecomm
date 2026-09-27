import { Link, useNavigate } from 'react-router'
import { usePricedCart } from '../api/cart'
import { ApiError } from '../api/http'
import { useCheckout } from '../api/orders'
import { CartLines } from '../components/CartLines'
import { ErrorMessage, Loading } from '../components/Status'

export function CheckoutPage() {
  const { cart, priced } = usePricedCart()
  const checkout = useCheckout()
  const navigate = useNavigate()

  if (cart.isPending) return <Loading />
  if (cart.error) return <ErrorMessage error={cart.error} />
  if (!priced || priced.lines.length === 0) {
    return (
      <p>
        Your cart is empty. <Link to="/">Browse products</Link>
      </p>
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
      <div className="payment">
        <h2>Payment</h2>
        <p className="muted">This is a demo: the payment is mocked and always succeeds.</p>
        <button className="primary" onClick={pay} disabled={checkout.isPending}>
          {checkout.isPending ? 'Paying…' : 'Pay'}
        </button>
        {checkout.error && <CheckoutError error={checkout.error} />}
      </div>
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
