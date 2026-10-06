import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useFormatMoney } from '../api/currencies'
import { useRatingSummaries } from '../api/reviews'
import { searchPageSize, useSearch } from '../api/search'
import { FilterPanel } from '../components/FilterPanel'
import { ProductImage } from '../components/ProductImage'
import { SummaryStars } from '../components/StarRating'
import { EmptyState, ErrorState } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button, ButtonLink } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Icon } from '../components/ui/Icon'
import { Input } from '../components/ui/Input'
import { Skeleton } from '../components/ui/Skeleton'
import { productCountLabel } from '../domain/catalog'
import { pageCount } from '../domain/paging'
import type { RatingSummary } from '../domain/reviews'
import {
  type FacetOption,
  type Facets,
  type ProductSummary,
  type Search,
  type SearchResults,
  type SortKey,
  categoryOptions,
  hasFilters,
  listingHref,
  searchFromUrl,
  urlOf,
  withInStock,
  withPage,
  withSort,
  withText,
  withoutFilters,
} from '../domain/search'

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
    <Link to={listingHref(option.search)} className="chip" aria-current={option.selected || undefined}>
      {content}
    </Link>
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
        <EmptyState title="There's nothing on this page" action={<ButtonLink to={listingHref(withPage(search, 0))}>Go to the first page</ButtonLink>}>
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
            <ButtonLink to={listingHref(withoutFilters(search))}>Clear filters</ButtonLink>
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
  return <ProductGrid results={results} busy={busy} />
}

/** The page's Products, each with its Rating summary once Reviews & Ratings answers; a card waits for nothing. */
function ProductGrid({ results, busy }: { results: SearchResults | undefined; busy: boolean }) {
  const summaries = useRatingSummaries(results?.items.map((p) => p.sku) ?? [])
  return (
    <ul className="product-grid" aria-busy={busy} aria-label="Products">
      {results ? (
        results.items.map((p) => <ProductCard key={p.sku} product={p} summary={summaries.data?.[p.sku]} />)
      ) : (
        <ProductCardSkeletons />
      )}
    </ul>
  )
}

function ProductCard({ product, summary }: { product: ProductSummary; summary: RatingSummary | undefined }) {
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
          <SummaryStars summary={summary} />
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
        <ButtonLink to={listingHref(withPage(search, search.page - 1))} rel="prev">
          Previous page
        </ButtonLink>
      ) : (
        <span />
      )}
      <span className="muted">
        Page {shown} of {count}
      </span>
      {shown < count ? (
        <ButtonLink to={listingHref(withPage(search, search.page + 1))} rel="next">
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
