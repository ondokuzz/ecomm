import { usePricedCart, useRemoveFromCart, useSetQuantity } from '../api/cart'
import { CartLines } from '../components/CartLines'
import { EmptyState, ErrorMessage, Loading } from '../components/Status'
import { ButtonLink } from '../components/ui/Button'

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
        <EmptyState
          title="Your cart is empty"
          action={
            <ButtonLink variant="primary" to="/">
              Browse products
            </ButtonLink>
          }
        >
          Find something you like and add it here.
        </EmptyState>
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
          <div className="page-actions">
            <p className="muted">Prices are Catalog's current ones; checkout confirms them.</p>
            <ButtonLink variant="primary" size="lg" to="/checkout">
              Go to checkout
            </ButtonLink>
          </div>
        </>
      )}
    </section>
  )
}
