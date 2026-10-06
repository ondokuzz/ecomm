import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useCategories, useDeleteCategory } from '../api/categories'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button, ButtonLink } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { Icon } from '../components/ui/Icon'
import type { Category } from '../domain/category'

/** What the editor tells the list after a save, through the navigation's state. */
export interface SavedState {
  saved?: string
}

const columns = 5

export function CategoryListPage() {
  const categories = useCategories()
  const remove = useDeleteCategory()
  const [deleting, setDeleting] = useState<Category>()
  const [deleted, setDeleted] = useState<string>()
  const location = useLocation()
  const navigate = useNavigate()
  // Read once, then dropped from history, so a reload or Back doesn't say it again.
  const [saved] = useState((location.state as SavedState | null)?.saved)
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])

  const confirmDelete = () => {
    if (!deleting) return
    remove.mutate(deleting.slug, {
      onSuccess: () => setDeleted(deleting.name),
      onSettled: () => setDeleting(undefined),
    })
  }

  const notice = deleted ? `Deleted ${deleted}.` : saved ? `Saved ${saved}.` : undefined

  return (
    <>
      <div className="page-heading">
        <h1>Categories</h1>
        <ButtonLink to="/categories/new" variant="primary">
          <Icon name="plus" size={16} />
          New Category
        </ButtonLink>
      </div>
      <p className="muted page-intro">
        A Category defines the attributes its Products carry. Variant axes, such as colour or storage, tell a Product's
        Variants apart.
      </p>

      <div role="status">
        {notice && (
          <div className="alert alert-success">
            <Icon name="check" />
            <span>{notice}</span>
          </div>
        )}
      </div>
      {remove.error && (
        <ErrorMessage
          error={remove.error}
          title={`Couldn't delete ${categories.data?.find((c) => c.slug === remove.variables)?.name ?? remove.variables}`}
        />
      )}

      {categories.error ? (
        <ErrorMessage
          error={categories.error}
          title="Couldn't load the Categories"
          retrying={categories.isFetching}
          onRetry={() => categories.refetch()}
        />
      ) : (
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Slug</th>
                <th scope="col">Attributes</th>
                <th scope="col" className="num">
                  Products
                </th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            {categories.data ? (
              <tbody>
                {categories.data.length === 0 && (
                  <tr>
                    <td colSpan={columns} className="table-empty">
                      No Categories yet. <Link to="/categories/new">Create the first one</Link>.
                    </td>
                  </tr>
                )}
                {categories.data.map((category) => (
                  <tr key={category.slug}>
                    <th scope="row">
                      <Link to={`/categories/${category.slug}`}>{category.name}</Link>
                    </th>
                    <td>
                      <code>{category.slug}</code>
                    </td>
                    <td>
                      <AttributeList category={category} />
                    </td>
                    <td className="num">{category.productCount}</td>
                    <td className="actions">
                      <ButtonLink
                        to={`/categories/${category.slug}`}
                        variant="ghost"
                        size="sm"
                        icon
                        aria-label={`Edit ${category.name}`}
                      >
                        <Icon name="edit" size={16} />
                      </ButtonLink>
                      <Button
                        variant="ghost"
                        size="sm"
                        icon
                        className="danger-text"
                        aria-label={`Delete ${category.name}`}
                        onClick={() => {
                          remove.reset()
                          setDeleted(undefined)
                          setDeleting(category)
                        }}
                      >
                        <Icon name="trash" size={16} />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            ) : (
              <SkeletonRows rows={3} columns={columns} />
            )}
          </table>
        </div>
      )}

      <ConfirmDialog
        open={deleting !== undefined}
        title={`Delete ${deleting?.name ?? 'Category'}?`}
        confirmLabel="Delete Category"
        confirming={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(undefined)}
      >
        {deleting && deleting.productCount > 0
          ? `It still has ${productCount(deleting.productCount)}, so Catalog will refuse until they are moved or deleted.`
          : "Its attribute definitions go with it. This can't be undone."}
      </ConfirmDialog>
    </>
  )
}

/** A Category's definitions in order, its Variant axes marked. */
function AttributeList({ category }: { category: Category }) {
  if (category.attributes.length === 0) return <span className="muted">None</span>
  return (
    <ul className="tag-list">
      {category.attributes.map((definition) => (
        <li key={definition.name}>
          {definition.variantAxis ? (
            <Badge tone="primary" title="Variant axis">
              {definition.name}
            </Badge>
          ) : (
            <Badge>{definition.name}</Badge>
          )}
        </li>
      ))}
    </ul>
  )
}

function productCount(count: number): string {
  return count === 1 ? '1 Product' : `${count} Products`
}
