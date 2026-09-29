import { useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useCategories, useProducts } from '../api/catalog'
import { ProductImage } from '../components/ProductImage'
import { EmptyState, ErrorMessage, ErrorState } from '../components/Status'
import { ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Skeleton } from '../components/ui/Skeleton'
import { type CategoryChip, type Product, cardPrice, categoryChips, productCountLabel } from '../domain/catalog'
import { formatMoney } from '../domain/money'

export function ProductListPage() {
  const [params] = useSearchParams()
  const category = params.get('category') || undefined
  const products = useProducts(category)

  return (
    <div className="catalog">
      {category ? (
        <header className="category-header">
          <h1 className="capitalize">{category}</h1>
          {products.data && <p>{productCountLabel(products.data.length)}</p>}
        </header>
      ) : (
        <header className="hero">
          <p className="hero-eyebrow">New season, new gear</p>
          <h1>Find your next favourite gadget</h1>
          <p className="hero-tagline">Phones, laptops and audio from the brands you love, all in one place.</p>
        </header>
      )}
      <CategoryChips category={category} />
      {products.error ? (
        <ErrorState
          title="We couldn't load the products"
          error={products.error}
          retrying={products.isFetching}
          onRetry={() => products.refetch()}
        />
      ) : products.data?.length === 0 ? (
        <EmptyState
          title="Nothing here yet"
          illustration={<EmptyShelf />}
          action={category && <ButtonLink to="/">See all products</ButtonLink>}
        >
          {category ? 'We have no products in this category.' : 'We have no products yet. Check back soon.'}
        </EmptyState>
      ) : (
        <ul className="product-grid" aria-busy={products.isPending} aria-label="Products">
          {products.data ? products.data.map((p) => <ProductCard key={p.sku} product={p} />) : <ProductCardSkeletons />}
        </ul>
      )}
    </div>
  )
}

/** "All" and each category, with its Product count; choosing one puts it in `?category=`. */
function CategoryChips({ category }: { category: string | undefined }) {
  const categories = useCategories()
  const row = useRef<HTMLUListElement>(null)
  const loaded = categories.data !== undefined

  // A category loaded straight from the URL may sit off-screen in the chip row on a phone. This
  // scrolls the row alone, never the page.
  useEffect(() => {
    const selected = row.current?.querySelector<HTMLElement>('[aria-current]')
    if (row.current && selected) {
      row.current.scrollLeft = selected.offsetLeft - (row.current.clientWidth - selected.offsetWidth) / 2
    }
  }, [category, loaded])

  return (
    <nav className="chips-nav" aria-label="Categories">
      <ul className="chips" ref={row}>
        {categories.data
          ? categoryChips(categories.data, category).map((chip) => <Chip key={chip.label} chip={chip} />)
          : categories.isPending &&
            Array.from({ length: 4 }, (_, i) => (
              <li key={i}>
                <Skeleton width="6.5rem" height="2.5rem" radius="var(--radius-pill)" />
              </li>
            ))}
      </ul>
      {categories.error && <ErrorMessage error={categories.error} />}
    </nav>
  )
}

function Chip({ chip }: { chip: CategoryChip }) {
  return (
    <li>
      <Link
        to={chip.category ? `/?${new URLSearchParams({ category: chip.category })}` : '/'}
        className="chip"
        aria-current={chip.selected || undefined}
      >
        <span className="capitalize">{chip.label}</span>
        <span className="chip-count">{chip.productCount}</span>
      </Link>
    </li>
  )
}

function ProductCard({ product }: { product: Product }) {
  return (
    <li>
      <Link to={`/products/${encodeURIComponent(product.sku)}`} className="card-link">
        <Card interactive className="product-card">
          <div className="product-card-media">
            <ProductImage product={product} />
          </div>
          <span className="brand-name">{product.attributes.brand}</span>
          <span className="product-name">{product.name}</span>
          <CardPrice product={product} />
        </Card>
      </Link>
    </li>
  )
}

/** The card's Price: "From" the lowest when the Variants' Prices differ. */
function CardPrice({ product }: { product: Product }) {
  const { price, from } = cardPrice(product)
  return (
    <span className="price">
      {from && <span className="price-from">From </span>}
      {formatMoney(price)}
    </span>
  )
}

function ProductCardSkeletons() {
  return Array.from({ length: 8 }, (_, i) => (
    <li key={i}>
      <Card className="product-card">
        <Skeleton height="auto" radius="var(--radius-md)" className="product-image-skeleton" />
        <Skeleton width="30%" height="0.75rem" />
        <Skeleton width="80%" height="1.25rem" />
        <Skeleton width="40%" height="1.25rem" />
      </Card>
    </li>
  ))
}

/** An empty shelf with a lone box, for a category with no Products. */
function EmptyShelf() {
  return (
    <svg viewBox="0 0 200 140" width="200" height="140" aria-hidden="true" className="empty-illustration">
      <ellipse cx="100" cy="126" rx="78" ry="8" className="empty-shadow" />
      <rect x="24" y="96" width="152" height="10" rx="5" className="empty-shelf" />
      <rect x="36" y="106" width="8" height="18" rx="3" className="empty-shelf" />
      <rect x="156" y="106" width="8" height="18" rx="3" className="empty-shelf" />
      <g transform="rotate(-6 100 70)">
        <rect x="70" y="48" width="60" height="46" rx="6" className="empty-box" />
        <path d="M70 60h60" className="empty-tape" />
        <path d="M100 48v12" className="empty-tape" />
      </g>
      <circle cx="54" cy="36" r="6" className="empty-spark" />
      <circle cx="150" cy="28" r="4" className="empty-spark-2" />
      <path d="M160 58l4 8 8 4-8 4-4 8-4-8-8-4 8-4z" className="empty-spark" />
    </svg>
  )
}
