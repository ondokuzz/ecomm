import { Link } from 'react-router'
import { usePricedCart, useRemoveFromCart, useSetQuantity } from '../api/cart'
import { CartLines } from '../components/CartLines'
import { ErrorMessage, Loading } from '../components/Status'

export function CartPage() {
  const { cart, priced } = usePricedCart()
  const setQuantity = useSetQuantity()
  const remove = useRemoveFromCart()

  if (cart.isPending) return <Loading />
  if (cart.error) return <ErrorMessage error={cart.error} />

  return (
    <section>
      <h1>Your cart</h1>
      {!priced || priced.lines.length === 0 ? (
        <p>
          Your cart is empty. <Link to="/">Browse products</Link>
        </p>
      ) : (
        <>
          <CartLines
            priced={priced}
            onChange={(variantId, quantity) => setQuantity.mutate({ variantId, quantity })}
            onRemove={(variantId) => remove.mutate(variantId)}
            disabled={setQuantity.isPending || remove.isPending}
          />
          {setQuantity.error && <ErrorMessage error={setQuantity.error} />}
          {remove.error && <ErrorMessage error={remove.error} />}
          <p className="muted">Prices are Catalog's current ones; checkout confirms them.</p>
          <Link to="/checkout" className="button primary">
            Go to checkout
          </Link>
        </>
      )}
    </section>
  )
}
