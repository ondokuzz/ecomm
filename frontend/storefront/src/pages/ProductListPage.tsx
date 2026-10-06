import { type FormEvent, useEffect, useId, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useCurrencies, useFormatMoney } from '../api/currencies'
import { searchPageSize, useSearch } from '../api/search'
import { ProductImage } from '../components/ProductImage'
import { EmptyState, ErrorState } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { Input } from '../components/ui/Input'
import { Skeleton } from '../components/ui/Skeleton'
import { productCountLabel } from '../domain/catalog'
import { type Currencies, decimalOf, moneyOf } from '../domain/money'
import { pageCount } from '../domain/paging'
import {
  type AttributeGroup,
  type FacetOption,
  type Facets,
  type ProductSummary,
  type Range,
  type Search,
  type SearchResults,
  type SortKey,
  attributeGroups,
  categoryOptions,
  hasFilters,
  searchFromUrl,
  urlOf,
  withInStock,
  withPage,
  withPrice,
  withRange,
  withSort,
  withText,
  withoutFilters,
} from '../domain/search'

/** The listing's address for a search. */
function hrefOf(search: Search): string {
  const query = urlOf(search).toString()
  return query ? `/?${query}` : '/'
}

/**
 * The Product listing: Search's results for what the URL holds, which a search box, Category chips,
 * a filter panel built from the facets, an in-stock toggle, a sort and paging all change. Every one
 * of them is in the URL, so a reload or a shared link shows the same view.
 */
export function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const search = searchFromUrl(params)
  const results = useSearch(search)
  const go = (next: Search) => setParams(urlOf(next))
  const data = results.data

  return (
    <div className="catalog">
      <ListingHeader search={search} results={data} />
      {/* Keyed by the text, so a URL that changes it, such as on Back, refills the box. */}
      <SearchBox key={search.text} text={search.text} onSearch={(text) => go(withText(search, text))} />
      <CategoryChips facets={data?.facets} search={search} />
      {results.error && !data ? (
        <ErrorState
          title="We couldn't load the products"
          error={results.error}
          retrying={results.isFetching}
          onRetry={() => results.refetch()}
        />
      ) : (
        <div className="listing">
          <FilterPanel facets={data?.facets} search={search} go={go} />
          <section className="listing-results" aria-label="Results">
            <Toolbar results={data} search={search} go={go} />
            <Results results={data} search={search} busy={results.isFetching} />
            {data && <Pager results={data} search={search} />}
          </section>
        </div>
      )}
    </div>
  )
}

function ListingHeader({ search, results }: { search: Search; results: SearchResults | undefined }) {
  if (!search.text && !search.category) {
    return (
      <header className="hero">
        <p className="hero-eyebrow">New season, new gear</p>
        <h1>Find your next favourite gadget</h1>
        <p className="hero-tagline">Phones, laptops and audio from the brands you love, all in one place.</p>
      </header>
    )
  }
  const category = results?.facets.categories.find((c) => c.slug === search.category)
  return (
    <header className="category-header">
      <h1 className={search.category ? 'capitalize' : undefined}>
        {search.category ? (category?.name ?? search.category) : `Results for “${search.text}”`}
      </h1>
      {results && (
        <p>
          {productCountLabel(results.total)}
          {search.category && search.text && ` for “${search.text}”`}
        </p>
      )}
    </header>
  )
}

/** Searches the name, the description and the attribute values; an empty search clears the text. */
function SearchBox({ text, onSearch }: { text: string | undefined; onSearch: (text: string) => void }) {
  const [value, setValue] = useState(text ?? '')
  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSearch(value)
  }
  return (
    <form role="search" className="search-box" onSubmit={submit}>
      <Icon name="search" size={18} />
      <Input
        type="search"
        aria-label="Search products"
        placeholder="Search products"
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
      <Button type="submit" variant="primary">
        Search
      </Button>
    </form>
  )
}

