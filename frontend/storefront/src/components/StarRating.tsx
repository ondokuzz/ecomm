import { useId } from 'react'
import { type RatingSummary, type StarFill, ratingLabel, reviewCountLabel, starFills } from '../domain/reviews'

const starPath = 'M12 2.8l2.8 5.8 6.3.9-4.6 4.4 1.1 6.3L12 17.2l-5.6 3 1.1-6.3-4.6-4.4 6.3-.9z'

function Star({ fill, size }: { fill: StarFill; size: number }) {
  return (
    <span className={`star star-${fill}`} style={{ width: size, height: size }}>
      <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
        <path d={starPath} />
      </svg>
      {fill !== 'empty' && (
        <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true" className="star-fill">
          <path d={starPath} />
        </svg>
      )}
    </span>
  )
}

/** A rating as five stars, to the nearest half; screen readers hear it as "Rated 4.5 out of 5". */
export function Stars({ rating, size = 16 }: { rating: number | null; size?: number }) {
  return (
    <span className="stars" role="img" aria-label={ratingLabel(rating)}>
      {starFills(rating).map((fill, i) => (
        <Star key={i} fill={fill} size={size} />
      ))}
    </span>
  )
}

/**
 * A Rating summary in a line: the stars, the average and how many reviews, as on a Product card or
 * under a Product's name; nothing until the Product has a review.
 */
export function SummaryStars({ summary, size = 14 }: { summary: RatingSummary | undefined; size?: number }) {
  if (!summary || summary.average === null) return null
  return (
    <span className="summary-stars">
      <Stars rating={summary.average} size={size} />
      <span>
        <strong>{summary.average.toFixed(1)}</strong> · {reviewCountLabel(summary.count)}
      </span>
    </span>
  )
}

/** Picks a rating from 1 to 5 as a group of radio buttons drawn as stars; arrow keys move along it. */
export function StarInput({
  value,
  onChange,
  invalid,
  describedBy,
}: {
  value: number | null
  onChange: (rating: number) => void
  invalid?: boolean
  describedBy?: string
}) {
  const name = useId()
  return (
    <fieldset className="star-input" aria-invalid={invalid || undefined} aria-describedby={describedBy}>
      <legend>Your rating</legend>
      <div className="star-input-stars">
        {[1, 2, 3, 4, 5].map((star) => (
          <label key={star} className={value !== null && star <= value ? 'chosen' : undefined}>
            <input
              type="radio"
              name={name}
              value={star}
              checked={value === star}
              onChange={() => onChange(star)}
              className="visually-hidden"
            />
            <Star fill={value !== null && star <= value ? 'full' : 'empty'} size={28} />
            <span className="visually-hidden">
              {star} {star === 1 ? 'star' : 'stars'}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  )
}
