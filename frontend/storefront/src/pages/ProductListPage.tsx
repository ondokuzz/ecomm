import { Link, useSearchParams } from 'react-router'
import { useCategories, useProducts } from '../api/catalog'
import { ErrorMessage, Loading } from '../components/Status'
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
            <button className={category ? '' : 'selected'} onClick={() => choose()}>
              All
            </button>
          </li>
          {categories.data?.map((c) => (
            <li key={c.category}>
              <button className={c.category === category ? 'selected' : ''} onClick={() => choose(c.category)}>
                {c.category} <span className="muted">({c.productCount})</span>
              </button>
            </li>
          ))}
        </ul>
        {categories.error && <ErrorMessage error={categories.error} />}
      </aside>
      <section>
        <h1 className="capitalize">{category ?? 'All products'}</h1>
        {products.isPending && <Loading />}
        {products.error && <ErrorMessage error={products.error} />}
        {products.data?.length === 0 && <p className="muted">No products here.</p>}
        <ul className="product-grid">
          {products.data?.map((p) => (
            <li key={p.sku}>
              <Link to={`/products/${encodeURIComponent(p.sku)}`} className="product-card">
                <span className="product-name">{p.name}</span>
                <span className="muted">{p.attributes.brand}</span>
                <span className="price">{formatMoney(p.price)}</span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
