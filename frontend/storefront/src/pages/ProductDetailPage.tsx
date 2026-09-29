import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useAuth } from 'react-oidc-context'
import { useCart, useSetQuantity } from '../api/cart'
import { useCategory, useProduct, useStock, useStocks } from '../api/catalog'
import { useAuthPending, useSignin } from '../auth/session'
import { ProductImage } from '../components/ProductImage'
import { EmptyState, ErrorMessage, ErrorState } from '../components/Status'
import { VariantPicker } from '../components/VariantPicker'
import { Badge, type Tone } from '../components/ui/Badge'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { QuantityStepper } from '../components/ui/QuantityStepper'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/toasts'
import { quantityOf } from '../domain/cart'
import { type Product, chosenVariant, specs, variantAxes } from '../domain/catalog'
import { formatMoney } from '../domain/money'
import { type StockLevel, stockLevel } from '../domain/stock'

export function ProductDetailPage() {
  const { sku = '' } = useParams()
  const product = useProduct(sku)

  if (product.isPending) return <ProductDetailSkeleton />
  if (product.error) {
    return (
      <ErrorState
        title="We couldn't load this product"
        error={product.error}
        retrying={product.isFetching}
        onRetry={() => product.refetch()}
      />
    )
  }
  if (!product.data) {
    return (
      <EmptyState
        title="Product not found"
        illustration={
          <span className="empty-icon">
            <Icon name="search" size={28} />
          </span>
        }
        action={
          <ButtonLink variant="primary" to="/">
            Back to products
          </ButtonLink>
        }
      >
        We couldn't find a product with the code <code>{sku}</code>. It may have been removed from the Catalog.
      </EmptyState>
    )
  }
  return <ProductDetail product={product.data} />
}

function ProductDetail({ product }: { product: Product }) {
  // The chosen Variant lives in the URL, so a reload or a shared link keeps it.
  const [params, setParams] = useSearchParams()
  const variant = chosenVariant(product, params.get('variant'))
  const stock = useStock(variant.id)
  const stocks = useStocks(product.variants.map((v) => v.id))
  // How many units Inventory has; undefined while unknown, or when it has no Stock record.
  const available = stock.data ?? undefined
  // Until the Category loads (or if it fails to), the specs keep the Product's own order.
  const category = useCategory(product.category)
  const definitions = category.data?.attributes
  const rows = specs({ ...product.attributes, ...variant.axisValues }, definitions)
  const axes = variantAxes(product, variant, definitions, stocks)

  return (
    <article className="product-page">
      <div className="product-media">
        <ProductImage product={product} variant={variant} />
      </div>
      <div className="product-info">
        <Card className="purchase-panel">
          <Breadcrumb product={product} />
          {product.attributes.brand && <span className="brand-name">{product.attributes.brand}</span>}
          <h1>{product.name}</h1>
          <p className="price large">{formatMoney(variant.price)}</p>
          {axes.length > 0 && (
            <VariantPicker
              axes={axes}
              onChoose={(chosen) => setParams({ variant: chosen.id }, { replace: true, preventScrollReset: true })}
            />
          )}
          {stock.isPending ? <Badge>Checking stock…</Badge> : <StockIndicator quantity={available} />}
          {/* A fresh quantity for each Variant. */}
          <AddToCart key={variant.id} productName={product.name} variantId={variant.id} available={available} />
        </Card>
        {rows.length > 0 && (
          <section className="specs" aria-labelledby="specs-heading">
            <h2 id="specs-heading">Specifications</h2>
            <table className="spec-table">
              <tbody>
                {rows.map(({ name, value }) => (
                  <tr key={name}>
                    <th scope="row">{name}</th>
                    <td>{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        )}
      </div>
    </article>
  )
}

function Breadcrumb({ product }: { product: Product }) {
  return (
    <nav aria-label="Breadcrumb" className="breadcrumb">
      <ol>
        <li>
          <Link to="/">Products</Link>
        </li>
        <li>
          <Link to={`/?${new URLSearchParams({ category: product.category })}`} className="capitalize">
            {product.category}
          </Link>
        </li>
        <li aria-current="page">{product.name}</li>
      </ol>
    </nav>
  )
}

const stockTones: Record<StockLevel, Tone> = { in: 'success', low: 'warning', out: 'danger', unknown: 'neutral' }

/** In stock, low stock or out of stock, as a coloured pill. */
function StockIndicator({ quantity }: { quantity: number | undefined }) {
  const { level, label } = stockLevel(quantity)
  return (
    <Badge tone={stockTones[level]} dot={level !== 'unknown'} className="stock-indicator">
      {label}
    </Badge>
  )
}

function AddToCart({
  productName,
  variantId,
  available,
}: {
  productName: string
  variantId: string
  available?: number
}) {
  const auth = useAuth()
  const pending = useAuthPending()
  const { signin } = useSignin()
  const cart = useCart()
  const setQuantity = useSetQuantity()
  const toast = useToast()
  const [quantity, setQuantityInput] = useState(1)

  if (pending) return null
  if (!auth.isAuthenticated) {
    return (
      <div className="signin-callout">
        <p>
          <strong>Want this one?</strong> Log in to add it to your cart and check out.
        </p>
        <Button variant="primary" size="lg" onClick={signin}>
          Log in to add to cart
        </Button>
      </div>
    )
  }

  const outOfStock = stockLevel(available).level === 'out'
  const inCart = cart.data ? quantityOf(cart.data, variantId) : 0
  const add = () =>
    setQuantity.mutate(
      { variantId, quantity: inCart + quantity },
      { onSuccess: () => toast({ message: 'Added to cart', action: { label: 'View cart', to: '/cart' } }) },
    )

  return (
    <div className="add-to-cart">
      <div className="add-to-cart-controls">
        <QuantityStepper
          value={quantity}
          onChange={setQuantityInput}
          max={available || undefined}
          disabled={outOfStock}
          label={`Quantity of ${productName}`}
        />
        <Button
          variant="primary"
          size="lg"
          className="add-to-cart-button"
          onClick={add}
          loading={setQuantity.isPending}
          disabled={!cart.data || outOfStock}
        >
          <Icon name="cart" size={18} />
          Add to cart
        </Button>
      </div>
      {inCart > 0 && (
        <span className="muted">
          {inCart} in your <Link to="/cart">cart</Link>
        </span>
      )}
      {setQuantity.error && <ErrorMessage error={setQuantity.error} />}
    </div>
  )
}

/** The Product page's shape while the Product loads; screen readers hear "Loading…". */
function ProductDetailSkeleton() {
  return (
    <div className="product-page" role="status">
      <span className="visually-hidden">Loading…</span>
      <Skeleton height="auto" radius="var(--radius-lg)" className="product-image-skeleton" />
      <Card className="purchase-panel">
        <Skeleton width="50%" height="0.875rem" />
        <Skeleton width="80%" height="2.25rem" radius="var(--radius-md)" />
        <Skeleton width="35%" height="2rem" radius="var(--radius-md)" />
        <Skeleton width="6rem" height="1.5rem" radius="var(--radius-pill)" />
        <Skeleton height="3rem" radius="var(--radius-pill)" />
      </Card>
    </div>
  )
}
