import { Link, useSearchParams } from 'react-router'
import { useCategories, useProducts } from '../api/catalog'
import { ProductImage } from '../components/ProductImage'
import { EmptyState, ErrorMessage } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Skeleton } from '../components/ui/Skeleton'
import { formatMoney } from '../domain/money'

export function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const category = params.get('category') || undefined
  const categories = useCategories()
  const products = useProducts(category)

  const choose = (next?: string) => setParams(next ? { category: next } : {})

  return (
    <div className="catalog">
      <aside>
        <h2>Categories</h2>
        <ul className="categories">
          <li>
            <Button variant="ghost" aria-pressed={!category} onClick={() => choose()}>
              All
            </Button>
          </li>
          {categories.data?.map((c) => (
            <li key={c.category}>
              <Button variant="ghost" aria-pressed={c.category === category} onClick={() => choose(c.category)}>
                {c.category} <span className="badge">{c.productCount}</span>
              </Button>
            </li>
          ))}
        </ul>
        {categories.error && <ErrorMessage error={categories.error} />}
      </aside>
      <section>
        <h1 className="capitalize">{category ?? 'All products'}</h1>
        {products.error && <ErrorMessage error={products.error} />}
        {products.data?.length === 0 && (
          <EmptyState title="Nothing here yet" action={<ButtonLink to="/">See all products</ButtonLink>}>
            We have no products in this category.
          </EmptyState>
        )}
        <ul className="product-grid" aria-busy={products.isPending}>
          {products.isPending && <ProductCardSkeletons />}
          {products.data?.map((p) => (
            <li key={p.sku}>
              <Link to={`/products/${encodeURIComponent(p.sku)}`} className="card-link">
                <Card interactive className="product-card">
                  <ProductImage product={p} />
                  <span className="brand-name">{p.attributes.brand}</span>
                  <span className="product-name">{p.name}</span>
                  <span className="price">{formatMoney(p.price)}</span>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
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
