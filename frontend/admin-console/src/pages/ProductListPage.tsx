import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useCategories } from '../api/categories'
import { useCurrencies } from '../api/currencies'
import { failureOf } from '../api/failure'
import { type DeletedProduct, useDeleteProduct, useProducts } from '../api/products'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { Icon } from '../components/ui/Icon'
import { Input } from '../components/ui/Input'
import { formatMoney } from '../domain/money'
import { type Product, matchesSearch } from '../domain/product'
import type { SavedState } from './CategoryListPage'

const columns = 6

/** Every Product, or one Category's (`?category=`), narrowed by a search of names and SKUs (`?q=`). */
export function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const category = params.get('category') ?? ''
  const query = params.get('q') ?? ''
  const products = useProducts(category || undefined)
  const categories = useCategories()
  const currencies = useCurrencies()
  const remove = useDeleteProduct()
  const [deleting, setDeleting] = useState<Product>()
  const [deleted, setDeleted] = useState<{ name: string } & DeletedProduct>()
  const location = useLocation()
  const navigate = useNavigate()
  // Read once, then dropped from history, so a reload or Back doesn't say it again.
  const [saved] = useState((location.state as SavedState | null)?.saved)
  useEffect(() => {
    if (location.state) navigate(location.pathname + location.search, { replace: true, state: null })
  }, [location.state, location.pathname, location.search, navigate])

  const setParam = (name: string, value: string) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (value) next.set(name, value)
        else next.delete(name)
        return next
      },
      { replace: true },
    )

  const confirmDelete = () => {
    if (!deleting) return
    remove.mutate(deleting, {
      onSuccess: (result) => setDeleted({ name: deleting.name, ...result }),
      onSettled: () => setDeleting(undefined),
    })
  }

  const categoryName = (slug: string) => categories.data?.find((c) => c.slug === slug)?.name ?? slug
  const shown = products.data?.filter((product) => matchesSearch(product, query))
  const notice = deleted ? `Deleted ${deleted.name}.` : saved ? `Saved ${saved}.` : undefined

  return (
    <>
      <div className="page-heading">
        <h1>Products</h1>
        <ButtonLink to={category ? `/products/new?category=${encodeURIComponent(category)}` : '/products/new'} variant="primary">
          <Icon name="plus" size={16} />
          New Product
        </ButtonLink>
      </div>
      <p className="muted page-intro">
        A Product is sold as one or more Variants, each with its own Price and Stock.
      </p>

      <div className="toolbar" role="search">
        <label className="field">
          Category
          <select className="input" value={category} onChange={(e) => setParam('category', e.target.value)}>
            <option value="">All Categories</option>
            {categories.data?.map((c) => (
              <option key={c.slug} value={c.slug}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Search
          <Input
            type="search"
            placeholder="Name or SKU"
            value={query}
            onChange={(e) => setParam('q', e.target.value)}
          />
        </label>
      </div>

      <div role="status">
        {notice && (
          <div className="alert alert-success">
            <Icon name="check" />
            <span>{notice}</span>
          </div>
        )}
      </div>
      {remove.error && <ErrorMessage error={remove.error} title={`Couldn't delete ${remove.variables?.name}`} />}
      {deleted && deleted.stockFailures.length > 0 && <StockLeft failures={deleted.stockFailures} />}

      {products.error ? (
        <ErrorMessage
          error={products.error}
          title="Couldn't load the Products"
          retrying={products.isFetching}
          onRetry={() => products.refetch()}
        />
      ) : (
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">SKU</th>
                <th scope="col">Category</th>
                <th scope="col" className="num">
                  Variants
                </th>
                <th scope="col" className="num">
                  Price from
                </th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            {shown ? (
              <tbody>
                {shown.length === 0 && (
                  <tr>
                    <td colSpan={columns} className="table-empty">
                      {products.data!.length === 0 ? (
                        <>
                          No Products yet. <Link to="/products/new">Create the first one</Link>.
                        </>
                      ) : (
                        'No Products match your search.'
                      )}
                    </td>
                  </tr>
                )}
                {shown.map((product) => (
                  <tr key={product.sku}>
                    <th scope="row">
                      <Link to={`/products/${encodeURIComponent(product.sku)}`}>{product.name}</Link>
                    </th>
                    <td>
                      <code>{product.sku}</code>
                    </td>
                    <td>{categoryName(product.category)}</td>
                    <td className="num">{product.variants.length}</td>
                    <td className="num">{currencies.data && formatMoney(product.priceFrom, currencies.data)}</td>
                    <td className="actions">
                      <ButtonLink
                        to={`/products/${encodeURIComponent(product.sku)}`}
                        variant="ghost"
                        size="sm"
                        icon
                        aria-label={`Edit ${product.name}`}
                      >
                        <Icon name="edit" size={16} />
                      </ButtonLink>
                      <Button
                        variant="ghost"
                        size="sm"
                        icon
                        className="danger-text"
                        aria-label={`Delete ${product.name}`}
                        onClick={() => {
                          remove.reset()
                          setDeleted(undefined)
                          setDeleting(product)
                        }}
                      >
                        <Icon name="trash" size={16} />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            ) : (
              <SkeletonRows rows={5} columns={columns} />
            )}
          </table>
        </div>
      )}

      <ConfirmDialog
        open={deleting !== undefined}
        title={`Delete ${deleting?.name ?? 'Product'}?`}
        confirmLabel="Delete Product"
        confirming={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(undefined)}
      >
        {deleting &&
          `${variantsAndStock(deleting.variants.length)} go with it, and Customers can no longer buy them. This can't be undone.`}
      </ConfirmDialog>
    </>
  )
}

/** The Variants of a deleted Product whose Stock Inventory kept, each with Inventory's reason. */
function StockLeft({ failures }: { failures: DeletedProduct['stockFailures'] }) {
  return (
    <div className="alert alert-danger" role="alert">
      <Icon name="alert" />
      <div className="alert-body">
        <strong>Inventory still stocks some of its Variants</strong>
        <ul className="plain-list">
          {failures.map(({ variantId, error }) => (
            <li key={variantId}>
              <code>{variantId}</code>: {failureOf(error).message}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

function variantsAndStock(count: number): string {
  return count === 1 ? 'Its Variant and its Stock' : `All ${count} of its Variants and their Stock`
}