/** "All" and each Category with how many Products it would leave; one with none can't be chosen. */
function CategoryChips({ facets, search }: { facets: Facets | undefined; search: Search }) {
  const row = useRef<HTMLUListElement>(null)
  const loaded = facets !== undefined

  // A Category chosen straight from the URL may sit off-screen in the chip row on a phone. This
  // scrolls the row alone, never the page.
  useEffect(() => {
    const selected = row.current?.querySelector<HTMLElement>('[aria-current]')
    if (row.current && selected) {
      row.current.scrollLeft = selected.offsetLeft - (row.current.clientWidth - selected.offsetWidth) / 2
    }
  }, [search.category, loaded])

  return (
    <nav className="chips-nav" aria-label="Categories">
      <ul className="chips" ref={row}>
        {facets
          ? categoryOptions(facets, search).map((option) => (
              <li key={option.label}>
                <Chip option={option} />
              </li>
            ))
          : Array.from({ length: 4 }, (_, i) => (
              <li key={i}>
                <Skeleton width="6.5rem" height="2.5rem" radius="var(--radius-pill)" />
              </li>
            ))}
      </ul>
    </nav>
  )
}

function Chip({ option }: { option: FacetOption }) {
  const content = (
    <>
      <span className="capitalize">{option.label}</span>
      <span className="chip-count">{option.count}</span>
    </>
  )
  if (option.disabled) {
    return (
      <span className="chip" aria-disabled="true">
        {content}
      </span>
    )
  }
  return (
    <Link to={hrefOf(option.search)} className="chip" aria-current={option.selected || undefined}>
      {content}
    </Link>
  )
}

/**
 * The chosen Category's attributes and the price range, built from the facets. On a phone it folds
 * away behind a "Filters" button.
 */
function FilterPanel({ facets, search, go }: { facets: Facets | undefined; search: Search; go: (s: Search) => void }) {
  const [open, setOpen] = useState(false)
  const panel = useId()
  const currencies = useCurrencies().data
  if (!facets) return <aside className="filters" aria-busy="true" />
  const groups = attributeGroups(facets, search)
  return (
    <aside className="filters" aria-label="Filters">
      <Button className="filters-toggle" aria-expanded={open} aria-controls={panel} onClick={() => setOpen(!open)}>
        Filters
      </Button>
      <div id={panel} className="filters-body" data-open={open || undefined}>
        {groups.map((group) =>
          group.kind === 'values' ? (
            <ValueGroup key={group.name} group={group} go={go} />
          ) : (
            <RangeGroup key={`${group.name} ${urlOf(search)}`} group={group} search={search} go={go} />
          ),
        )}
        {currencies && (
          <PriceGroup key={urlOf(search).toString()} facets={facets} currencies={currencies} search={search} go={go} />
        )}
        {hasFilters(search) && (
          <Link to={hrefOf(withoutFilters(search))} className="filters-clear">
            Clear filters
          </Link>
        )}
      </div>
    </aside>
  )
}

/** An attribute's values as checkboxes with their counts; ticking one searches at once. */
function ValueGroup({ group, go }: { group: Extract<AttributeGroup, { kind: 'values' }>; go: (s: Search) => void }) {
  return (
    <fieldset className="filter-group">
      <legend className="capitalize">{group.name}</legend>
      {group.options.map((option) => (
        <label key={option.value} className="filter-option" aria-disabled={option.disabled || undefined}>
          <input
            type="checkbox"
            checked={option.selected}
            disabled={option.disabled}
            onChange={() => go(option.search)}
          />
          <span>{option.value}</span>
          <span className="filter-count">{option.count}</span>
        </label>
      ))}
    </fieldset>
  )
}

/** A NUMBER attribute's range, between its lowest and highest value. */
function RangeGroup({
  group,
  search,
  go,
}: {
  group: Extract<AttributeGroup, { kind: 'range' }>
  search: Search
  go: (s: Search) => void
}) {
  const [range, setRange] = useState<Range>(group.chosen)
  const invalid = (text: string | undefined) => text !== undefined && !/^-?\d+(\.\d+)?$/.test(text.trim())
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (invalid(range.min) || invalid(range.max)) return
    go(withRange(search, group.name, { min: range.min?.trim(), max: range.max?.trim() }))
  }
  return (
    <form className="filter-group" onSubmit={submit}>
      <fieldset>
        <legend className="capitalize">{group.name}</legend>
        <RangeFields
          name={group.name}
          range={range}
          placeholders={{ min: String(group.min), max: String(group.max) }}
          invalid={{ min: invalid(range.min), max: invalid(range.max) }}
          onChange={setRange}
        />
      </fieldset>
    </form>
  )
}

