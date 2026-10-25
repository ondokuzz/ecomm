import { Link, useLocation, useSearchParams } from 'react-router'
import { useProducts } from '../api/products'
import { useModerationQueue } from '../api/reviews'
import { ReviewStatusBadge } from '../components/ReviewStatusBadge'
import { ErrorMessage, SkeletonRows } from '../components/Status'
import { Button } from '../components/ui/Button'
import { formatAgo } from '../domain/ago'
import { pageCount } from '../domain/order'
import { openReportsLabel } from '../domain/review'

/** Where a review's page links back to: the queue's page as it was. */
export interface FromReviewQueueState {
  queue: string
}

const columns = 6

/**
 * The Review moderation queue: held reviews, which the screen kept back, and published ones with
 * open reports, oldest first, each with its open reports by reason. The page lives in the URL
 * (`?page=`).
 */
export function ReviewQueuePage() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const pageNumber = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0)
  const queue = useModerationQueue(pageNumber)
  // Every Product's name, from one request, for the rows' SKUs.
  const products = useProducts(undefined)
  const nameOf = (sku: string) => products.data?.find((product) => product.sku === sku)?.name
  const page = queue.data
  const pages = page ? pageCount(page) : 1
  const show = (next: number) =>
    setParams(next === 0 ? new URLSearchParams() : new URLSearchParams({ page: String(next) }))
  const now = new Date(queue.dataUpdatedAt)

  return (
    <>
      <div className="page-heading">
        <h1>Review moderation</h1>
      </div>
      <p className="muted page-intro">
        Held reviews, kept back by the word screen, and published reviews with open reports, oldest first. Open one to
        publish it, dismiss its reports or remove it.
      </p>

      {queue.error ? (
        <ErrorMessage
          error={queue.error}
          title="Couldn't load the moderation queue"
          retrying={queue.isFetching}
          onRetry={() => queue.refetch()}
        />
      ) : (
        <>
          <div className="table-scroll">
            <table className="table" aria-label="Reviews to moderate" aria-busy={queue.isPlaceholderData || undefined}>
              <thead>
                <tr>
                  <th scope="col">Review</th>
                  <th scope="col">Product</th>
                  <th scope="col">Rating</th>
                  <th scope="col">Status</th>
                  <th scope="col">Open reports</th>
                  <th scope="col">Posted</th>
                </tr>
              </thead>
              {page ? (
                <tbody>
                  {page.items.length === 0 && (
                    <tr>
                      <td colSpan={columns} className="table-empty">
                        {page.total === 0 ? 'No reviews await moderation.' : 'There are no reviews on this page.'}
                      </td>
                    </tr>
                  )}
                  {page.items.map((review) => {
                    const productName = nameOf(review.sku)
                    return (
                      <tr key={review.id}>
                        <th scope="row">
                          <Link
                            to={`/reviews/${review.id}`}
                            state={{ queue: location.search } satisfies FromReviewQueueState}
                          >
                            {review.title ?? excerpt(review.body)}
                          </Link>
                          <small className="hint">by {review.author}</small>
                        </th>
                        <td>
                          {productName ?? <code>{review.sku}</code>}
                          {productName && (
                            <small className="hint">
                              <code>{review.sku}</code>
                            </small>
                          )}
                        </td>
                        <td>{review.rating} ★</td>
                        <td>
                          <ReviewStatusBadge status={review.status} />
                        </td>
                        <td>{openReportsLabel(review.openReports)}</td>
                        <td>
                          <time dateTime={review.createdAt}>{formatAgo(review.createdAt, now)}</time>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              ) : (
                <SkeletonRows rows={5} columns={columns} />
              )}
            </table>
          </div>
          {page && (
            <nav className="pager" aria-label="Pages">
              <span className="muted">
                Page {page.page + 1} of {pages} · {page.total === 1 ? '1 review' : `${page.total} reviews`}
              </span>
              <Button
                size="sm"
                disabled={page.page === 0 || queue.isPlaceholderData}
                onClick={() => show(page.page - 1)}
              >
                Previous
              </Button>
              <Button
                size="sm"
                disabled={page.page + 1 >= pages || queue.isPlaceholderData}
                onClick={() => show(page.page + 1)}
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

/** The start of a review without a title, as its link. */
function excerpt(body: string): string {
  return body.length > 60 ? `${body.slice(0, 60).trimEnd()}…` : body
}
