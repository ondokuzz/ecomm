import type { FieldErrors } from '../api/fieldErrors'
import type { Category } from './category'
import { type Currencies, type Money, decimalOf, moneyOf } from './money'

/**
 * A purchasable version of a Product, told apart from its siblings by its axis values. Its Variant
 * ID is unique across the Catalog and never changes, since Carts, Orders and Stock name it.
 */
export interface Variant {
  id: string
  /** Its value for each of its Category's Variant axes, in the Category's order. */
  axisValues: Record<string, string>
  price: Money
  /** Its own images, which replace its Product's; usually none. */
  images: string[]
}

/** A Product as Catalog answers it. */
export interface Product {
  sku: string
  name: string
  category: string
  attributes: Record<string, string>
  images: string[]
  /** The lowest of its Variants' Prices. */
  priceFrom: Money
  variants: Variant[]
}

/** A Product as Staff send it: no `priceFrom`, which Catalog works out. */
export type ProductRequest = Omit<Product, 'priceFrom'>

/** A Variant's Stock as Inventory answers it: `quantity` is what's left to sell. */
export interface Stock {
  variantId: string
  quantity: number
  onHand: number
  reserved: number
}

/** Each Variant's on-hand count as loaded, by Variant ID; undefined when Inventory doesn't stock it. */
export type OnHandByVariant = Record<string, number | undefined>

/** One Variant being edited: its Price as a decimal and its images as text. */
export interface VariantForm {
  id: string
  /** Catalog has it, so its Variant ID can't change. */
  saved: boolean
  axisValues: Record<string, string>
  price: string
  /** Separated by commas, on one line. */
  images: string
  /** The On-hand count to set; blank leaves it alone. */
  onHand: string
}

/**
 * The Product editor's state. Its Variants are in the order they are sent, so `variants[i]` names
 * the same row on the server. Every Variant is priced in its one `currency`.
 */
export interface ProductForm {
  sku: string
  name: string
  category: string
  attributes: Record<string, string>
  /** One per line. */
  images: string
  currency: string
  variants: VariantForm[]
}

/** The Variant axes of `category`, such as color and storage, in its order. */
export function axesOf(category: Category | undefined) {
  return category?.attributes.filter((definition) => definition.variantAxis) ?? []
}

/** The definitions of `category` that describe the Product itself rather than tell its Variants apart. */
export function attributesOf(category: Category | undefined) {
  return category?.attributes.filter((definition) => !definition.variantAxis) ?? []
}

/** A new Variant row with a blank value for each of the Category's axes and no Stock yet. */
export function blankVariant(category: Category | undefined): VariantForm {
  return {
    id: '',
    saved: false,
    axisValues: Object.fromEntries(axesOf(category).map((axis) => [axis.name, ''])),
    price: '',
    images: '',
    onHand: '0',
  }
}

/**
 * `product` laid out for editing with its Variants' On-hand counts, its Prices as decimals in
 * Catalog's `currencies`, or a new Product in `category`.
 */
export function formOf(
  product: Product | undefined,
  category: Category | undefined,
  onHand: OnHandByVariant,
  currencies: Currencies,
): ProductForm {
  if (!product) {
    return {
      sku: '',
      name: '',
      category: category?.slug ?? '',
      attributes: {},
      images: '',
      currency: 'EUR',
      variants: [blankVariant(category)],
    }
  }
  return {
    sku: product.sku,
    name: product.name,
    category: product.category,
    attributes: { ...product.attributes },
    images: product.images.join('\n'),
    currency: product.variants[0]?.price.currency ?? 'EUR',
    variants: product.variants.map((variant) => ({
      id: variant.id,
      saved: true,
      axisValues: { ...variant.axisValues },
      price: decimalOf(variant.price, currencies),
      images: variant.images.join(', '),
      onHand: onHand[variant.id]?.toString() ?? '',
    })),
  }
}

/**
 * The form as Catalog takes it, or the fields it can't send. Text is trimmed and blank attribute
 * and axis values are left out, for Catalog to name if they are required. Only what `category`
 * defines is sent. The currency must be one of Catalog's `currencies`, a Price must read as Money
 * in it, and an on-hand count must be a whole number or blank; these are named as `currency`,
 * `variants[i].price` and `variants[i].onHand`.
 */
export function productRequest(
  form: ProductForm,
  category: Category | undefined,
  currencies: Currencies,
): { request: ProductRequest; errors: FieldErrors } | { request?: undefined; errors: FieldErrors } {
  const errors: FieldErrors = {}
  const currencyValid = moneyOf('0', form.currency, currencies) !== undefined
  if (!currencyValid) errors.currency = 'must be a currency Catalog prices in, such as EUR'

  const variants = form.variants.map((variant, index) => {
    const price = moneyOf(variant.price, form.currency, currencies)
    if (!price && currencyValid) errors[`variants[${index}].price`] = 'must be a Price such as 799.00'
    if (variant.onHand.trim() !== '' && onHandOf(variant) === undefined) {
      errors[`variants[${index}].onHand`] = 'must be a whole number, 0 or more'
    }
    return {
      id: variant.id.trim(),
      axisValues: valuesFor(axesOf(category), variant.axisValues),
      price: price!,
      images: splitList(variant.images),
    }
  })
  if (Object.keys(errors).length > 0) return { errors }

  return {
    request: {
      sku: form.sku.trim(),
      name: form.name.trim(),
      category: form.category,
      attributes: valuesFor(attributesOf(category), form.attributes),
      images: splitList(form.images),
      variants,
    },
    errors,
  }
}

/**
 * Catalog's field errors as the editor's fields. Every Variant is priced in the form's one
 * currency, so a Variant's `variants[i].price.currency` is the `currency` field.
 */
export function productFieldErrors(errors: FieldErrors): FieldErrors {
  return Object.fromEntries(
    Object.entries(errors).map(([field, message]) => [
      /^variants\[\d+\]\.price\.currency$/.test(field) ? 'currency' : field,
      message,
    ]),
  )
}

/** A Variant whose On-hand count to set through Inventory, by its row in the form. */
export interface StockChange {
  index: number
  variantId: string
  onHand: number
}

/** Each Variant whose on-hand count was changed, or that Inventory doesn't stock yet; a blank count is left alone. */
export function stockChanges(form: ProductForm, loaded: OnHandByVariant): StockChange[] {
  return form.variants.flatMap((variant, index) => {
    const onHand = onHandOf(variant)
    const variantId = variant.id.trim()
    if (onHand === undefined || loaded[variantId] === onHand) return []
    return [{ index, variantId, onHand }]
  })
}

/** Whether `query` is part of the Product's name or SKU, whatever the case; an empty one matches all. */
export function matchesSearch(product: Pick<Product, 'sku' | 'name'>, query: string): boolean {
  const needle = query.trim().toLowerCase()
  return product.name.toLowerCase().includes(needle) || product.sku.toLowerCase().includes(needle)
}

function onHandOf(variant: VariantForm): number | undefined {
  const text = variant.onHand.trim()
  return /^\d+$/.test(text) && Number.isSafeInteger(Number(text)) ? Number(text) : undefined
}

/** The non-blank, trimmed values of `values` for each of `definitions`, in their order. */
function valuesFor(definitions: { name: string }[], values: Record<string, string>): Record<string, string> {
  return Object.fromEntries(
    definitions.flatMap(({ name }) => {
      const value = values[name]?.trim() ?? ''
      return value === '' ? [] : [[name, value]]
    }),
  )
}

function splitList(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter((item) => item !== '')
}