/**
 * The price range, typed as decimals in a Currency and sent in its Minor unit. Its bounds are the
 * Variant Prices in that Currency.
 */
function PriceGroup({
  facets,
  currencies,
  search,
  go,
}: {
  facets: Facets
  currencies: Currencies
  search: Search
  go: (s: Search) => void
}) {
  const [currency, setCurrency] = useState(search.currency ?? facets.prices[0]?.currency)
  const bounds = facets.prices.find((p) => p.currency === currency)
  const decimal = (amountMinor: string | number | undefined) =>
    amountMinor === undefined || !currency || !currencies.has(currency)
      ? ''
      : decimalOf({ amountMinor: Number(amountMinor), currency }, currencies)
  const [range, setRange] = useState<Range>({
    min: decimal(search.price.min) || undefined,
    max: decimal(search.price.max) || undefined,
  })
  if (facets.prices.length === 0 || !currency) return null

  const amount = (text: string | undefined) => (text ? moneyOf(text, currency, currencies)?.amountMinor : undefined)
  const invalid = (text: string | undefined) => text !== undefined && text !== '' && amount(text) === undefined
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (invalid(range.min) || invalid(range.max)) return
    const min = amount(range.min)
    const max = amount(range.max)
    const chosen = { ...(min !== undefined && { min: String(min) }), ...(max !== undefined && { max: String(max) }) }
    go(withPrice(search, min === undefined && max === undefined ? undefined : currency, chosen))
  }
  return (
    <form className="filter-group" onSubmit={submit}>
      <fieldset>
        <legend>Price</legend>
        {facets.prices.length > 1 && (
          <select
            className="input"
            aria-label="Currency"
            value={currency}
            onChange={(event) => setCurrency(event.target.value)}
          >
            {facets.prices.map((p) => (
              <option key={p.currency}>{p.currency}</option>
            ))}
          </select>
        )}
        <RangeFields
          name={`price in ${currency}`}
          range={range}
          placeholders={{ min: decimal(bounds?.min), max: decimal(bounds?.max) }}
          invalid={{ min: invalid(range.min), max: invalid(range.max) }}
          onChange={setRange}
        />
      </fieldset>
    </form>
  )
}

function RangeFields({
  name,
  range,
  placeholders,
  invalid = {},
  onChange,
}: {
  name: string
  range: Range
  placeholders: { min: string; max: string }
  invalid?: { min?: boolean; max?: boolean }
  onChange: (range: Range) => void
}) {
  const set = (end: 'min' | 'max', value: string) => onChange({ ...range, [end]: value === '' ? undefined : value })
  return (
    <div className="filter-range">
      <Input
        inputMode="decimal"
        aria-label={`Lowest ${name}`}
        placeholder={placeholders.min}
        value={range.min ?? ''}
        aria-invalid={invalid.min || undefined}
        onChange={(event) => set('min', event.target.value)}
      />
      <span aria-hidden="true">–</span>
      <Input
        inputMode="decimal"
        aria-label={`Highest ${name}`}
        placeholder={placeholders.max}
        value={range.max ?? ''}
        aria-invalid={invalid.max || undefined}
        onChange={(event) => set('max', event.target.value)}
      />
      <Button type="submit" size="sm">
        Apply
      </Button>
    </div>
  )
}

const sortLabels: Record<SortKey, string> = {
  relevance: 'Best match',
  newest: 'Newest',
  'price-asc': 'Price: low to high',
  'price-desc': 'Price: high to low',
}

