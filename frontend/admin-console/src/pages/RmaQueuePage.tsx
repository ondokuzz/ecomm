import { Link, useLocation, useSearchParams } from 'react-router'
import { useRmas } from '../api/rmas'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Button } from '../components/ui/Button'
import { formatAgo } from '../domain/ago'
import { orderReference, pageCount } from '../domain/order'
import {
  type Queue,
  queueOf,
  queueSearchOf,
  rmaKindLabel,
  rmaStatusLabel,
  queueStatuses,
  rmasPageSize,
  rmasQueryOf,
} from '../domain/rma'

const columns = 5

/** Where an RMA page's back link returns to: the queue's tab and page as they were. */
export interface FromQueueState {
  queue: string
}

/**
 * The RMA approval queue: requested RMAs, oldest first, so none waits forgotten, with tabs for the
 * approved and the rejected ones. The tab and page live in the URL (`?status=&page=`).
 */
export function RmaQueuePage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const queue = queueOf(params)
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
      <nav className="subnav" aria-label="RMA statuses">
        {queueStatuses.map((status) => (
          <Link
            key={status}
            to={`/rmas${queueSearchOf({ status, page: 0 })}`}
            className={status === queue.status ? 'subnav-link active' : 'subnav-link'}
            aria-current={status === queue.status ? 'page' : undefined}
          >
            {rmaStatusLabel(status)}
          </Link>
        ))}
      </nav>
      <p className="muted page-intro">
        {queue.status === 'REQUESTED'
          ? 'Returns and warranty claims awaiting a decision, oldest first. Open one to approve or reject it.'
          : `${rmaStatusLabel(queue.status)} RMAs, oldest request first. A decision is final.`}
      </p>

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
                  <th scope="col">Requested</th>
                </tr>
              </thead>
              {page ? (
                <tbody>
                  {page.items.length === 0 && (
                    <tr>
                      <td colSpan={columns} className="table-empty">
                        {page.total === 0
                          ? `No RMAs are ${rmaStatusLabel(queue.status).toLowerCase()}.`
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
                      <td>
                        <time dateTime={rma.requestedAt}>{formatAgo(rma.requestedAt, now)}</time>
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
