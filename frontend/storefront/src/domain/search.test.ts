import { describe, expect, it } from 'vitest'
import {
  type Facets,
  type Search,
  apiQueryOf,
  attributeGroups,
  categoryOptions,
  emptySearch,
  hasFilters,
  listingHref,
  searchFromUrl,
  toggleValue,
  urlOf,
  withCategory,
  withInStock,
  withPage,
  withPrice,
  withRange,
  withSort,
  withText,
  withoutFilters,
} from './search'

const url = (query: string) => searchFromUrl(new URLSearchParams(query))
const query = (search: Search) => urlOf(search).toString()

const facets: Facets = {
  categories: [
    { slug: 'audio', name: 'Audio', count: 0 },
    { slug: 'phones', name: 'Phones', count: 3 },
  ],
  attributes: [
    {
      name: 'storage',
      type: 'ENUM',
      values: [
        { value: '128 GB', count: 2 },
        { value: '1 TB', count: 0 },
      ],
      min: null,
      max: null,
    },
    { name: 'brand', type: 'TEXT', values: [], min: null, max: null },
    { name: 'screen', type: 'NUMBER', values: [], min: 6.1, max: 6.8 },
    { name: 'weight', type: 'NUMBER', values: [], min: null, max: null },
  ],
  prices: [{ currency: 'EUR', min: 69900, max: 99900 }],
  inStock: 2,
}

describe('searchFromUrl', () => {
  it('reads every part of a search from the URL', () => {
    expect(
      url(
        'q=pixel&category=phones&attr.storage=128+GB&attr.storage=256+GB&range.screen=6..6.5' +
          '&currency=EUR&price=50000..&inStock=true&sort=price-asc&page=3',
      ),
    ).toEqual({
      text: 'pixel',
      category: 'phones',
      values: { storage: ['128 GB', '256 GB'] },
      ranges: { screen: { min: '6', max: '6.5' } },
      currency: 'EUR',
      price: { min: '50000' },
      inStock: true,
      sort: 'price-asc',
      page: 2,
    })
  })

  it('is everything when the URL names nothing', () => {
    expect(url('')).toEqual(emptySearch)
  })

  it('leaves out what it cannot send to Search, so a mangled link still shows Products', () => {
    expect(url('sort=cheapest&range.screen=big&price=1.5..&inStock=yes&page=0&q=+')).toEqual(emptySearch)
    expect(url('price=100..200')).toEqual(emptySearch)
  })
})

describe('urlOf', () => {
  it('writes a search back to the same URL', () => {
    const written =
      'q=pixel&category=phones&attr.storage=128+GB&attr.storage=256+GB&range.screen=6..6.5' +
      '&currency=EUR&price=50000..&inStock=true&sort=price-asc&page=3'
    expect(query(url(written))).toBe(written)
  })

  it('leaves out what is unset, and the first page', () => {
    expect(query(emptySearch)).toBe('')
    expect(query(withPage(emptySearch, 0))).toBe('')
  })
})

describe('listingHref', () => {
  it('is the listing with the search in its query, or the bare listing for everything', () => {
    expect(listingHref(withCategory(emptySearch, 'phones'))).toBe('/?category=phones')
    expect(listingHref(emptySearch)).toBe('/')
  })
})

describe('apiQueryOf', () => {
  it('asks Search for the page counting from 0, with its size', () => {
    const search = withPage(withCategory(emptySearch, 'phones'), 2)
    expect(apiQueryOf(search, 24)).toBe('category=phones&page=2&size=24')
  })
})

describe('changing a search', () => {
  const onPage3 = withPage(url('category=phones&attr.storage=128+GB&range.screen=6..'), 2)

  it('goes back to the first page', () => {
    for (const changed of [
      withText(onPage3, 'pixel'),
      toggleValue(onPage3, 'storage', '256 GB'),
      withRange(onPage3, 'screen', { max: '7' }),
      withPrice(onPage3, 'EUR', { min: '100' }),
      withInStock(onPage3, true),
      withSort(onPage3, 'newest'),
    ]) {
      expect(changed.page).toBe(0)
    }
  })

  it('adds a value, and takes it off again', () => {
    const added = toggleValue(onPage3, 'storage', '256 GB')
    expect(added.values).toEqual({ storage: ['128 GB', '256 GB'] })
    expect(toggleValue(toggleValue(added, 'storage', '128 GB'), 'storage', '256 GB').values).toEqual({})
  })

  it('drops the attribute filters with the Category they belong to', () => {
    const laptops = withCategory(onPage3, 'laptops')
    expect(laptops).toMatchObject({ category: 'laptops', values: {}, ranges: {} })
  })

  it('drops a range with neither end', () => {
    expect(withRange(onPage3, 'screen', {}).ranges).toEqual({})
  })

  it('clears the filters but keeps the text and the sort', () => {
    const search = withSort(withText(onPage3, 'pixel'), 'newest')
    expect(hasFilters(search)).toBe(true)
    const cleared = withoutFilters(withInStock(search, true))
    expect(cleared).toEqual({ ...emptySearch, text: 'pixel', sort: 'newest' })
    expect(hasFilters(cleared)).toBe(false)
  })
})

describe('categoryOptions', () => {
  it('offers "All" and each Category with its count, the chosen one selected, an empty one disabled', () => {
    const options = categoryOptions(facets, url('category=phones&attr.storage=1+TB'))
    expect(options.map(({ label, count, selected, disabled }) => ({ label, count, selected, disabled }))).toEqual([
      { label: 'All', count: 3, selected: false, disabled: false },
      { label: 'Audio', count: 0, selected: false, disabled: true },
      { label: 'Phones', count: 3, selected: true, disabled: false },
    ])
    expect(query(options[0].search)).toBe('')
    expect(query(options[2].search)).toBe('category=phones')
  })
})

describe('attributeGroups', () => {
  const groups = attributeGroups(facets, url('category=phones&attr.storage=1+TB&range.screen=6.5..'))

  it('lists each value with its count and the search choosing it leads to', () => {
    expect(groups[0]).toMatchObject({ kind: 'values', name: 'storage' })
    const options = groups[0].kind === 'values' ? groups[0].options : []
    expect(options.map(({ value, count, selected }) => ({ value, count, selected }))).toEqual([
      { value: '128 GB', count: 2, selected: false },
      { value: '1 TB', count: 0, selected: true },
    ])
    expect(query(options[0].search)).toBe('category=phones&attr.storage=1+TB&attr.storage=128+GB&range.screen=6.5..')
    expect(query(options[1].search)).toBe('category=phones&range.screen=6.5..')
  })

  it('disables a value that would leave nothing, unless it is chosen and so can be cleared', () => {
    const options = groups[0].kind === 'values' ? groups[0].options : []
    expect(options.map((o) => o.disabled)).toEqual([false, false])
    const unchosen = attributeGroups(facets, url('category=phones'))[0]
    expect(unchosen.kind === 'values' && unchosen.options.map((o) => o.disabled)).toEqual([false, true])
  })

  it('gives a NUMBER its bounds and the chosen range, and leaves out what has nothing to offer', () => {
    expect(groups.slice(1)).toEqual([{ kind: 'range', name: 'screen', min: 6.1, max: 6.8, chosen: { min: '6.5' } }])
  })
})
