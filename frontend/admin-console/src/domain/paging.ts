/** One page of a list, counting from 0, with how many items there are in all. */
export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}
