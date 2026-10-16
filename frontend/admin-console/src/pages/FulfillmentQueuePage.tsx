import { useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { useQueueCounts, useShipments, useWarehouses } from '../api/shipments'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { formatAgo } from '../domain/ago'
import { orderReference, pageCount } from '../domain/order'
import {
  type Queue,
  queueOf,
  queueSearchOf,
  queuedStatuses,
  shipmentStatusLabel,
  shipmentsPageSize,
  shipmentsQueryOf,
  unitsOf,
} from '../domain/shipment'

const columns = 5

/** Where the chosen Warehouse is kept, per browser: Staff at one site look at its Shipments every time. */
const warehouseStorageKey = 'ecomm.fulfillment.warehouseId'

/** Where a Shipment page's back link returns to: the queue's tab and page as they were. */
export interface FromQueueState {
  queue: string
}

/**
 * The fulfillment queue: Shipments awaiting pick, picked, packed or on hold, each tab with its
 * count, oldest first so Orders are worked in the order they were paid. The tab and page live in
 * the URL (`?status=&page=`); the Warehouse filter is remembered by the browser.
 */
export function FulfillmentQueuePage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const queue = queueOf(params)
  const [warehouseId, setWarehouseId] = useRememberedWarehouse()
  const warehouses = useWarehouses()
  const counts = useQueueCounts(warehouseId)
  const shipments = useShipments(shipmentsQueryOf(queue, warehouseId, shipmentsPageSize))
  const page = shipments.data
  const pages = page ? pageCount(page) : 1
  const show = (next: Queue) => setParams(new URLSearchParams(queueSearchOf(next)))
  // How long ago, as of the page's fetch: it moves on with each refetch.
  const now = new Date(shipments.dataUpdatedAt)
  const label = shipmentStatusLabel(queue.status).toLowerCase()

  return (
    <>
      <div className="page-heading">
        <h1>Fulfillment</h1>
      </div>
      <nav className="subnav" aria-label="Shipment statuses">
        {queuedStatuses.map((status) => (
          <Link
            key={status}
            to={`/fulfillment${queueSearchOf({ status, page: 0 })}`}
            className={status === queue.status ? 'subnav-link active' : 'subnav-link'}
            aria-current={status === queue.status ? 'page' : undefined}
          >
            {shipmentStatusLabel(status)}{' '}
            {counts[status] !== undefined && (
              <Badge tone={status === 'ON_HOLD' && counts[status] > 0 ? 'danger' : 'neutral'}>{counts[status]}</Badge>
            )}
          </Link>
        ))}
      </nav>
      <div className="filters">
        <label className="field">
          Warehouse
          <select
            className="input"
            value={warehouseId ?? ''}
            onChange={(e) => {
              setWarehouseId(e.target.value || undefined)
              show({ ...queue, page: 0 })
            }}
          >
            <option value="">All Warehouses</option>
            {warehouses.data?.map((warehouse) => (
              <option key={warehouse.id} value={warehouse.id}>
                {warehouse.name} ({warehouse.id})
              </option>
            ))}
            {/* A remembered Warehouse that is no longer configured still shows what is chosen. */}
            {warehouseId && warehouses.data && !warehouses.data.some((w) => w.id === warehouseId) && (
              <option value={warehouseId}>{warehouseId}</option>
            )}
          </select>
        </label>
      </div>
      <p className="muted page-intro">
        {queue.status === 'ON_HOLD'
          ? 'Shipments whose Payment could not be captured, oldest first. They stay out of the Carrier’s hands.'
          : queue.status === 'PACKED'
            ? 'Packed Shipments, oldest first. Each goes to the Carrier by itself once its Payment is captured.'
            : `Shipments ${label}, oldest Order first. Open one to ${queue.status === 'AWAITING_PICK' ? 'pick' : 'pack'} it.`}
      </p>

      {warehouses.error && (
        <ErrorMessage
          error={warehouses.error}
          title="Couldn't load the Warehouses"
          retrying={warehouses.isFetching}
          onRetry={() => warehouses.refetch()}
        />
      )}
      {shipments.error ? (
        <ErrorMessage
          error={shipments.error}
          title="Couldn't load the Shipments"
          retrying={shipments.isFetching}
          onRetry={() => shipments.refetch()}
        />
      ) : (
        <>
          <div className="table-scroll">
            <table className="table" aria-label="Shipments" aria-busy={shipments.isPlaceholderData || undefined}>
              <thead>
                <tr>
                  <th scope="col">Order</th>
                  <th scope="col">Paid</th>
                  <th scope="col">Warehouse</th>
                  <th scope="col">Destination</th>
                  <th scope="col" className="num">
                    Units
                  </th>
                </tr>
              </thead>
              {page ? (
                <tbody>
                  {page.items.length === 0 && (
                    <tr>
                      <td colSpan={columns} className="table-empty">
                        {page.total === 0 ? `No Shipments are ${label}.` : 'There are no Shipments on this page.'}
                      </td>
                    </tr>
                  )}
                  {page.items.map((shipment) => (
                    <tr key={shipment.id}>
                      <th scope="row">
                        <Link
                          to={`/fulfillment/${shipment.id}`}
                          state={{ queue: location.search } satisfies FromQueueState}
                        >
                          <code>#{orderReference({ id: shipment.orderId })}</code>
                        </Link>
                      </th>
                      <td>
                        <time dateTime={shipment.createdAt}>{formatAgo(shipment.createdAt, now)}</time>
                      </td>
                      <td>
                        <code>{shipment.warehouseId}</code>
                      </td>
                      <td>{shipment.shippingAddress.country}</td>
                      <td className="num">{unitsOf(shipment)}</td>
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
                Page {page.page + 1} of {pages} · {page.total === 1 ? '1 Shipment' : `${page.total} Shipments`}
              </span>
              <Button
                size="sm"
                disabled={page.page === 0 || shipments.isPlaceholderData}
                onClick={() => show({ ...queue, page: page.page - 1 })}
              >
                Previous
              </Button>
              <Button
                size="sm"
                disabled={page.page + 1 >= pages || shipments.isPlaceholderData}
                onClick={() => show({ ...queue, page: page.page + 1 })}
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

/** The Warehouse this browser last chose, or every Warehouse; storage that can't be used only forgets it. */
function useRememberedWarehouse(): [string | undefined, (warehouseId: string | undefined) => void] {
  const [warehouseId, setWarehouseId] = useState<string | undefined>(() => {
    try {
      return localStorage.getItem(warehouseStorageKey) ?? undefined
    } catch {
      return undefined
    }
  })
  const remember = (next: string | undefined) => {
    setWarehouseId(next)
    try {
      if (next) localStorage.setItem(warehouseStorageKey, next)
      else localStorage.removeItem(warehouseStorageKey)
    } catch {
      // Not remembered: it still applies until the page is left.
    }
  }
  return [warehouseId, remember]
}
