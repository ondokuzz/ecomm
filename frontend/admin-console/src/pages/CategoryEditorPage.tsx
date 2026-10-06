import { type FormEvent, useId, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useCategory, useSaveCategory } from '../api/categories'
import { isNotFound } from '../api/failure'
import { type FieldErrors, fieldErrorsOf } from '../api/fieldErrors'
import { NotFound } from '../components/NotFound'
import { ErrorMessage, FieldError } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { Input } from '../components/ui/Input'
import {
  type AttributeType,
  type Category,
  type CategoryForm,
  type DefinitionForm,
  attributeTypes,
  blankDefinition,
  categoryRequest,
  formOf,
} from '../domain/category'
import type { SavedState } from './CategoryListPage'

/** `/categories/new` creates a Category; `/categories/:slug` edits one. */
export function CategoryEditorPage() {
  const { slug } = useParams()
  const category = useCategory(slug)

  if (slug === undefined) return <CategoryEditor />
  if (isNotFound(category.error)) return <NotFound what="Category" />
  if (category.error) {
    return (
      <>
        <BackLink />
        <ErrorMessage
          error={category.error}
          title="Couldn't load the Category"
          retrying={category.isFetching}
          onRetry={() => category.refetch()}
        />
      </>
    )
  }
  if (!category.data) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  return <CategoryEditor key={slug} category={category.data} />
}

function BackLink() {
  return (
    <Link to="/categories" className="back-link">
      <Icon name="arrowLeft" size={16} />
      Categories
    </Link>
  )
}

/**
 * The Category form: its name, its slug when it is new, and a table of attribute definitions in
 * order. Catalog checks what is sent; each field it refuses shows its message beside that field
 * until it is edited.
 */
