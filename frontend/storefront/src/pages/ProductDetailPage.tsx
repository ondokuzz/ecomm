import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from 'react-oidc-context'
import { useCart, useSetQuantity } from '../api/cart'
import { useProduct, useStock } from '../api/catalog'
import { useAuthPending, useSignin } from '../auth/session'
import { EmptyState, ErrorMessage, Loading } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { QuantityStepper } from '../components/ui/QuantityStepper'
import { quantityOf } from '../domain/cart'
import { type Product, defaultVariant } from '../domain/catalog'
import { formatMoney } from '../domain/money'

export function ProductDetailPage() {
  const { sku = '' } = useParams()
  const product = useProduct(sku)

  if (product.isPending) return <Loading />
  if (product.error) return <ErrorMessage error={product.error} />
  if (!product.data) {
    return (
      <EmptyState title="We don't have that product" action={<ButtonLink to="/">Back to products</ButtonLink>}>
        It may have been removed from the Catalog.
      </EmptyState>
    )
  }
  return <ProductDetail product={product.data} />
}

function ProductDetail({ product }: { product: Product }) {
  const variant = defaultVariant(product)
  const stock = useStock(variant.id)

  return (
    <article className="product-detail">
      <Link to={`/?category=${encodeURIComponent(product.category)}`} className="back-link">
        <Icon name="arrowLeft" size={16} /> {product.category}
      </Link>
      <Card>
        <h1>{product.name}</h1>
        <div className="row">
          <p className="price large">{formatMoney(variant.price)}</p>
          {stock.isPending ? (
            <Badge>Checking stock…</Badge>
          ) : stock.data === undefined ? (
            <Badge>Stock unknown</Badge>
          ) : stock.data > 0 ? (
            <Badge tone="success" dot>
              In stock: {stock.data}
            </Badge>
          ) : (
            <Badge tone="danger" dot>
              Out of stock
            </Badge>
          )}
        </div>
        <dl className="attributes">
          {Object.entries(product.attributes).map(([name, value]) => (
            <div key={name}>
              <dt>{name}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
        <AddToCart variantId={variant.id} available={stock.data} />
      </Card>
    </article>
  )
}

function AddToCart({ variantId, available }: { variantId: string; available?: number }) {
  const auth = useAuth()
  const pending = useAuthPending()
  const { signin } = useSignin()
  const cart = useCart()
  const setQuantity = useSetQuantity()
  const [quantity, setQuantityInput] = useState(1)

  if (pending) return null
  if (!auth.isAuthenticated) {
    return (
      <div className="add-to-cart">
        <Button variant="primary" size="lg" onClick={signin}>
          Log in to add to cart
        </Button>
      </div>
    )
  }

  const inCart = cart.data ? quantityOf(cart.data, variantId) : 0
  const add = () => setQuantity.mutate({ variantId, quantity: inCart + quantity })

  return (
    <div className="add-to-cart">
      <QuantityStepper value={quantity} onChange={setQuantityInput} disabled={available === 0} />
      <Button
        variant="primary"
        size="lg"
        onClick={add}
        loading={setQuantity.isPending}
        disabled={!cart.data || available === 0}
      >
        <Icon name="cart" size={18} />
        Add to cart
      </Button>
      {inCart > 0 && (
        <span className="muted">
          {inCart} in your <Link to="/cart">cart</Link>
        </span>
      )}
      {setQuantity.error && <ErrorMessage error={setQuantity.error} />}
    </div>
  )
}
