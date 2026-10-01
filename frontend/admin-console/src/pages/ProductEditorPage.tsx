import { type FormEvent, type ReactNode, useEffect, useId, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { useCategories } from '../api/categories'
import { failureOf, isNotFound } from '../api/failure'
import { type FieldErrors, fieldErrorsOf } from '../api/fieldErrors'
import { useProduct, useSaveProduct, useStocks } from '../api/products'
import { NotFound } from '../components/NotFound'
import { ErrorMessage, FieldError } from '../components/Status'
import { Button, ButtonLink } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { Input } from '../components/ui/Input'
import type { AttributeDefinition, Category } from '../domain/category'
import {
  type OnHandByVariant,
  type Product,
  type ProductForm,
  type Stock,
  type VariantForm,
  attributesOf,
  axesOf,
  blankVariant,
  formOf,
  productRequest,
  stockChanges,
} from '../domain/product'
import type { SavedState } from './CategoryListPage'

/**
 * `/products/new` creates a Product, in the Category `?category=` names if any; `/products/:sku`
 * edits one. The editor opens once the Product, the Categories and each Variant's Stock are in.
 */
export function ProductEditorPage() {
  const { sku } = useParams()
  const [params] = useSearchParams()
  const product = useProduct(sku)
  const categories = useCategories()
  const stocks = useStocks(product.data?.variants.map((variant) => variant.id) ?? [])
  const stockError = stocks.find((stock) => stock.error)

  if (isNotFound(product.error)) return <NotFound what="Product" />
  const failed = product.error
    ? { error: product.error, title: "Couldn't load the Product", query: product }
    : categories.error
      ? { error: categories.error, title: "Couldn't load the Categories", query: categories }
      : stockError
        ? { error: stockError.error, title: "Couldn't load the Stock", query: stockError }
        : undefined
  if (failed) {
    return (
      <>
        <BackLink />
        <ErrorMessage
          error={failed.error}
          title={failed.title}
          retrying={failed.query.isFetching}
          onRetry={() => failed.query.refetch()}
        />
      </>
    )
  }
  const loading =
    !categories.data || (sku !== undefined && (!product.data || stocks.some((stock) => stock.data === undefined)))
  if (loading) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  const onHand: OnHandByVariant = Object.fromEntries(
    product.data?.variants.map((variant, i) => [variant.id, stocks[i]?.data?.onHand]) ?? [],
  )
  return (
    <ProductEditor
      key={sku ?? 'new'}
      product={product.data}
      categories={categories.data!}
      initialCategory={params.get('category') ?? undefined}
      onHand={onHand}
    />
  )
}

function BackLink() {
  return (
    <Link to="/products" className="back-link">
      <Icon name="arrowLeft" size={16} />
      Products
    </Link>
  )
}

/**
 * What of the form wasn't saved, besides Catalog refusing it: nothing, since fields the editor
 * checks itself are wrong, or the On-hand counts Inventory refused after Catalog took the Product.
 */
type Unsaved = 'invalid' | 'stock'

/**
 * What creating a Product carries to its own page when Inventory refused some of its On-hand
 * counts: each count as typed, and Inventory's reason.
 */
interface RefusedStockState {
  refusedStock: { variantId: string; onHand: string; message: string }[]
}

/**
 * The Product form: its Category, which decides its attribute fields and Variant axes, its details
 * and images, and a table of Variants with their Prices and Stock. Saving sends the Product to
 * Catalog, then each changed on-hand count to Inventory. Each field either refuses shows its
 * message beside that field until it is edited.
 */
function ProductEditor({
  product,
  categories,
  initialCategory,
  onHand,
}: {
  product?: Product
  categories: Category[]
  initialCategory?: string
  onHand: OnHandByVariant
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const save = useSaveProduct()
  // Read once, then dropped from history, so a reload shows the Product as Inventory has it.
  const [refused] = useState(() => (location.state as RefusedStockState | null)?.refusedStock ?? [])
  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, navigate])
  const [form, setForm] = useState<ProductForm>(() => {
    const loaded = formOf(product, categories.find((c) => c.slug === (product?.category ?? initialCategory)), onHand)
    const typed = new Map(refused.map((r) => [r.variantId, r.onHand]))
    return { ...loaded, variants: loaded.variants.map((v) => ({ ...v, onHand: typed.get(v.id) ?? v.onHand })) }
  })
  // Catalog has the Product once there is one here.
  const savedSku = product?.sku
  // Each Variant's on-hand count as Inventory last said.
  const [loadedOnHand, setLoadedOnHand] = useState(onHand)
  const [errors, setErrors] = useState<FieldErrors>(() =>
    Object.fromEntries(
      refused.flatMap(({ variantId, message }) => {
        const index = form.variants.findIndex((v) => v.id === variantId)
        return index < 0 ? [] : [[`variants[${index}].onHand`, message]]
      }),
    ),
  )
  const [unsaved, setUnsaved] = useState<Unsaved | undefined>(refused.length > 0 ? 'stock' : undefined)
  const stocks = useStocks(form.variants.filter((variant) => variant.saved).map((variant) => variant.id))
  const stockOf = (variantId: string) =>
    stocks.find((stock) => stock.data?.variantId === variantId)?.data ?? undefined
  const category = categories.find((c) => c.slug === form.category)
  const axes = axesOf(category)
  const id = useId()
  const errorId = (field: string) => `${id}-${field.replace(/[^\w-]/g, '-')}-error`

  const field = (name: string) => ({
    'aria-invalid': errors[name] ? true : undefined,
    'aria-describedby': errors[name] ? errorId(name) : undefined,
  })
  const clearErrors = (...names: string[]) =>
    setErrors((current) => {
      if (!names.some((name) => name in current)) return current
      return Object.fromEntries(Object.entries(current).filter(([name]) => !names.includes(name)))
    })
  // Rows renumber when one is added or removed, so their errors no longer name the right row.
  const clearVariantErrors = () =>
    setErrors((current) => Object.fromEntries(Object.entries(current).filter(([name]) => !name.startsWith('variants'))))

  const chooseCategory = (slug: string) => {
    const chosen = categories.find((c) => c.slug === slug)
    // A Category brings its own attributes and axes; values typed for another one don't carry over.
    setForm((current) => ({
      ...current,
      category: slug,
      attributes: {},
      variants: current.variants.map((variant) => ({ ...variant, axisValues: blankVariant(chosen).axisValues })),
    }))
    setErrors((current) =>
      Object.fromEntries(
        Object.entries(current).filter(
          ([name]) => name !== 'category' && !name.startsWith('attributes') && !name.includes('axisValues'),
        ),
      ),
    )
  }
  const setAttribute = (name: string, value: string) => {
    setForm((current) => ({ ...current, attributes: { ...current.attributes, [name]: value } }))
    clearErrors(`attributes.${name}`, 'attributes')
  }
  const setVariant = <K extends keyof VariantForm>(index: number, key: K, value: VariantForm[K]) => {
    setForm((current) => ({
      ...current,
      variants: current.variants.map((variant, i) => (i === index ? { ...variant, [key]: value } : variant)),
    }))
    clearErrors(`variants[${index}].${key}`)
  }
  const setAxisValue = (index: number, axis: string, value: string) => {
    setForm((current) => ({
      ...current,
      variants: current.variants.map((variant, i) =>
        i === index ? { ...variant, axisValues: { ...variant.axisValues, [axis]: value } } : variant,
      ),
    }))
    clearErrors(`variants[${index}].axisValues.${axis}`, `variants[${index}].axisValues`)
  }
  const setVariants = (change: (variants: VariantForm[]) => VariantForm[]) => {
    setForm((current) => ({ ...current, variants: change(current.variants) }))
    clearVariantErrors()
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    save.reset()
    const { request, errors: invalid } = productRequest(form, category)
    if (!request) {
      setErrors(invalid)
      setUnsaved('invalid')
      return
    }
    setUnsaved(undefined)
    const changes = stockChanges(form, loadedOnHand)
    save.mutate(
      { sku: savedSku, request, changes },
      {
        onSuccess: ({ product: saved, stockFailures }) => {
          if (stockFailures.length === 0) {
            navigate('/products', { state: { saved: saved.name } satisfies SavedState })
            return
          }
          if (savedSku === undefined) {
            // A new Product has its own page now, which a reload or Back must find; it shows the refusals.
            navigate(`/products/${encodeURIComponent(saved.sku)}`, {
              replace: true,
              state: {
                refusedStock: stockFailures.map(({ change, error }) => ({
                  variantId: change.variantId,
                  onHand: form.variants[change.index]!.onHand,
                  message: failureOf(error).message,
                })),
              } satisfies RefusedStockState,
            })
            return
          }
          // Its SKU and Variant IDs are fixed, and saving again retries the counts still to set.
          const failed = new Set(stockFailures.map(({ change }) => change.index))
          setForm((current) => ({ ...current, variants: current.variants.map((v) => ({ ...v, saved: true })) }))
          setLoadedOnHand((current) => ({
            ...current,
            ...Object.fromEntries(changes.filter((c) => !failed.has(c.index)).map((c) => [c.variantId, c.onHand])),
          }))
          setErrors(
            Object.fromEntries(
              stockFailures.map(({ change, error }) => [`variants[${change.index}].onHand`, failureOf(error).message]),
            ),
          )
          setUnsaved('stock')
        },
        onError: (error) => setErrors(fieldErrorsOf(error)),
      },
    )
  }

  const title = product ? product.name : 'New Product'
  const hasFieldErrors = Object.keys(errors).length > 0
  return (
    <form onSubmit={submit} noValidate className="editor">
      <BackLink />
      <div className="page-heading">
        <h1>{title}</h1>
      </div>

      {save.error && (
        <ErrorMessage
          error={save.error}
          title="Couldn't save the Product"
          // Each field's own message is beside it; repeating it here would say it twice.
          message={hasFieldErrors ? 'Fix the fields marked below, then save again.' : undefined}
        />
      )}
      {unsaved === 'invalid' && (
        <FormAlert title="Couldn't save the Product" message="Fix the fields marked below, then save again." />
      )}
      {unsaved === 'stock' && (
        <FormAlert
          title={`Saved ${form.name.trim()}, but not all of its Stock`}
          message="Inventory refused the On-hand counts marked below. Fix them, then save again."
        />
      )}

      <section className="panel" aria-labelledby={`${id}-details`}>
        <h2 id={`${id}-details`}>Details</h2>
        <div className="form-grid">
          {savedSku === undefined ? (
            <label className="field">
              Category
              <select
                className="input"
                value={form.category}
                onChange={(e) => chooseCategory(e.target.value)}
                autoFocus
                {...field('category')}
              >
                <option value="" disabled>
                  Choose a Category
                </option>
                {categories.map((c) => (
                  <option key={c.slug} value={c.slug}>
                    {c.name}
                  </option>
                ))}
              </select>
              <span className="hint">It decides the attributes and Variant axes below.</span>
              <FieldError id={errorId('category')} message={errors.category} />
            </label>
          ) : (
            <div className="field">
              Category
              <span className="readonly">{category?.name ?? form.category}</span>
            </div>
          )}
          {savedSku === undefined ? (
            <label className="field">
              SKU
              <Input
                value={form.sku}
                onChange={(e) => {
                  setForm({ ...form, sku: e.target.value })
                  clearErrors('sku')
                }}
                spellCheck={false}
                autoCapitalize="characters"
                {...field('sku')}
              />
              <span className="hint">Such as PHN-PIXEL-9. It can't change later.</span>
              <FieldError id={errorId('sku')} message={errors.sku} />
            </label>
          ) : (
            <div className="field">
              SKU
              <code className="readonly">{savedSku}</code>
              <span className="hint">A SKU never changes.</span>
            </div>
          )}
          <label className="field">
            Name
            <Input
              value={form.name}
              onChange={(e) => {
                setForm({ ...form, name: e.target.value })
                clearErrors('name')
              }}
              {...field('name')}
            />
            <FieldError id={errorId('name')} message={errors.name} />
          </label>
        </div>
      </section>

      {category && (
        <>
          <section className="panel" aria-labelledby={`${id}-attributes`}>
            <h2 id={`${id}-attributes`}>Attributes</h2>
            {attributesOf(category).length === 0 ? (
              <p className="hint">{category.name} defines no attributes beyond its Variant axes.</p>
            ) : (
              <div className="form-grid">
                {attributesOf(category).map((definition) => {
                  const name = `attributes.${definition.name}`
                  return (
                    <label className="field" key={definition.name}>
                      <span>
                        {definition.name}
                        {!definition.required && <span className="hint"> (optional)</span>}
                      </span>
                      <ValueInput
                        definition={definition}
                        value={form.attributes[definition.name] ?? ''}
                        onChange={(value) => setAttribute(definition.name, value)}
                        {...field(name)}
                      />
                      <FieldError id={errorId(name)} message={errors[name]} />
                    </label>
                  )
                })}
              </div>
            )}
            <FieldError id={errorId('attributes')} message={errors.attributes} />
          </section>

          <section className="panel" aria-labelledby={`${id}-images`}>
            <h2 id={`${id}-images`}>Images</h2>
            <label className="field">
              Product images
              <textarea
                className="input"
                rows={3}
                value={form.images}
                onChange={(e) => {
                  setForm({ ...form, images: e.target.value })
                  clearErrors('images')
                }}
                spellCheck={false}
                placeholder="e.g. /images/products/phn-pixel-9/front.svg"
                {...field('images')}
              />
              <span className="hint">One path or URL per line, the first shown first. A Variant's own images replace these.</span>
              <FieldError id={errorId('images')} message={errors.images} />
            </label>
          </section>

          <section className="panel" aria-labelledby={`${id}-variants`}>
            <div className="panel-heading">
              <h2 id={`${id}-variants`}>Variants</h2>
              <Button size="sm" onClick={() => setVariants((variants) => [...variants, blankVariant(category)])}>
                <Icon name="plus" size={16} />
                Add Variant
              </Button>
            </div>
            <div className="form-grid currency">
              <label className="field">
                Currency
                <Input
                  value={form.currency}
                  onChange={(e) => {
                    setForm({ ...form, currency: e.target.value })
                    clearErrors('currency')
                  }}
                  maxLength={3}
                  spellCheck={false}
                  autoCapitalize="characters"
                  {...field('currency')}
                />
                <span className="hint">Every Variant of a Product is priced in one currency.</span>
                <FieldError id={errorId('currency')} message={errors.currency} />
              </label>
            </div>
            <div className="table-scroll">
              <table className="table variants">
                <thead>
                  <tr>
                    <th scope="col">Variant ID</th>
                    {axes.map((axis) => (
                      <th scope="col" key={axis.name}>
                        {axis.name}
                      </th>
                    ))}
                    <th scope="col">Price</th>
                    <th scope="col">Images</th>
                    <th scope="col">On hand</th>
                    <th scope="col" className="num">
                      Reserved
                    </th>
                    <th scope="col" className="num">
                      Available
                    </th>
                    <th scope="col">
                      <span className="visually-hidden">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {form.variants.map((variant, index) => {
                    const at = (key: string) => `variants[${index}].${key}`
                    // By position, not ID, so a control's name doesn't change while its ID is typed.
                    const label = `Variant ${index + 1}`
                    const stock: Stock | undefined = variant.saved ? stockOf(variant.id) : undefined
                    // A repeated combination is named on the row's axis values as a whole.
                    const combination = (
                      <FieldError id={errorId(at('axisValues'))} message={errors[at('axisValues')]} />
                    )
                    return (
                      <tr key={index}>
                        <td>
                          {variant.saved ? (
                            <code className="readonly">{variant.id}</code>
                          ) : (
                            <Input
                              aria-label={`${label} ID`}
                              value={variant.id}
                              onChange={(e) => setVariant(index, 'id', e.target.value)}
                              spellCheck={false}
                              placeholder={index === 0 && savedSku === undefined ? 'Usually the SKU' : undefined}
                              {...field(at('id'))}
                            />
                          )}
                          <FieldError id={errorId(at('id'))} message={errors[at('id')]} />
                          {axes.length === 0 && combination}
                        </td>
                        {axes.map((axis, a) => {
                          const name = at(`axisValues.${axis.name}`)
                          return (
                            <td key={axis.name}>
                              <ValueInput
                                definition={axis}
                                aria-label={`${label} ${axis.name}`}
                                value={variant.axisValues[axis.name] ?? ''}
                                onChange={(value) => setAxisValue(index, axis.name, value)}
                                {...field(name)}
                                aria-invalid={errors[name] || errors[at('axisValues')] ? true : undefined}
                              />
                              <FieldError id={errorId(name)} message={errors[name]} />
                              {a === 0 && combination}
                            </td>
                          )
                        })}
                        <td>
                          <Input
                            aria-label={`${label} price in ${form.currency.trim().toUpperCase() || 'the currency'}`}
                            inputMode="decimal"
                            placeholder="e.g. 799.00"
                            value={variant.price}
                            onChange={(e) => setVariant(index, 'price', e.target.value)}
                            {...field(at('price'))}
                          />
                          <FieldError id={errorId(at('price'))} message={errors[at('price')]} />
                        </td>
                        <td>
                          <Input
                            aria-label={`${label} images, separated by commas`}
                            placeholder="Product's"
                            value={variant.images}
                            onChange={(e) => setVariant(index, 'images', e.target.value)}
                            spellCheck={false}
                            {...field(at('images'))}
                          />
                          <FieldError id={errorId(at('images'))} message={errors[at('images')]} />
                        </td>
                        <td>
                          <Input
                            aria-label={`${label} on hand`}
                            inputMode="numeric"
                            placeholder="Not stocked"
                            value={variant.onHand}
                            onChange={(e) => setVariant(index, 'onHand', e.target.value)}
                            {...field(at('onHand'))}
                          />
                          <FieldError id={errorId(at('onHand'))} message={errors[at('onHand')]} />
                        </td>
                        <td className="num">{stock ? stock.reserved : <Dash />}</td>
                        <td className="num">{stock ? stock.quantity : <Dash />}</td>
                        <td className="actions">
                          <Button
                            variant="ghost"
                            size="sm"
                            icon
                            className="danger-text"
                            aria-label={`Remove ${label}`}
                            disabled={form.variants.length === 1}
                            onClick={() => setVariants((variants) => variants.filter((_, i) => i !== index))}
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
              Each Variant needs a value for every Variant axis, and no two alike. A saved Variant's ID never changes,
              and Catalog keeps every saved Variant until its Product is deleted, since Carts, Orders and Stock name it.
              On hand is what Inventory physically holds; Reservations hold some of it for checkouts, and the rest is
              available to sell.
            </p>
          </section>
        </>
      )}

      <div className="form-actions">
        <Button type="submit" variant="primary" loading={save.isPending} disabled={!category}>
          {savedSku ? 'Save changes' : 'Create Product'}
        </Button>
        <ButtonLink to="/products" variant="ghost">
          Cancel
        </ButtonLink>
      </div>
    </form>
  )
}

/** A failure of the form as a whole that no request's error describes. */
function FormAlert({ title, message }: { title: string; message: string }) {
  return (
    <div className="alert alert-danger" role="alert">
      <Icon name="alert" />
      <div className="alert-body">
        <strong>{title}</strong>
        <span>{message}</span>
      </div>
    </div>
  )
}

/** No Stock yet: the Variant is new, or Inventory doesn't stock it. */
function Dash(): ReactNode {
  return (
    <>
      <span className="muted" aria-hidden="true">
        —
      </span>
      <span className="visually-hidden">None</span>
    </>
  )
}

/**
 * A value for an attribute or a Variant axis, as its definition's type takes it: a choice of an
 * `ENUM`'s values or of yes and no for a `BOOLEAN`, a decimal for a `NUMBER`, text otherwise.
 */
function ValueInput({
  definition,
  value,
  onChange,
  ...props
}: {
  definition: AttributeDefinition
  value: string
  onChange: (value: string) => void
  'aria-label'?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}) {
  if (definition.type === 'ENUM' || definition.type === 'BOOLEAN') {
    const options =
      definition.type === 'ENUM'
        ? definition.values.map((v) => ({ value: v, label: v }))
        : [
            { value: 'true', label: 'Yes' },
            { value: 'false', label: 'No' },
          ]
    // A value the definition no longer allows stays visible, for Catalog to refuse.
    if (value !== '' && !options.some((option) => option.value === value)) options.unshift({ value, label: value })
    return (
      <select className="input" value={value} onChange={(e) => onChange(e.target.value)} {...props}>
        <option value="">—</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    )
  }
  return (
    <Input
      value={value}
      inputMode={definition.type === 'NUMBER' ? 'decimal' : undefined}
      onChange={(e) => onChange(e.target.value)}
      {...props}
    />
  )
}
