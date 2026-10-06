import { type FormEvent, useId, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import {
  useDeleteReview,
  useEditReview,
  useEligibility,
  usePostReview,
  useRatingSummary,
  useReviews,
} from '../api/reviews'
import { useAuthPending, useSignin } from '../auth/session'
import type { Variant } from '../domain/catalog'
import { pageCount } from '../domain/paging'
import {
  type RatingSummary,
  type Review,
  type ReviewDraft,
  type ReviewErrors,
  draftOf,
  emptyDraft,
  refusalMessage,
  reviewCountLabel,
  reviewLimits,
  reviewsPageSize,
  starShares,
  validateReview,
  variantBoughtLabel,
} from '../domain/reviews'
import { ErrorMessage } from './Status'
import { StarInput, Stars } from './StarRating'
import { Button } from './ui/Button'
import { Card } from './ui/Card'
import { Input } from './ui/Input'
import { ConfirmDialog } from './ui/ConfirmDialog'
import { Skeleton } from './ui/Skeleton'
import { useToast } from './ui/toasts'

/**
 * A Product's ratings and reviews: the summary with its split by star, what the signed-in Customer
 * may do (write, edit or delete their review, or why they can't), and the reviews, newest first.
 */
export function ProductReviews({ sku, variants }: { sku: string; variants: Variant[] }) {
  const summary = useRatingSummary(sku)
  return (
    <section className="reviews" aria-labelledby="reviews-heading">
      <h2 id="reviews-heading">Ratings &amp; reviews</h2>
      <div className="reviews-layout">
        <div className="reviews-aside">
          {summary.error ? (
            <ErrorMessage
              error={summary.error}
              title="We couldn't load the ratings"
              retrying={summary.isFetching}
              onRetry={() => summary.refetch()}
            />
          ) : summary.data ? (
            <Summary summary={summary.data} />
          ) : (
            <Skeleton height="9rem" radius="var(--radius-lg)" />
          )}
          <YourReview sku={sku} />
        </div>
        <ReviewList sku={sku} variants={variants} />
      </div>
    </section>
  )
}

function Summary({ summary }: { summary: RatingSummary }) {
  return (
    <Card className="rating-summary">
      <div className="rating-summary-head">
        <span className="rating-average">{summary.average === null ? '–' : summary.average.toFixed(1)}</span>
        <div>
          <Stars rating={summary.average} size={20} />
          <span className="muted">{reviewCountLabel(summary.count)}</span>
        </div>
      </div>
      <ul className="star-bars" aria-label="Reviews by rating">
        {starShares(summary).map(({ star, count, percent }) => (
          <li key={star}>
            <span className="star-bar-label">{star} ★</span>
            <span className="star-bar" aria-hidden="true">
              <span style={{ width: `${percent}%` }} />
            </span>
            <span className="star-bar-count">
              {count}
              <span className="visually-hidden">
                {' '}
                {count === 1 ? 'review' : 'reviews'} gave {star} {star === 1 ? 'star' : 'stars'}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

/** The signed-in Customer's side: the form, their own review, or why they can't write one. */
function YourReview({ sku }: { sku: string }) {
  const auth = useAuth()
  const pending = useAuthPending()
  const { signin } = useSignin()
  const eligibility = useEligibility(sku)
  const [editing, setEditing] = useState(false)

  if (pending) return null
  if (!auth.isAuthenticated) {
    return (
      <div className="signin-callout">
        <p>
          <strong>Bought this?</strong> Log in to review it.
        </p>
        <Button variant="primary" onClick={signin}>
          Log in to review
        </Button>
      </div>
    )
  }
  if (eligibility.error) {
    return (
      <ErrorMessage
        error={eligibility.error}
        title="We couldn't check whether you can review this"
        retrying={eligibility.isFetching}
        onRetry={() => eligibility.refetch()}
      />
    )
  }
  if (!eligibility.data) return <Skeleton height="6rem" radius="var(--radius-lg)" />

  const { eligible, reason, review } = eligibility.data
  if (eligible) return <WriteReview sku={sku} />
  if (review) {
    return editing ? (
      <EditReview sku={sku} review={review} onDone={() => setEditing(false)} />
    ) : (
      <OwnReview sku={sku} review={review} onEdit={() => setEditing(true)} />
    )
  }
  return (
    <p className="review-refusal muted" role="status">
      {refusalMessage(reason)}
    </p>
  )
}

function WriteReview({ sku }: { sku: string }) {
  const post = usePostReview(sku)
  const toast = useToast()
  return (
    <Card className="review-form-card">
      <h3>Write a review</h3>
      <ReviewForm
        initial={emptyDraft}
        submitLabel="Post review"
        submitting={post.isPending}
        error={post.error}
        onSubmit={(draft) => post.mutate(draft, { onSuccess: () => toast({ message: 'Thanks! Your review is up.' }) })}
      />
    </Card>
  )
}

function EditReview({ sku, review, onDone }: { sku: string; review: Review; onDone: () => void }) {
  const edit = useEditReview(sku)
  const toast = useToast()
  return (
    <Card className="review-form-card">
      <h3>Edit your review</h3>
      <ReviewForm
        initial={draftOf(review)}
        submitLabel="Save changes"
        submitting={edit.isPending}
        error={edit.error}
        onCancel={onDone}
        onSubmit={(draft) =>
          edit.mutate(
            { id: review.id, draft },
            {
              onSuccess: () => {
                toast({ message: 'Your review is updated.' })
                onDone()
              },
            },
          )
        }
      />
    </Card>
  )
}

function OwnReview({ sku, review, onEdit }: { sku: string; review: Review; onEdit: () => void }) {
  const remove = useDeleteReview(sku)
  const toast = useToast()
  const [confirming, setConfirming] = useState(false)
  return (
    <Card className="own-review">
      <h3>Your review</h3>
      <Stars rating={review.rating} />
      {review.title && <strong>{review.title}</strong>}
      <p className="review-body">{review.body}</p>
      <div className="own-review-actions">
        <Button size="sm" onClick={onEdit}>
          Edit
        </Button>
        <Button size="sm" variant="ghost" onClick={() => setConfirming(true)}>
          Delete
        </Button>
      </div>
      {remove.error && <ErrorMessage error={remove.error} />}
      <ConfirmDialog
        open={confirming}
        title="Delete your review?"
        confirmLabel="Delete review"
        confirming={remove.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={() =>
          remove.mutate(review.id, {
            onSuccess: () => toast({ message: 'Your review is deleted.' }),
            onSettled: () => setConfirming(false),
          })
        }
      >
        Its rating leaves the product's average too. You can write a new one afterwards.
      </ConfirmDialog>
    </Card>
  )
}

/**
 * The review's rating, title and body, checked before it is sent; each problem shows under its field
 * once the Customer has tried to send, and clears as they fix it.
 */
function ReviewForm({
  initial,
  submitLabel,
  submitting,
  error,
  onSubmit,
  onCancel,
}: {
  initial: ReviewDraft
  submitLabel: string
  submitting: boolean
  error: unknown
  onSubmit: (draft: ReviewDraft) => void
  onCancel?: () => void
}) {
  const [draft, setDraft] = useState(initial)
  const [tried, setTried] = useState(false)
  const id = useId()
  const errors: ReviewErrors = tried ? validateReview(draft) : {}
  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTried(true)
    if (Object.keys(validateReview(draft)).length === 0) onSubmit(draft)
  }
  const update = (change: Partial<ReviewDraft>) => setDraft((d) => ({ ...d, ...change }))

  return (
    <form className="review-form" onSubmit={submit} noValidate>
      <StarInput
        value={draft.rating}
        onChange={(rating) => update({ rating })}
        invalid={!!errors.rating}
        describedBy={errors.rating ? `${id}-rating` : undefined}
      />
      <FieldError id={`${id}-rating`} message={errors.rating} />
      <label className="field review-field">
        <span>
          Title <span className="muted">(optional)</span>
        </span>
        <Input
          value={draft.title}
          onChange={(e) => update({ title: e.target.value })}
          aria-invalid={errors.title ? true : undefined}
          aria-describedby={`${id}-title`}
        />
        <Counter id={`${id}-title`} length={draft.title.trim().length} max={reviewLimits.title} />
      </label>
      <FieldError message={errors.title} />
      <label className="field review-field">
        <span>Review</span>
        <textarea
          className="input textarea"
          rows={5}
          value={draft.body}
          onChange={(e) => update({ body: e.target.value })}
          aria-invalid={errors.body ? true : undefined}
          aria-describedby={`${id}-body`}
        />
        <Counter id={`${id}-body`} length={draft.body.trim().length} max={reviewLimits.body} />
      </label>
      <FieldError message={errors.body} />
      {error ? <ErrorMessage error={error} /> : null}
      <div className="review-form-actions">
        {onCancel && (
          <Button onClick={onCancel} disabled={submitting}>
            Cancel
          </Button>
        )}
        <Button type="submit" variant="primary" loading={submitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}

function Counter({ id, length, max }: { id: string; length: number; max: number }) {
  return (
    <span id={id} className={length > max ? 'char-count over' : 'char-count'}>
      {length.toLocaleString('en')} / {max.toLocaleString('en')}
    </span>
  )
}

function FieldError({ id, message }: { id?: string; message: string | undefined }) {
  if (!message) return null
  return (
    <span id={id} className="field-error" role="alert">
      {message}
    </span>
  )
}

function ReviewList({ sku, variants }: { sku: string; variants: Variant[] }) {
  const [page, setPage] = useState(0)
  const reviews = useReviews(sku, page)
  // Deleting the only review on the last page leaves it empty; step back to the one before.
  if (reviews.data && !reviews.isPlaceholderData && reviews.data.items.length === 0 && page > 0) setPage(page - 1)

  if (reviews.error) {
    return (
      <ErrorMessage
        error={reviews.error}
        title="We couldn't load the reviews"
        retrying={reviews.isFetching}
        onRetry={() => reviews.refetch()}
      />
    )
  }
  if (!reviews.data) {
    return (
      <div className="review-list" role="status">
        <span className="visually-hidden">Loading…</span>
        <Skeleton height="7rem" radius="var(--radius-lg)" />
        <Skeleton height="7rem" radius="var(--radius-lg)" />
      </div>
    )
  }
  const { items, total } = reviews.data
  if (total === 0) {
    return <p className="muted review-list">No one has reviewed this product yet.</p>
  }
  const pages = pageCount({ size: reviewsPageSize, total })
  return (
    <div className="review-list">
      <ul aria-label="Reviews" aria-busy={reviews.isPlaceholderData}>
        {items.map((review) => (
          <li key={review.id}>
            <ReviewItem review={review} variants={variants} />
          </li>
        ))}
      </ul>
      {pages > 1 && (
        <nav className="review-pager" aria-label="Review pages">
          <Button size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Newer
          </Button>
          <span className="muted">
            Page {page + 1} of {pages}
          </span>
          <Button size="sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>
            Older
          </Button>
        </nav>
      )}
    </div>
  )
}

const dateFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium' })

function ReviewItem({ review, variants }: { review: Review; variants: Variant[] }) {
  const bought = variantBoughtLabel(variants, review.variantId)
  return (
    <article className="review">
      <header className="review-head">
        <Stars rating={review.rating} />
        {review.title && <h3 className="review-title">{review.title}</h3>}
      </header>
      <p className="review-body">{review.body}</p>
      <footer className="review-meta muted">
        <span>{review.author}</span>
        <span>
          <time dateTime={review.createdAt}>{dateFormat.format(new Date(review.createdAt))}</time>
          {review.editedAt && ' · edited'}
        </span>
        {bought && <span>{bought}</span>}
      </footer>
    </article>
  )
}