/** How many Products matched, the in-stock toggle with its count, and the sort. */
function Toolbar({ results, search, go }: { results: SearchResults | undefined; search: Search; go: (s: Search) => void }) {
  // Without text there is nothing to match, so relevance is the same as newest.
  const sorts: SortKey[] = search.text ? ['relevance', 'newest', 'price-asc', 'price-desc'] : ['newest', 'price-asc', 'price-desc']
  const sort = search.sort && sorts.includes(search.sort) ? search.sort : sorts[0]
  return (
    <div className="listing-toolbar">
      <p className="muted" aria-live="polite">
        {results && productCountLabel(results.total)}
      </p>
      <label className="filter-option stock-toggle">
        <input type="checkbox" checked={search.inStock} onChange={(event) => go(withInStock(search, event.target.checked))} />
        <span>In stock only</span>
        {results && <span className="filter-count">{results.facets.inStock}</span>}
      </label>
      <label className="sort">
        <span>Sort by</span>
        <select className="input" value={sort} onChange={(event) => go(withSort(search, event.target.value as SortKey))}>
          {sorts.map((key) => (
            <option key={key} value={key}>
              {sortLabels[key]}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}

function Results({ results, search, busy }: { results: SearchResults | undefined; search: Search; busy: boolean }) {
  if (results && results.items.length === 0) {
    if (results.total > 0) {
      return (
        <EmptyState title="There's nothing on this page" action={<ButtonLink to={hrefOf(withPage(search, 0))}>Go to the first page</ButtonLink>}>
          The results have fewer pages than this.
        </EmptyState>
      )
    }
    const filtered = hasFilters(search)
    return (
      <EmptyState
        title={filtered || search.text ? 'No products match' : 'Nothing here yet'}
        illustration={<EmptyShelf />}
        action={
          filtered ? (
            <ButtonLink to={hrefOf(withoutFilters(search))}>Clear filters</ButtonLink>
          ) : (
            search.text && <ButtonLink to="/">See all products</ButtonLink>
          )
        }
      >
        {filtered
          ? 'Nothing matches all of these filters. Clear them to see more.'
          : search.text
            ? `We have nothing matching “${search.text}”.`
            : 'We have no products yet. Check back soon.'}
      </EmptyState>
    )
  }
  return (
    <ul className="product-grid" aria-busy={busy} aria-label="Products">
      {results ? results.items.map((p) => <ProductCard key={p.sku} product={p} />) : <ProductCardSkeletons />}
    </ul>
  )
}

function ProductCard({ product }: { product: ProductSummary }) {
  const formatMoney = useFormatMoney()
  return (
    <li>
      <Link to={`/products/${encodeURIComponent(product.sku)}`} className="card-link">
        <Card interactive className="product-card">
          <div className="product-card-media">
            <ProductImage product={{ name: product.name, images: product.image ? [product.image] : [] }} />
            {!product.inStock && <Badge className="product-card-stock">Out of stock</Badge>}
          </div>
          <span className="brand-name">{product.attributes.brand}</span>
          <span className="product-name">{product.name}</span>
          <span className="price">
            {product.priceVaries && <span className="price-from">From </span>}
            {formatMoney(product.priceFrom)}
          </span>
        </Card>
      </Link>
    </li>
  )
}

/** The previous and next pages, and where this one sits; hidden while everything fits on one page. */
function Pager({ results, search }: { results: SearchResults; search: Search }) {
  const count = pageCount({ size: searchPageSize, total: results.total })
  if (count === 1) return null
  const shown = search.page + 1
  return (
    <nav className="pager" aria-label="Product pages">
      {shown > 1 ? (
        <ButtonLink to={hrefOf(withPage(search, search.page - 1))} rel="prev">
          Previous page
        </ButtonLink>
      ) : (
        <span />
      )}
      <span className="muted">
        Page {shown} of {count}
      </span>
      {shown < count ? (
        <ButtonLink to={hrefOf(withPage(search, search.page + 1))} rel="next">
          Next page
        </ButtonLink>
      ) : (
        <span />
      )}
    </nav>
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

/** An empty shelf with a lone box, for a search with no Products. */
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
