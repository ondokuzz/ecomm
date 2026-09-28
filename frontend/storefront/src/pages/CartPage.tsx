import { useState } from 'react'
import { useClearCart, usePricedCart, useRemoveFromCart, useSetQuantity } from '../api/cart'
import { ProductLines } from '../components/ProductLines'
import { EmptyCart } from '../components/EmptyCart'
import { OrderSummary } from '../components/OrderSummary'
import { ErrorMessage, Loading } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { Icon } from '../components/ui/Icon'

export function CartPage() {
  const { cart, priced } = usePricedCart()
  const setQuantity = useSetQuantity()
  const remove = useRemoveFromCart()
  const clear = useClearCart()
  const [confirmingClear, setConfirmingClear] = useState(false)

  if (cart.isPending) return <Loading />
  if (cart.error) return <ErrorMessage error={cart.error} />
  // Emptying the Cart removes the button and dialog that had focus, so focus moves to the empty state.
  if (!priced || priced.lines.length === 0) return <EmptyCart focusTitle={clear.isSuccess} />

  const busy = setQuantity.isPending || remove.isPending || clear.isPending
  return (
    <section>
      <div className="page-heading">
        <h1>Your cart</h1>
        <Button variant="ghost" size="sm" onClick={() => setConfirmingClear(true)} disabled={busy}>
          <Icon name="trash" size={16} />
          Empty cart
        </Button>
      </div>
      {clear.error && <ErrorMessage error={clear.error} />}
      <div className="cart-layout">
        <div className="cart-main">
          <ProductLines
            lines={priced.lines}
            label="Cart lines"
            onChange={(variantId, quantity) => setQuantity.mutate({ variantId, quantity })}
            onRemove={(variantId) => remove.mutate(variantId)}
            disabled={busy}
          />
          {setQuantity.error && <ErrorMessage error={setQuantity.error} />}
          {remove.error && <ErrorMessage error={remove.error} />}
        </div>
        <OrderSummary cart={cart.data} priced={priced}>
          <ButtonLink variant="primary" size="lg" to="/checkout" className="order-summary-action">
            Go to checkout
            <Icon name="arrowRight" size={18} />
          </ButtonLink>
          <p className="muted order-summary-note">Prices are Catalog's current ones; checkout confirms them.</p>
        </OrderSummary>
      </div>
      <ConfirmDialog
        open={confirmingClear}
        title="Empty your cart?"
        confirmLabel="Empty cart"
        confirming={clear.isPending}
        onCancel={() => setConfirmingClear(false)}
        onConfirm={() => clear.mutate(undefined, { onSettled: () => setConfirmingClear(false) })}
      >
        This removes every item from your cart.
      </ConfirmDialog>
    </section>
  )
}
