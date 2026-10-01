/** What kind of value an attribute holds; an `ENUM` holds one of its definition's `values`. */
export type AttributeType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM'

export const attributeTypes: readonly AttributeType[] = ['TEXT', 'NUMBER', 'BOOLEAN', 'ENUM']

/**
 * An attribute a Category's Products carry. A Variant axis tells a Product's Variants apart, such
 * as colour or storage; any other definition describes the Product itself.
 */
export interface AttributeDefinition {
  name: string
  type: AttributeType
  /** An `ENUM`'s allowed values; empty for any other type. */
  values: string[]
  required: boolean
  variantAxis: boolean
}

/** A Category as Catalog answers it. */
export interface Category {
  slug: string
  name: string
  productCount: number
  attributes: AttributeDefinition[]
}

/** A Category as Staff send it; on an update the slug is the path's. */
export interface CategoryRequest {
  slug: string
  name: string
  attributes: AttributeDefinition[]
}

/** One attribute definition being edited: an `ENUM`'s values as one comma-separated line. */
export interface DefinitionForm extends Omit<AttributeDefinition, 'values'> {
  values: string
}

/** The Category editor's state. Its rows are in the order they are sent, so `attributes[i]` names the same row on the server. */
export interface CategoryForm {
  slug: string
  name: string
  attributes: DefinitionForm[]
}

export function blankDefinition(): DefinitionForm {
  return { name: '', type: 'TEXT', values: '', required: false, variantAxis: false }
}

/** `category` laid out for editing, or an empty form for a new one. */
export function formOf(category: Category | undefined): CategoryForm {
  if (!category) return { slug: '', name: '', attributes: [] }
  return {
    slug: category.slug,
    name: category.name,
    attributes: category.attributes.map((definition) => ({ ...definition, values: definition.values.join(', ') })),
  }
}

/**
 * The form as Catalog takes it: names trimmed, and an `ENUM`'s values split on commas, trimmed and
 * without empty ones. Any other type sends no values, even when some were typed before its type changed.
 */
export function categoryRequest(form: CategoryForm): CategoryRequest {
  return {
    slug: form.slug.trim(),
    name: form.name.trim(),
    attributes: form.attributes.map((definition) => ({
      name: definition.name.trim(),
      type: definition.type,
      values: definition.type === 'ENUM' ? splitValues(definition.values) : [],
      required: definition.required,
      variantAxis: definition.variantAxis,
    })),
  }
}

function splitValues(line: string): string[] {
  return line
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value !== '')
}
