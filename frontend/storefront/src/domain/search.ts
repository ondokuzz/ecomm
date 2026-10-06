import type { Money } from './money'
import { pageIndexFromUrl } from './paging'

/** How Search orders its results; relevance, with no text, is newest first. */
export type SortKey = 'relevance' | 'newest' | 'price-asc' | 'price-desc'

export const sortKeys: readonly SortKey[] = ['relevance', 'newest', 'price-asc', 'price-desc']

/** Both ends of a range, as written in the URL; an end left out is open. */
export interface Range {
  min?: string
  max?: string
}

/**
 * What the Customer is searching for, all of it held in the listing's URL, so a reload or a shared
 * link shows the same view. `values` are an attribute's chosen values, any of which a Product must
 * have; `ranges` a NUMBER attribute's; `price` is in `currency`'s Minor unit. `page` counts from 0.
 */
export interface Search {
  text?: string
  category?: string
  values: Record<string, string[]>
  ranges: Record<string, Range>
  currency?: string
  price: Range
  inStock: boolean
  sort?: SortKey
  page: number
}

export const emptySearch: Search = { values: {}, ranges: {}, price: {}, inStock: false, page: 0 }

/** What a Product card shows, as Search sums a Product up. */
export interface ProductSummary {
  sku: string
  name: string
  /** Its first image, or its first Variant's own; null when it has none. */
  image: string | null
  /** The lowest of its Variants' Prices, "from" when `priceVaries`. */
  priceFrom: Money
  priceVaries: boolean
  /** Whether any of its Variants is in Stock. */
  inStock: boolean
  attributes: Record<string, string>
}

export interface ValueCount {
  value: string
  count: number
}

export interface AttributeFacet {
  name: string
  type: 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM'
  /** An ENUM's, TEXT's or BOOLEAN's values, with how many Products each would leave. */
  values: ValueCount[]
  /** A NUMBER's lowest and highest value; null when no Product has one. */
  min: number | null
  max: number | null
}

/**
 * What the results could be narrowed by, each counted as if its own filter weren't applied, so a
 * chosen value still shows what its siblings would add.
 */
export interface Facets {
  categories: { slug: string; name: string; count: number }[]
  /** Once a Category is chosen, one per Attribute definition, in its order. */
  attributes: AttributeFacet[]
  prices: { currency: string; min: number; max: number }[]
  inStock: number
}

export interface SearchResults {
  items: ProductSummary[]
  page: number
  size: number
  total: number
  facets: Facets
}

const attributePrefix = 'attr.'
const rangePrefix = 'range.'
const decimalRange = /^(-?\d+(?:\.\d+)?)?\.\.(-?\d+(?:\.\d+)?)?$/
const amountRange = /^(\d+)?\.\.(\d+)?$/

/**
 * The search a listing URL holds. Whatever Search would refuse, such as an unknown sort or a range
 * upside down, is left out, so a mangled link still shows Products.
 */
export function searchFromUrl(params: URLSearchParams): Search {
  const values: Record<string, string[]> = {}
  const ranges: Record<string, Range> = {}
  for (const [key, value] of params) {
    if (key.startsWith(attributePrefix) && key.length > attributePrefix.length) {
      const name = key.slice(attributePrefix.length)
      if (!values[name]?.includes(value)) values[name] = [...(values[name] ?? []), value]
    } else if (key.startsWith(rangePrefix) && key.length > rangePrefix.length) {
      const range = rangeOf(value, decimalRange)
      if (range) ranges[key.slice(rangePrefix.length)] = range
    }
  }
  const currency = /^[A-Z]{3}$/.test(params.get('currency') ?? '') ? params.get('currency')! : undefined
  const sort = params.get('sort')
  return {
    text: params.get('q')?.trim() || undefined,
    category: params.get('category') || undefined,
    values,
    ranges,
    currency,
    price: (currency && rangeOf(params.get('price') ?? '', amountRange)) || {},
    inStock: params.get('inStock') === 'true',
    sort: sortKeys.includes(sort as SortKey) ? (sort as SortKey) : undefined,
    page: pageIndexFromUrl(params.get('page')),
  }
}

/** A `<min>..<max>` range, with at least one end and not upside down; undefined otherwise. */
function rangeOf(value: string, pattern: RegExp): Range | undefined {
  const match = pattern.exec(value)
  if (!match || (match[1] === undefined && match[2] === undefined)) return undefined
  const [, min, max] = match
  if (min !== undefined && max !== undefined && Number(min) > Number(max)) return undefined
  return { ...(min !== undefined && { min }), ...(max !== undefined && { max }) }
}

function rangeText({ min, max }: Range): string | undefined {
  return min === undefined && max === undefined ? undefined : `${min ?? ''}..${max ?? ''}`
}

/** The listing URL's query for a search: only what is set, and the page counting from 1. */
export function urlOf(search: Search): URLSearchParams {
  const params = filterParams(search)
  if (search.sort) params.append('sort', search.sort)
  if (search.page > 0) params.append('page', String(search.page + 1))
  return params
}

/** The listing's address for a search. */
export function listingHref(search: Search): string {
  const query = urlOf(search).toString()
  return query ? `/?${query}` : '/'
}

