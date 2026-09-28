import { describe, expect, it } from 'vitest'
import { addToast, dismissToast } from './toasts'

const toast = (id: number) => ({ id, message: `Toast ${id}` })

describe('addToast', () => {
  it('puts the new toast last', () => {
    expect(addToast([toast(1)], toast(2)).map((t) => t.id)).toEqual([1, 2])
  })

  it('drops the oldest once more than three are showing', () => {
    expect(addToast([toast(1), toast(2), toast(3)], toast(4)).map((t) => t.id)).toEqual([2, 3, 4])
  })
})

describe('dismissToast', () => {
  it('removes only that toast', () => {
    expect(dismissToast([toast(1), toast(2), toast(3)], 2).map((t) => t.id)).toEqual([1, 3])
  })

  it('leaves the toasts as they are when that one is already gone', () => {
    expect(dismissToast([toast(1)], 7).map((t) => t.id)).toEqual([1])
  })
})
