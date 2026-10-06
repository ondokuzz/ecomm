import { type FormEvent, useId, useState } from 'react'
import { Link } from 'react-router'
import { useCurrencies } from '../api/currencies'
import { lookupFailure } from '../api/failure'
import { type Currencies, decimalOf, moneyOf } from '../domain/money'
import {
  type AttributeGroup,
  type Facets,
  type Range,
  type Search,
  attributeGroups,
  hasFilters,
  listingHref,
  urlOf,
  withPrice,
  withRange,
  withoutFilters,
} from '../domain/search'
import { LookupError } from './Status'
import { Button } from './ui/Button'
import { Input } from './ui/Input'

/**
 * The chosen Category's attributes and the price range, built from the facets. On a phone it folds
 * away behind a "Filters" button.
 */
export function FilterPanel({
  facets,
  search,
  go,
}: {
  facets: Facets | undefined
  search: Search
  go: (s: Search) => void
}) {
  const [open, setOpen] = useState(false)
  const panel = useId()
  const currencies = useCurrencies()
  if (!facets) return <aside className="filters" aria-busy="true" />
  const groups = attributeGroups(facets, search)
  return (
    <aside className="filters" aria-label="Filters">
      <Button className="filters-toggle" aria-expanded={open} aria-controls={panel} onClick={() => setOpen(!open)}>
        Filters
      </Button>
      <div id={panel} className="filters-body" data-open={open || undefined}>
        {groups.map((group) =>
          group.kind === 'values' ? (
            <ValueGroup key={group.name} group={group} go={go} />
          ) : (
            <RangeGroup key={`${group.name} ${urlOf(search)}`} group={group} search={search} go={go} />
          ),
        )}
        {currencies.data && (
          <PriceGroup
            key={urlOf(search).toString()}
            facets={facets}
            currencies={currencies.data}
            search={search}
            go={go}
          />
        )}
        {/* The price is typed and shown by Catalog's Currencies, so without them it can't be offered. */}
        <LookupError failure={lookupFailure([currencies])} title="We couldn't load the price filter" />
        {hasFilters(search) && (
          <Link to={listingHref(withoutFilters(search))} className="filters-clear">
            Clear filters
          </Link>
        )}
      </div>
    </aside>
  )
}

/** An attribute's values as checkboxes with their counts; ticking one searches at once. */
function ValueGroup({ group, go }: { group: Extract<AttributeGroup, { kind: 'values' }>; go: (s: Search) => void }) {
  return (
    <fieldset className="filter-group">
      <legend className="capitalize">{group.name}</legend>
      {group.options.map((option) => (
        <label key={option.value} className="filter-option" aria-disabled={option.disabled || undefined}>
          <input
            type="checkbox"
            checked={option.selected}
            disabled={option.disabled}
            onChange={() => go(option.search)}
          />
          <span>{option.value}</span>
          <span className="filter-count">{option.count}</span>
        </label>
      ))}
    </fieldset>
  )
}

/** A NUMBER attribute's range, between its lowest and highest value. */
function RangeGroup({
  group,
  search,
  go,
}: {
  group: Extract<AttributeGroup, { kind: 'range' }>
  search: Search
  go: (s: Search) => void
}) {
  const [range, setRange] = useState<Range>(group.chosen)
  const invalid = (text: string | undefined) => text !== undefined && !/^-?\d+(\.\d+)?$/.test(text.trim())
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (invalid(range.min) || invalid(range.max)) return
    go(withRange(search, group.name, { min: range.min?.trim(), max: range.max?.trim() }))
  }
  return (
    <form className="filter-group" onSubmit={submit}>
      <fieldset>
        <legend className="capitalize">{group.name}</legend>
        <RangeFields
          name={group.name}
          range={range}
          placeholders={{ min: String(group.min), max: String(group.max) }}
          invalid={{ min: invalid(range.min), max: invalid(range.max) }}
          onChange={setRange}
        />
      </fieldset>
    </form>
  )
}

/**
 * The price range, typed as decimals in a Currency and sent in its Minor unit. Its bounds are the
 * Variant Prices in that Currency.
 */
function PriceGroup({
  facets,
  currencies,
  search,
  go,
}: {
  facets: Facets
  currencies: Currencies
  search: Search
  go: (s: Search) => void
}) {
  const [currency, setCurrency] = useState(search.currency ?? facets.prices[0]?.currency)
  const bounds = facets.prices.find((p) => p.currency === currency)
  const decimal = (amountMinor: string | number | undefined) =>
    amountMinor === undefined || !currency || !currencies.has(currency)
      ? ''
      : decimalOf({ amountMinor: Number(amountMinor), currency }, currencies)
  const [range, setRange] = useState<Range>({
    min: decimal(search.price.min) || undefined,
    max: decimal(search.price.max) || undefined,
  })
  if (facets.prices.length === 0 || !currency) return null

  const amount = (text: string | undefined) => (text ? moneyOf(text, currency, currencies)?.amountMinor : undefined)
  const invalid = (text: string | undefined) => text !== undefined && text !== '' && amount(text) === undefined
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (invalid(range.min) || invalid(range.max)) return
    const min = amount(range.min)
    const max = amount(range.max)
    const chosen = { ...(min !== undefined && { min: String(min) }), ...(max !== undefined && { max: String(max) }) }
    go(withPrice(search, min === undefined && max === undefined ? undefined : currency, chosen))
  }
  return (
    <form className="filter-group" onSubmit={submit}>
      <fieldset>
        <legend>Price</legend>
        {facets.prices.length > 1 && (
          <select
            className="input"
            aria-label="Currency"
            value={currency}
            onChange={(event) => setCurrency(event.target.value)}
          >
            {facets.prices.map((p) => (
              <option key={p.currency}>{p.currency}</option>
            ))}
          </select>
        )}
        <RangeFields
          name={`price in ${currency}`}
          range={range}
          placeholders={{ min: decimal(bounds?.min), max: decimal(bounds?.max) }}
          invalid={{ min: invalid(range.min), max: invalid(range.max) }}
          onChange={setRange}
        />
      </fieldset>
    </form>
  )
}

function RangeFields({
  name,
  range,
  placeholders,
  invalid = {},
  onChange,
}: {
  name: string
  range: Range
  placeholders: { min: string; max: string }
  invalid?: { min?: boolean; max?: boolean }
  onChange: (range: Range) => void
}) {
  const set = (end: 'min' | 'max', value: string) => onChange({ ...range, [end]: value === '' ? undefined : value })
  return (
    <div className="filter-range">
      <Input
        inputMode="decimal"
        aria-label={`Lowest ${name}`}
        placeholder={placeholders.min}
        value={range.min ?? ''}
        aria-invalid={invalid.min || undefined}
        onChange={(event) => set('min', event.target.value)}
      />
      <span aria-hidden="true">–</span>
      <Input
        inputMode="decimal"
        aria-label={`Highest ${name}`}
        placeholder={placeholders.max}
        value={range.max ?? ''}
        aria-invalid={invalid.max || undefined}
        onChange={(event) => set('max', event.target.value)}
      />
      <Button type="submit" size="sm">
        Apply
      </Button>
    </div>
  )
}