function CategoryEditor({ category }: { category?: Category }) {
  const navigate = useNavigate()
  const save = useSaveCategory(category?.slug)
  const [form, setForm] = useState<CategoryForm>(() => formOf(category))
  const [errors, setErrors] = useState<FieldErrors>({})
  const id = useId()
  const errorId = (field: string) => `${id}-${field.replace(/[^\w-]/g, '-')}-error`

  const field = (name: string) => ({
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? errorId(name) : undefined,
  })
  const clearError = (name: string) =>
    setErrors((current) => {
      if (!(name in current)) return current
      const { [name]: _, ...rest } = current
      return rest
    })
  // Rows renumber when one is added, removed or moved, so their errors no longer name the right row.
  const clearAttributeErrors = () =>
    setErrors((current) =>
      Object.fromEntries(Object.entries(current).filter(([name]) => !name.startsWith('attributes'))),
    )

  const setDefinition = <K extends keyof DefinitionForm>(index: number, key: K, value: DefinitionForm[K]) => {
    setForm((current) => ({
      ...current,
      attributes: current.attributes.map((definition, i) =>
        i === index ? { ...definition, [key]: value } : definition,
      ),
    }))
    clearError(`attributes[${index}].${key}`)
    if (key === 'type') clearError(`attributes[${index}].values`)
  }
  const setAttributes = (change: (attributes: DefinitionForm[]) => DefinitionForm[]) => {
    setForm((current) => ({ ...current, attributes: change(current.attributes) }))
    clearAttributeErrors()
  }
  const move = (index: number, by: -1 | 1) =>
    setAttributes((attributes) => {
      const moved = [...attributes]
      const [definition] = moved.splice(index, 1)
      moved.splice(index + by, 0, definition!)
      return moved
    })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const request = categoryRequest(form)
    save.mutate(request, {
      onSuccess: () => navigate('/categories', { state: { saved: request.name } satisfies SavedState }),
      onError: (error) => setErrors(fieldErrorsOf(error)),
    })
  }

  const title = category ? category.name : 'New Category'
  return (
    <form onSubmit={submit} noValidate className="editor">
      <BackLink />
      <div className="page-heading">
        <h1>{title}</h1>
      </div>

      {save.error && (
        <ErrorMessage
          error={save.error}
          title="Couldn't save the Category"
          // Each field's own message is beside it; repeating it here would say it twice.
          message={Object.keys(errors).length > 0 ? 'Fix the fields marked below, then save again.' : undefined}
        />
      )}

      <section className="panel" aria-labelledby={`${id}-details`}>
        <h2 id={`${id}-details`}>Details</h2>
        <div className="form-grid">
          <label className="field">
            Name
            <Input
              value={form.name}
              onChange={(e) => {
                setForm({ ...form, name: e.target.value })
                clearError('name')
              }}
              autoFocus={!category}
              {...field('name')}
            />
            <FieldError id={errorId('name')} message={errors.name} />
          </label>
          {category ? (
            <div className="field">
              Slug
              <code className="readonly">{category.slug}</code>
              <span className="hint">A slug never changes; Products name their Category by it.</span>
            </div>
          ) : (
            <label className="field">
              Slug
              <Input
                value={form.slug}
                onChange={(e) => {
                  setForm({ ...form, slug: e.target.value })
                  clearError('slug')
                }}
                spellCheck={false}
                autoCapitalize="none"
                {...field('slug')}
              />
              <span className="hint">
                Lowercase letters, digits and hyphens, such as smart-watches. It can't change later.
              </span>
              <FieldError id={errorId('slug')} message={errors.slug} />
            </label>
          )}
        </div>
        {category && category.productCount > 0 && (
          <p className="hint">
            {category.productCount === 1 ? '1 Product uses' : `${category.productCount} Products use`} this Category.
            Changed definitions apply to each the next time it is saved; nothing is rewritten now.
          </p>
        )}
      </section>

      <section className="panel" aria-labelledby={`${id}-attributes`}>
        <div className="panel-heading">
          <h2 id={`${id}-attributes`}>Attribute definitions</h2>
          <Button size="sm" onClick={() => setAttributes((attributes) => [...attributes, blankDefinition()])}>
            <Icon name="plus" size={16} />
            Add attribute
          </Button>
        </div>
        <div className="table-scroll">
          <table className="table definitions">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Type</th>
                <th scope="col">Values</th>
                <th scope="col" className="center">
                  Required
                </th>
                <th scope="col" className="center">
                  Variant axis
                </th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {form.attributes.length === 0 && (
                <tr>
                  <td colSpan={6} className="table-empty">
                    No attributes yet. Products in this Category will carry only a name, images and Variants.
                  </td>
                </tr>
              )}
              {form.attributes.map((definition, index) => {
                const at = (key: string) => `attributes[${index}].${key}`
                const label = definition.name.trim() || `attribute ${index + 1}`
                return (
                  <tr key={index}>
                    <td>
                      <Input
                        aria-label={`Attribute ${index + 1} name`}
                        value={definition.name}
                        onChange={(e) => setDefinition(index, 'name', e.target.value)}
                        spellCheck={false}
                        {...field(at('name'))}
                      />
                      <FieldError id={errorId(at('name'))} message={errors[at('name')]} />
                    </td>
                    <td>
                      <select
                        className="input"
                        aria-label={`${capitalise(label)} type`}
                        value={definition.type}
                        onChange={(e) => setDefinition(index, 'type', e.target.value as AttributeType)}
                        {...field(at('type'))}
                      >
                        {attributeTypes.map((type) => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                      <FieldError id={errorId(at('type'))} message={errors[at('type')]} />
                    </td>
                    <td>
                      <Input
                        aria-label={`${capitalise(label)} values, separated by commas`}
                        placeholder={definition.type === 'ENUM' ? 'e.g. 128 GB, 256 GB' : 'Only for ENUM'}
                        value={definition.type === 'ENUM' ? definition.values : ''}
                        disabled={definition.type !== 'ENUM'}
                        onChange={(e) => setDefinition(index, 'values', e.target.value)}
                        {...field(at('values'))}
                      />
                      <FieldError id={errorId(at('values'))} message={errors[at('values')]} />
                    </td>
                    <td className="center">
                      <input
                        type="checkbox"
                        aria-label={`${capitalise(label)} is required`}
                        checked={definition.required}
                        onChange={(e) => setDefinition(index, 'required', e.target.checked)}
                      />
                    </td>
                    <td className="center">
                      <input
                        type="checkbox"
                        aria-label={`${capitalise(label)} is a Variant axis`}
                        checked={definition.variantAxis}
                        onChange={(e) => setDefinition(index, 'variantAxis', e.target.checked)}
                      />
                    </td>
                    <td className="actions">
                      <Button
                        variant="ghost"
                        size="sm"
                        icon
                        aria-label={`Move ${label} up`}
                        disabled={index === 0}
                        onClick={() => move(index, -1)}
                      >
                        <Icon name="arrowUp" size={16} />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        icon
                        aria-label={`Move ${label} down`}
                        disabled={index === form.attributes.length - 1}
                        onClick={() => move(index, 1)}
                      >
                        <Icon name="arrowDown" size={16} />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        icon
                        className="danger-text"
                        aria-label={`Remove ${label}`}
                        onClick={() => setAttributes((attributes) => attributes.filter((_, i) => i !== index))}
                      >
                        <Icon name="trash" size={16} />
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <p className="hint">
          A Variant axis, such as colour or storage, tells a Product's Variants apart: each Variant needs a value for
          every axis. The others describe the Product itself. The order here is the order Products show them in.
        </p>
      </section>

      <div className="form-actions">
        <Button type="submit" variant="primary" loading={save.isPending}>
          {category ? 'Save changes' : 'Create Category'}
        </Button>
        <ButtonLink to="/categories" variant="ghost">
          Cancel
        </ButtonLink>
      </div>
    </form>
  )
}

function capitalise(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
