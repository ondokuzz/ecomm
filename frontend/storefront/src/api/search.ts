import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { type Search, type SearchResults, apiQueryOf } from '../domain/search'
import { api } from './http'

/** How many Products a page of the listing shows. */
export const searchPageSize = 24

/**
 * A page of the Products matching a search, with its facets. While a changed search loads, the last
 * results stay, so the listing doesn't flash empty with every filter.
 */
export function useSearch(search: Search) {
  const query = apiQueryOf(search, searchPageSize)
  return useQuery({
    queryKey: ['search', query],
    queryFn: () => api<SearchResults>('search-discovery', `/search?${query}`),
    placeholderData: keepPreviousData,
  })
}
