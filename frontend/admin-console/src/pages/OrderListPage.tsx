import { type FormEvent, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { useCurrencies } from '../api/currencies'
import { useOrders } from '../api/orders'
import { ErrorMessage, FieldError, SkeletonRows } from '../components/Status'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { Button, ButtonLink } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import {
  type OrderFilters,
  filtersOf,
  formatTime,
  orderReference,
  orderStatusLabel,
  orderStatuses,
  ordersPageSize,
  ordersQueryOf,
  pageCount,
  searchOf,
} from '../domain/order'
import { formatMoney } from '../domain/money'

const columns = 6

/** Where an Order page's back link returns to: the list as it was filtered and paged. */
export interface FromListState {
  list: string
}

/**
 * Every Customer's Orders, newest first, a page at a time. The filters and the page live in the URL
 * (`?status=&customer=&from=&to=&reference=&page=`), so a filtered list can be reloaded or shared.
 * Nothing here changes an Order.
 */
export function OrderListPage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const filters = filtersOf(params)
  const request = ordersQueryOf(filters, ordersPageSize)
  const orders = useOrders('query' in request ? request.query : undefined)
  const currencies = useCurrencies()

  const show = (next: OrderFilters) => setParams(new URLSearchParams(searchOf(next)))

  const invalid = 'error' in request
  const error = orders.error ?? currencies.error
  const page = orders.data
  const pages = page ? pageCount(page) : 1

  return (
    <>
      <div className="page-heading">
        <h1>Orders</h1>
      </div>
      <p className="muted page-intro">
        Every Customer's Orders, newest first. Open one to see its Status history. Orders can't be changed here.
      </p>

      {/* Keyed by the URL, so Back or a cleared filter shows in the form too. */}
      <FilterForm
        key={location.search}
        filters={filters}
        referenceError={invalid ? request.error : undefined}
        onApply={(next) => show({ ...next, page: 0 })}
      />

      {invalid ? null : error ? (
        <ErrorMessage
          error={error}
          title={orders.error ? "Couldn't load the Orders" : "Couldn't load the currencies"}
          retrying={orders.isFetching || currencies.isFetching}
          onRetry={() => (orders.error ? orders.refetch() : currencies.refetch())}
        />
      ) : (
        <>
          <div className="table-scroll">
            <table className="table" aria-busy={orders.isPlaceholderData || undefined}>
              <thead>
                <tr>
                  <th scope="col">Order</th>
                  <th scope="col">Placed</th>
                  <th scope="col">Customer</th>
                  <th scope="col" className="num">
                    Items
                  </th>
                  <th scope="col" className="num">
                    Total
                  </th>
                  <th scope="col">Status</th>
                </tr>
              </thead>
              {page && currencies.data ? (
                <tbody>
                  {page.items.length === 0 && (
                    <tr>
                      <td colSpan={columns} className="table-empty">
                        {page.total === 0 ? 'No Orders match these filters.' : 'There are no Orders on this page.'}
                      </td>
                    </tr>
                  )}
                  {page.items.map((order) => (
                    <tr key={order.id}>
                      <th scope="row">
                        <Link
                          to={`/orders/${order.id}`}
                          state={{ list: location.search } satisfies FromListState}
                          aria-label={`Order #${orderReference(order)}`}
                        >
                          <code>#{orderReference(order)}</code>
                        </Link>
                      </th>
                      <td>
                        <time dateTime={order.placedAt}>{formatTime(order.placedAt)}</time>
                      </td>
                      <td>
                        <code>{order.customerId}</code>
                      </td>
                      <td className="num">{order.lines.reduce((sum, line) => sum + line.quantity, 0)}</td>
                      <td className="num">{formatMoney(order.total, currencies.data)}</td>
                      <td>
                        <OrderStatusBadge status={order.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              ) : (
                <SkeletonRows rows={5} columns={columns} />
              )}
            </table>
          </div>
          {page && (
            <nav className="pager" aria-label="Pages">
              <span className="muted">
                Page {page.page + 1} of {pages} · {page.total === 1 ? '1 Order' : `${page.total} Orders`}
              </span>
              <Button
                size="sm"
                disabled={page.page === 0 || orders.isPlaceholderData}
                onClick={() => show({ ...filters, page: page.page - 1 })}
              >
                Previous
              </Button>
              <Button
                size="sm"
                disabled={page.page + 1 >= pages || orders.isPlaceholderData}
                onClick={() => show({ ...filters, page: page.page + 1 })}
              >
                Next
              </Button>
            </nav>
          )}
        </>
      )}
    </>
  )
}

/** The list's filters as a form; applying it goes back to the first page. */
function FilterForm({
  filters,
  referenceError,
  onApply,
}: {
  filters: OrderFilters
  referenceError: string | undefined
  onApply: (filters: OrderFilters) => void
}) {
  const [form, setForm] = useState(filters)
  const set = (field: keyof Omit<OrderFilters, 'page'>, value: string) => setForm({ ...form, [field]: value })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    onApply(form)
  }
  const filtered = Object.values({ ...filters, page: '' }).some(Boolean)
  return (
    <form className="filters" role="search" aria-label="Filter Orders" onSubmit={submit}>
      <label className="field">
        Status
        <select className="input" value={form.status} onChange={(e) => set('status', e.target.value)}>
          <option value="">Any Status</option>
          {orderStatuses.map((status) => (
            <option key={status} value={status}>
              {orderStatusLabel(status)}
            </option>
          ))}
        </select>
      </label>
      <label className="field">
        Customer ID
        <Input value={form.customer} onChange={(e) => set('customer', e.target.value)} />
      </label>
      <label className="field">
        Placed from
        <Input type="date" value={form.from} onChange={(e) => set('from', e.target.value)} />
      </label>
      <label className="field">
        Placed to
        <Input type="date" value={form.to} onChange={(e) => set('to', e.target.value)} />
      </label>
      <label className="field">
        Order reference
        <Input
          placeholder="#3F2A9C1B"
          value={form.reference}
          aria-invalid={referenceError ? true : undefined}
          aria-describedby={referenceError ? 'reference-error' : undefined}
          onChange={(e) => set('reference', e.target.value)}
        />
        <FieldError id="reference-error" message={referenceError} />
      </label>
      <div className="filters-actions">
        <Button type="submit" variant="primary">
          Filter
        </Button>
        {filtered && <ButtonLink to="/orders">Clear</ButtonLink>}
      </div>
    </form>
  )
}
