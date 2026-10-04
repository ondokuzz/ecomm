/** One page of a list, counting from 0, with how many items there are in all. */
export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

/** The page a URL's `page` parameter names, which counts from 1, as an index from 0; the first page unless it names a real one. */
export function pageIndexFromUrl(value: string | null): number {
  const page = Number(value)
  return Number.isInteger(page) && page >= 1 ? page - 1 : 0
}

/** How many pages the list has: at least one, even when it is empty. */
export function pageCount({ size, total }: Pick<Page<unknown>, 'size' | 'total'>): number {
  return Math.max(1, Math.ceil(total / size))
}
