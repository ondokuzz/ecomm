import { type FormEvent, useId, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { isNotFound } from '../api/failure'
import { ApiError } from '../api/http'
import { useProduct } from '../api/products'
import { useModerate, useModeratedReview } from '../api/reviews'
import { NotFound } from '../components/NotFound'
import { ReviewStatusBadge } from '../components/ReviewStatusBadge'
import { ErrorMessage, FieldError } from '../components/Status'
import { Button } from '../components/ui/Button'
import { Icon } from '../components/ui/Icon'
import { formatTime } from '../domain/order'
import {
  type ModeratedReview,
  actionsOf,
  historyRowsOf,
  openReportsLabel,
  removalOf,
  reportReasonLabel,
} from '../domain/review'
import type { FromReviewQueueState } from './ReviewQueuePage'

/**
 * `/reviews/:id`: one review, whatever its status, with its Product, its author, what it says, its
 * reports and its moderation history. Staff publish a held one, dismiss a published one's open
 * reports, or remove either with a reason its author is told.
 */
export function ReviewPage() {
  const { id } = useParams()
  const review = useModeratedReview(id!)

  if (isNotFound(review.error)) return <NotFound what="review" />
  if (review.error) {
    return (
      <>
        <BackLink />
        <ErrorMessage
          error={review.error}
          title="Couldn't load the review"
          retrying={review.isFetching}
          onRetry={() => review.refetch()}
        />
      </>
    )
  }
  if (!review.data) {
    return (
      <div aria-busy="true">
        <BackLink />
        <span className="skeleton heading-skeleton" />
        <span className="visually-hidden">Loading…</span>
      </div>
    )
  }
  return <ReviewDetail review={review.data} />
}

/** Back to the queue, on the page the review was opened from. */
function BackLink() {
  const queue = (useLocation().state as FromReviewQueueState | null)?.queue ?? ''
  return (
    <Link to={`/reviews${queue}`} className="back-link">
      <Icon name="arrowLeft" size={16} />
      Review moderation
    </Link>
  )
}

function ReviewDetail({ review }: { review: ModeratedReview }) {
  const product = useProduct(review.sku)
  const history = historyRowsOf(review)
  const actions = actionsOf(review)
  return (
    <>
      <BackLink />
      <div className="page-heading">
        <h1>Review by {review.author}</h1>
        <ReviewStatusBadge status={review.status} />
      </div>

      <section className="panel" aria-labelledby="review-facts">
        <h2 id="review-facts" className="visually-hidden">
          About this review
        </h2>
        <dl className="facts">
          <div>
            <dt>Product</dt>
            <dd>
              <Link to={`/products/${encodeURIComponent(review.sku)}`}>{product.data?.name ?? review.sku}</Link>
            </dd>
          </div>
          <div>
            <dt>Variant</dt>
            <dd>
              <code>{review.variantId}</code>
            </dd>
          </div>
          <div>
            <dt>Author</dt>
            <dd>
              {review.author}
              <small className="hint">
                <code>{review.customerId}</code>
              </small>
            </dd>
          </div>
          <div>
            <dt>Rating</dt>
            <dd>{review.rating} ★</dd>
          </div>
          <div>
            <dt>Posted</dt>
            <dd>
              <time dateTime={review.createdAt}>{formatTime(review.createdAt)}</time>
            </dd>
          </div>
          {review.editedAt && (
            <div>
              <dt>Edited</dt>
              <dd>
                <time dateTime={review.editedAt}>{formatTime(review.editedAt)}</time>
              </dd>
            </div>
          )}
          <div>
            <dt>Open reports</dt>
            <dd>{openReportsLabel(review.openReports)}</dd>
          </div>
        </dl>
        <h3 className="facts-heading">{review.title ?? 'The review'}</h3>
        <blockquote className="customer-reason">{review.body}</blockquote>
      </section>

      {actions.length > 0 && <Moderate review={review} />}

      <section className="panel" aria-labelledby="review-reports">
        <h2 id="review-reports">Reports</h2>
        {review.reports.length === 0 ? (
          <p className="muted">No Customer has reported it.</p>
        ) : (
          <div className="table-scroll">
            <table className="table" aria-labelledby="review-reports">
              <thead>
                <tr>
                  <th scope="col">Reason</th>
                  <th scope="col">Note</th>
                  <th scope="col">By</th>
                  <th scope="col">When</th>
                  <th scope="col">Open</th>
                </tr>
              </thead>
              <tbody>
                {review.reports.map((report) => (
                  <tr key={report.customerId}>
                    <th scope="row">{reportReasonLabel(report.reason)}</th>
                    <td>{report.note ?? <span className="muted">—</span>}</td>
                    <td>
                      <code>{report.customerId}</code>
                    </td>
                    <td>
                      <time dateTime={report.reportedAt}>{formatTime(report.reportedAt)}</time>
                    </td>
                    <td>{report.resolved ? 'Resolved' : 'Open'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel" aria-labelledby="review-history">
        <h2 id="review-history">Moderation history</h2>
        {history.length === 0 ? (
          <p className="muted">No Staff member has moderated it yet.</p>
        ) : (
          <div className="table-scroll">
            <table className="table" aria-labelledby="review-history">
              <thead>
                <tr>
                  <th scope="col">Action</th>
                  <th scope="col">When</th>
                  <th scope="col">By</th>
                  <th scope="col">Reason</th>
                </tr>
              </thead>
              <tbody>
                {history.map((row, i) => (
                  <tr key={i}>
                    <th scope="row">{row.action}</th>
                    <td>
                      <time dateTime={row.at}>{row.time}</time>
                    </td>
                    <td>
                      <code>{row.staffId}</code>
                    </td>
                    <td>{row.reason ?? <span className="muted">—</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  )
}

const movedOnReasons = new Set(['notHeld', 'noOpenReports', 'alreadyRemoved'])

/**
 * The actions the review's status allows: publish a held one, dismiss a published one's open
 * reports, and remove either, with a reason its author is told.
 */
function Moderate({ review }: { review: ModeratedReview }) {
  const id = useId()
  const moderate = useModerate(review.id)
  const actions = actionsOf(review)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string>()

  const remove = (event: FormEvent) => {
    event.preventDefault()
    const removal = removalOf(reason)
    if ('error' in removal) {
      setError(removal.error)
      return
    }
    setError(undefined)
    moderate.mutate({ action: 'remove', body: removal.body })
  }
  const pending = (action: string) => moderate.isPending && moderate.variables?.action === action
  const movedOn =
    moderate.error instanceof ApiError && movedOnReasons.has(String(moderate.error.problem.reason))
      ? 'Another Staff member moderated it first. It shows as they left it.'
      : undefined

  return (
    <section className="panel" aria-labelledby={`${id}-heading`}>
      <h2 id={`${id}-heading`}>Moderate</h2>
      {moderate.error && <ErrorMessage error={moderate.error} title="Couldn't moderate the review" message={movedOn} />}
      <div className="decide">
        {(actions.includes('publish') || actions.includes('dismiss-reports')) && (
          <div>
            <p className="muted">
              {actions.includes('publish')
                ? 'A false alarm? Publish it: it is shown, and counts in its Product’s Rating summary.'
                : 'Nothing wrong with it? Dismiss its reports: it stays up and leaves the queue.'}
            </p>
            <div className="form-actions">
              {actions.includes('publish') ? (
                <Button
                  variant="primary"
                  loading={pending('publish')}
                  disabled={moderate.isPending}
                  onClick={() => moderate.mutate({ action: 'publish' })}
                >
                  <Icon name="check" size={16} />
                  Publish
                </Button>
              ) : (
                <Button
                  variant="primary"
                  loading={pending('dismiss-reports')}
                  disabled={moderate.isPending}
                  onClick={() => moderate.mutate({ action: 'dismiss-reports' })}
                >
                  <Icon name="check" size={16} />
                  Dismiss reports
                </Button>
              )}
            </div>
          </div>
        )}
        <form onSubmit={remove} aria-label="Remove">
          <label className="field" htmlFor={`${id}-reason`}>
            Reason
          </label>
          <textarea
            id={`${id}-reason`}
            className="input"
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${id}-reason-error` : `${id}-reason-hint`}
          />
          <span id={`${id}-reason-hint`} className="hint">
            Its author is told why.
          </span>
          <FieldError id={`${id}-reason-error`} message={error} />
          <div className="form-actions">
            <Button type="submit" variant="danger" loading={pending('remove')} disabled={moderate.isPending}>
              <Icon name="close" size={16} />
              Remove
            </Button>
          </div>
        </form>
      </div>
    </section>
  )
}
