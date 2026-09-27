import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from 'react-oidc-context'
import { useCart, useSetQuantity } from '../api/cart'
import { useProduct, useStock } from '../api/catalog'
import { useAuthPending, useSignin } from '../auth/session'
import { ErrorMessage, Loading } from '../components/Status'
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
      <p>
        We don't have that product. <Link to="/">Back to products</Link>
      </p>
    )
  }
  return <ProductDetail product={product.data} />
}

function ProductDetail({ product }: { product: Product }) {
  const variant = defaultVariant(product)
  const stock = useStock(variant.id)

  return (
    <article className="product-detail">
      <Link to={`/?category=${encodeURIComponent(product.category)}`} className="muted">
        ← {product.category}
      </Link>
      <h1>{product.name}</h1>
      <p className="price large">{formatMoney(variant.price)}</p>
      <p>
        {stock.isPending ? (
          <span className="muted">Checking stock…</span>
        ) : stock.data === undefined ? (
          <span className="muted">Stock unknown</span>
        ) : stock.data > 0 ? (
          <span className="in-stock">In stock: {stock.data}</span>
        ) : (
          <span className="out-of-stock">Out of stock</span>
        )}
      </p>
      <dl className="attributes">
        {Object.entries(product.attributes).map(([name, value]) => (
          <div key={name}>
            <dt>{name}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <AddToCart variantId={variant.id} available={stock.data} />
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
      <p>
        <button className="primary" onClick={signin}>
          Log in to add to cart
        </button>
      </p>
    )
  }

  const inCart = cart.data ? quantityOf(cart.data, variantId) : 0
  const add = () => setQuantity.mutate({ variantId, quantity: inCart + quantity })

  return (
    <div className="add-to-cart">
      <label>
        Quantity{' '}
        <input
          type="number"
          min={1}
          value={quantity}
          onChange={(e) => setQuantityInput(Math.max(1, Math.floor(Number(e.target.value)) || 1))}
        />
      </label>
      <button className="primary" onClick={add} disabled={!cart.data || setQuantity.isPending || available === 0}>
        Add to cart
      </button>
      {inCart > 0 && (
        <span className="muted">
          {inCart} in your <Link to="/cart">cart</Link>
        </span>
      )}
      {setQuantity.error && <ErrorMessage error={setQuantity.error} />}
    </div>
  )
}