/** The query `GET /search` takes for a page of `size` Products, its page counting from 0. */
export function apiQueryOf(search: Search, size: number): string {
  const params = filterParams(search)
  if (search.sort) params.append('sort', search.sort)
  params.append('page', String(search.page))
  params.append('size', String(size))
  return params.toString()
}

function filterParams(search: Search): URLSearchParams {
  const params = new URLSearchParams()
  if (search.text) params.append('q', search.text)
  if (search.category) params.append('category', search.category)
  for (const [name, values] of Object.entries(search.values)) {
    for (const value of values) params.append(attributePrefix + name, value)
  }
  for (const [name, range] of Object.entries(search.ranges)) {
    const text = rangeText(range)
    if (text) params.append(rangePrefix + name, text)
  }
  if (search.currency) {
    params.append('currency', search.currency)
    const price = rangeText(search.price)
    if (price) params.append('price', price)
  }
  if (search.inStock) params.append('inStock', 'true')
  return params
}

// Every change but paging starts again from the first page, since the old page may no longer exist.

export function withText(search: Search, text: string): Search {
  return { ...search, text: text.trim() || undefined, page: 0 }
}

/** Another Category, or every one; the attribute filters were the old Category's, so they go. */
export function withCategory(search: Search, category: string | undefined): Search {
  return { ...search, category, values: {}, ranges: {}, page: 0 }
}

/** Chooses the attribute's value, or clears it if it was chosen. */
export function toggleValue(search: Search, name: string, value: string): Search {
  const chosen = search.values[name] ?? []
  const next = chosen.includes(value) ? chosen.filter((v) => v !== value) : [...chosen, value]
  const values = { ...search.values }
  if (next.length) values[name] = next
  else delete values[name]
  return { ...search, values, page: 0 }
}

/** A NUMBER attribute's range; one with neither end clears it. */
export function withRange(search: Search, name: string, range: Range): Search {
  const ranges = { ...search.ranges }
  if (rangeText(range)) ranges[name] = range
  else delete ranges[name]
  return { ...search, ranges, page: 0 }
}

/** A price range in `currency`'s Minor unit; with neither end, Products priced in `currency`. */
export function withPrice(search: Search, currency: string | undefined, price: Range): Search {
  return { ...search, currency, price: currency ? price : {}, page: 0 }
}

export function withInStock(search: Search, inStock: boolean): Search {
  return { ...search, inStock, page: 0 }
}

export function withSort(search: Search, sort: SortKey | undefined): Search {
  return { ...search, sort, page: 0 }
}

export function withPage(search: Search, page: number): Search {
  return { ...search, page }
}

/** Whether anything narrows the results beyond the text. */
export function hasFilters(search: Search): boolean {
  return (
    search.category !== undefined ||
    Object.keys(search.values).length > 0 ||
    Object.keys(search.ranges).length > 0 ||
    search.currency !== undefined ||
    search.inStock
  )
}

/** The same text and sort, with nothing else narrowing them. */
export function withoutFilters(search: Search): Search {
  return { ...emptySearch, text: search.text, sort: search.sort }
}

/**
 * One choice in the filter panel: how many Products it would leave, whether it is chosen, and the
 * search choosing it (or clearing it) leads to. One that would leave nothing is disabled, unless it
 * is chosen, so it can always be cleared.
 */
export interface FacetOption {
  label: string
  count: number
  selected: boolean
  disabled: boolean
  search: Search
}

/** "All", counting every Category's Products, then each Category. */
export function categoryOptions(facets: Facets, search: Search): FacetOption[] {
  const all = facets.categories.reduce((sum, c) => sum + c.count, 0)
  return [
    option('All', all, search.category === undefined, withCategory(search, undefined)),
    ...facets.categories.map((c) => option(c.name, c.count, c.slug === search.category, withCategory(search, c.slug))),
  ]
}

function option(label: string, count: number, selected: boolean, search: Search): FacetOption {
  return { label, count, selected, disabled: count === 0 && !selected, search }
}

export type AttributeGroup =
  | { kind: 'values'; name: string; options: (FacetOption & { value: string })[] }
  | { kind: 'range'; name: string; min: number; max: number; chosen: Range }

/**
 * The chosen Category's attributes as the filter panel shows them: values to tick, or a NUMBER's
 * range. An attribute with nothing to choose from is left out.
 */
export function attributeGroups(facets: Facets, search: Search): AttributeGroup[] {
  return facets.attributes.flatMap((facet): AttributeGroup[] => {
    if (facet.type === 'NUMBER') {
      return facet.min === null || facet.max === null
        ? []
        : [{ kind: 'range', name: facet.name, min: facet.min, max: facet.max, chosen: search.ranges[facet.name] ?? {} }]
    }
    if (facet.values.length === 0) return []
    const chosen = search.values[facet.name] ?? []
    return [
      {
        kind: 'values',
        name: facet.name,
        options: facet.values.map(({ value, count }) => ({
          ...option(value, count, chosen.includes(value), toggleValue(search, facet.name, value)),
          value,
        })),
      },
    ]
  })
}
