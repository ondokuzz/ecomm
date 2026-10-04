import { describe, expect, it } from 'vitest'
import { pageCount, pageIndexFromUrl } from './paging'

describe('pageIndexFromUrl', () => {
  it('reads the page the Customer sees, counting from 1, as an index from 0', () => {
    expect(pageIndexFromUrl('3')).toBe(2)
  })

  it('is the first page when the URL names none, or one that cannot be', () => {
    for (const value of [null, '', '0', '-2', '1.5', 'two']) {
      expect(pageIndexFromUrl(value)).toBe(0)
    }
  })
})

describe('pageCount', () => {
  it('rounds a part-filled last page up', () => {
    expect(pageCount({ size: 20, total: 41 })).toBe(3)
  })

  it('is one page even when there is nothing', () => {
    expect(pageCount({ size: 20, total: 0 })).toBe(1)
  })
})
