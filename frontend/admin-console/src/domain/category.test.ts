import { describe, expect, it } from 'vitest'
import { type Category, blankDefinition, categoryRequest, formOf } from './category'

const phones: Category = {
  slug: 'phones',
  name: 'Phones',
  productCount: 7,
  attributes: [
    { name: 'brand', type: 'TEXT', values: [], required: true, variantAxis: false },
    { name: 'storage', type: 'ENUM', values: ['128 GB', '256 GB'], required: true, variantAxis: true },
  ],
}

describe('formOf', () => {
  it("lays a Category out for editing, an ENUM's values as one comma-separated line", () => {
    expect(formOf(phones)).toEqual({
      slug: 'phones',
      name: 'Phones',
      attributes: [
        { name: 'brand', type: 'TEXT', values: '', required: true, variantAxis: false },
        { name: 'storage', type: 'ENUM', values: '128 GB, 256 GB', required: true, variantAxis: true },
      ],
    })
  })

  it('starts a new Category empty', () => {
    expect(formOf(undefined)).toEqual({ slug: '', name: '', attributes: [] })
  })
})

describe('categoryRequest', () => {
  it('sends the form back as Catalog takes it', () => {
    expect(categoryRequest(formOf(phones))).toEqual({
      slug: 'phones',
      name: 'Phones',
      attributes: [
        { name: 'brand', type: 'TEXT', values: [], required: true, variantAxis: false },
        { name: 'storage', type: 'ENUM', values: ['128 GB', '256 GB'], required: true, variantAxis: true },
      ],
    })
  })

  it("trims names and an ENUM's values, and drops empty values", () => {
    const form = {
      slug: ' cameras ',
      name: ' Cameras ',
      attributes: [{ ...blankDefinition(), name: ' mount ', type: 'ENUM' as const, values: ' E,RF , ,Z, ' }],
    }
    expect(categoryRequest(form)).toEqual({
      slug: 'cameras',
      name: 'Cameras',
      attributes: [{ name: 'mount', type: 'ENUM', values: ['E', 'RF', 'Z'], required: false, variantAxis: false }],
    })
  })

  it('sends no values for a type other than ENUM, whatever was typed before switching', () => {
    const form = { ...formOf(phones) }
    form.attributes = [{ ...form.attributes[1]!, type: 'TEXT' }]
    expect(categoryRequest(form).attributes[0]!.values).toEqual([])
  })
})
