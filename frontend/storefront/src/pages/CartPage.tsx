import { useState } from 'react'
import { useClearCart, usePricedCart, useRemoveFromCart, useSetQuantity } from '../api/cart'
import { useStocks } from '../api/catalog'
import { CartSkeleton } from '../components/PageSkeletons'
import { ProductLines } from '../components/ProductLines'
import { EmptyCart } from '../components/EmptyCart'
import { OrderSummary } from '../components/OrderSummary'
import { ErrorMessage, ErrorState, LookupError } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { Icon } from '../components/ui/Icon'
import { type CartLine } from '../domain/cart'
import { shortLines } from '../domain/stock'

export function CartPage() {
  const { cart, priced, variantsFailure } = usePricedCart()
  const setQuantity = useSetQuantity()
  const remove = useRemoveFromCart()
  const clear = useClearCart()
  const [confirmingClear, setConfirmingClear] = useState(false)
  const { stocks, failure: stocksFailure } = useStocks(cart.data?.items.map((item) => item.variantId) ?? [])

  if (cart.isPending) return <CartSkeleton />
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
      <LookupError failure={variantsFailure} title="We couldn't load your items' names and prices" />
      <LookupError failure={stocksFailure} title="We couldn't check the stock" />
      <div className="cart-layout">
        <div className="cart-main">
          <ProductLines
            lines={priced.lines}
            label="Cart lines"
            onChange={(variantId, quantity) => setQuantity.mutate({ variantId, quantity })}
            onRemove={(variantId) => remove.mutate(variantId)}
            disabled={busy}
            stocks={stocks}
          />
          {setQuantity.error && <ErrorMessage error={setQuantity.error} />}
          {remove.error && <ErrorMessage error={remove.error} />}
        </div>
        <OrderSummary cart={cart.data} priced={priced}>
          <ShortStockWarning lines={shortLines(priced.lines, stocks)} />
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

/** Before checkout, the lines it would refuse for want of Stock; nothing when there are none. */
function ShortStockWarning({ lines }: { lines: CartLine[] }) {
  if (lines.length === 0) return null
  return (
    <div className="alert alert-warning cart-stock-warning" role="status">
      <Icon name="alert" size={18} />
      <div className="alert-body">
        <strong>Not enough stock for everything in your cart</strong>
        <ul className="alert-list">
          {lines.map((line) => (
            <li key={line.variantId}>{line.name ?? line.variantId}</li>
          ))}
        </ul>
        <span>Lower the quantity or remove these before checkout, or checkout will stop.</span>
      </div>
    </div>
  )
}
