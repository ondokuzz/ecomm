import { Link, useLocation, useSearchParams } from 'react-router'
import { useQueueCounts, useRmas } from '../api/rmas'
import { RmaStatusBadge } from '../components/RmaStatusBadge'
import { SendBackDeadline } from '../components/SendBackDeadline'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { formatAgo } from '../domain/ago'
import { orderReference, pageCount } from '../domain/order'
import {
  type Queue,
  type QueueTabId,
  queueOf,
  queueSearchOf,
  queueTabOf,
  queueTabs,
  rmaKindLabel,
  rmasPageSize,
  rmasQueryOf,
} from '../domain/rma'

/** What each tab says it lists. */
const intros: Record<QueueTabId, string> = {
  requested: 'Returns and warranty claims awaiting a decision, oldest first. Open one to approve or reject it.',
  'awaiting-units':
    'Approved RMAs whose units are on their way back, soonest Send-back deadline first. The returns Saga expires one whose units never arrive.',
  received:
    'RMAs whose units arrived, oldest request first. Open one to inspect it; the returns Saga resolves it then.',
  'on-hold': 'RMAs the returns Saga held, oldest request first: their Refund was declined.',
  closed: 'Rejected, expired, refunded, exchanged and closed RMAs, oldest request first.',
}

/** Where an RMA page's back link returns to: the queue's tab and page as they were. */
export interface FromQueueState {
  queue: string
}

/**
 * The RMA queue, a tab per stage of the RMA lifecycle, each with its count: requested RMAs oldest
 * first, so none waits forgotten; approved ones by Send-back deadline, flagged within 2 days;
 * received, on hold, and closed. The tab and page live in the URL (`?tab=&page=`).
 */
export function RmaQueuePage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const queue = queueOf(params)
  const tab = queueTabOf(queue.tab)
  const counts = useQueueCounts()
  const awaitingUnits = queue.tab === 'awaiting-units'
  // A tab of several statuses shows each RMA's.
  const showStatus = tab.statuses.length > 1
  const columns = showStatus ? 6 : 5
  const rmas = useRmas(rmasQueryOf(queue, rmasPageSize))
  const page = rmas.data
  const pages = page ? pageCount(page) : 1
  const show = (next: Queue) => setParams(new URLSearchParams(queueSearchOf(next)))
  // How long ago, as of the page's fetch: it moves on with each refetch.
  const now = new Date(rmas.dataUpdatedAt)

  return (
    <>
      <div className="page-heading">
        <h1>RMAs</h1>
      </div>
      <nav className="subnav" aria-label="RMA stages">
        {queueTabs.map(({ id, label }) => (
          <Link
            key={id}
            to={`/rmas${queueSearchOf({ tab: id, page: 0 })}`}
            className={id === queue.tab ? 'subnav-link active' : 'subnav-link'}
            aria-current={id === queue.tab ? 'page' : undefined}
          >
            {label}
            {counts[id] !== undefined && (
              <Badge tone={id === 'on-hold' && counts[id] > 0 ? 'danger' : 'neutral'}>{counts[id]}</Badge>
            )}
          </Link>
        ))}
      </nav>
      <p className="muted page-intro">{intros[queue.tab]}</p>

      {rmas.error ? (
        <ErrorMessage
          error={rmas.error}
          title="Couldn't load the RMAs"
          retrying={rmas.isFetching}
          onRetry={() => rmas.refetch()}
        />
      ) : (
        <>
          <div className="table-scroll">
            <table className="table" aria-label="RMAs" aria-busy={rmas.isPlaceholderData || undefined}>
              <thead>
                <tr>
                  <th scope="col">RMA</th>
                  <th scope="col">Kind</th>
                  <th scope="col">Order</th>
                  <th scope="col">Product</th>
                  {showStatus && <th scope="col">Status</th>}
                  <th scope="col">{awaitingUnits ? 'Send back by' : 'Requested'}</th>
                </tr>
              </thead>
              {page ? (
                <tbody>
                  {page.items.length === 0 && (
                    <tr>
                      <td colSpan={columns} className="table-empty">
                        {page.total === 0
                          ? `No RMAs are ${tab.label.toLowerCase()}.`
                          : 'There are no RMAs on this page.'}
                      </td>
                    </tr>
                  )}
                  {page.items.map((rma) => (
                    <tr key={rma.id}>
                      <th scope="row">
                        <Link to={`/rmas/${rma.id}`} state={{ queue: location.search } satisfies FromQueueState}>
                          <code>{rma.reference}</code>
                        </Link>
                      </th>
                      <td>{rmaKindLabel(rma.kind)}</td>
                      <td>
                        <code>#{orderReference({ id: rma.orderId })}</code>
                      </td>
                      <td>
                        {rma.productName ?? <code>{rma.variantId}</code>}
                        {rma.productName && (
                          <small className="hint">
                            <code>{rma.variantId}</code> × {rma.quantity}
                          </small>
                        )}
                      </td>
                      {showStatus && (
                        <td>
                          <RmaStatusBadge status={rma.status} />
                        </td>
                      )}
                      <td>
                        {awaitingUnits && rma.sendBackBy ? (
                          <SendBackDeadline sendBackBy={rma.sendBackBy} now={now} awaited />
                        ) : (
                          <time dateTime={rma.requestedAt}>{formatAgo(rma.requestedAt, now)}</time>
                        )}
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
                Page {page.page + 1} of {pages} · {page.total === 1 ? '1 RMA' : `${page.total} RMAs`}
              </span>
              <Button
                size="sm"
                disabled={page.page === 0 || rmas.isPlaceholderData}
                onClick={() => show({ ...queue, page: page.page - 1 })}
              >
                Previous
              </Button>
              <Button
                size="sm"
                disabled={page.page + 1 >= pages || rmas.isPlaceholderData}
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
