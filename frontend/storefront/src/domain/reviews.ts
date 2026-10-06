import type { Variant } from './catalog'

/** A Customer's review of a Product, as Reviews & Ratings answers it. */
export interface Review {
  id: string
  sku: string
  /** The author's given name and family name's initial, as they were when they posted it. */
  author: string
  /** The Variant they bought. */
  variantId: string
  rating: number
  title: string | null
  body: string
  createdAt: string
  /** When they last edited it; null if they never have. */
  editedAt: string | null
}

export type Star = '1' | '2' | '3' | '4' | '5'

/** A Product's ratings: how many, their average to one decimal (null without any), and how many gave each star. */
export interface RatingSummary {
  sku: string
  count: number
  average: number | null
  perStar: Record<Star, number>
}

/** Why a Customer may not review a Product, as Reviews & Ratings' `reason` says. */
export type RefusalReason = 'notPurchased' | 'alreadyReviewed'

/** Whether the signed-in Customer may review a Product; if not, why, and their review if they have one. */
export interface Eligibility {
  eligible: boolean
  reason: RefusalReason | null
  review: Review | null
}

/** How many reviews a page of a Product's reviews shows. */
export const reviewsPageSize = 5

export type StarFill = 'full' | 'half' | 'empty'

/** The five stars that show a rating, to the nearest half star; all empty without one. */
export function starFills(rating: number | null): StarFill[] {
  const halves = rating === null ? 0 : Math.round(rating * 2)
  return [1, 2, 3, 4, 5].map((star) => (halves >= star * 2 ? 'full' : halves === star * 2 - 1 ? 'half' : 'empty'))
}

/** What the stars say, for screen readers and tooltips. */
export function ratingLabel(average: number | null): string {
  return average === null ? 'Not rated yet' : `Rated ${average.toFixed(1)} out of 5`
}

export function reviewCountLabel(count: number): string {
  if (count === 0) return 'No reviews yet'
  return `${count.toLocaleString('en')} ${count === 1 ? 'review' : 'reviews'}`
}

export interface StarShare {
  star: number
  count: number
  /** Its share of all the reviews, as a whole percent. */
  percent: number
}

/** Each star from five down to one, with how many gave it and their share, for the summary's bars. */
export function starShares(summary: RatingSummary): StarShare[] {
  return ([5, 4, 3, 2, 1] as const).map((star) => {
    const count = summary.perStar[String(star) as Star]
    return { star, count, percent: summary.count === 0 ? 0 : Math.round((count / summary.count) * 100) }
  })
}

/** The most a review's title and body may hold, as Reviews & Ratings checks too. */
export const reviewLimits = { title: 120, body: 2000 }

/** The review form as the Customer fills it in; no rating until they pick one. */
export interface ReviewDraft {
  rating: number | null
  title: string
  body: string
}

export const emptyDraft: ReviewDraft = { rating: null, title: '', body: '' }

export type ReviewErrors = Partial<Record<keyof ReviewDraft, string>>

/** What is wrong with the draft, field by field, before it is sent; empty when nothing is. */
export function validateReview(draft: ReviewDraft): ReviewErrors {
  const errors: ReviewErrors = {}
  if (draft.rating === null || !Number.isInteger(draft.rating) || draft.rating < 1 || draft.rating > 5) {
    errors.rating = 'Choose a rating from 1 to 5 stars.'
  }
  const title = draft.title.trim()
  if (title.length > reviewLimits.title) {
    errors.title = `Keep the title to ${reviewLimits.title} characters (it has ${title.length}).`
  }
  const body = draft.body.trim()
  if (!body) {
    errors.body = 'Tell other Customers what you think.'
  } else if (body.length > reviewLimits.body) {
    errors.body = `Keep the review to ${reviewLimits.body.toLocaleString('en')} characters (it has ${body.length.toLocaleString('en')}).`
  }
  return errors
}

/** What a valid draft sends: trimmed, with a blank title as none. */
export function reviewPayload(draft: ReviewDraft): { rating: number; title: string | null; body: string } {
  return { rating: draft.rating ?? 0, title: draft.title.trim() || null, body: draft.body.trim() }
}

export function draftOf(review: Review): ReviewDraft {
  return { rating: review.rating, title: review.title ?? '', body: review.body }
}

const refusals: Record<RefusalReason, string> = {
  notPurchased: 'Only Customers who have bought this product can review it.',
  alreadyReviewed: "You've already reviewed this product.",
}

/** Why the Customer can't review, from Reviews & Ratings' `reason`. */
export function refusalMessage(reason: string | null): string {
  return reason && Object.hasOwn(refusals, reason)
    ? refusals[reason as RefusalReason]
    : "You can't review this product."
}

/** Which Variant the reviewer bought, by its axis values; nothing when the Product has none, or the Variant is gone. */
export function variantBoughtLabel(
  variants: Pick<Variant, 'id' | 'axisValues'>[],
  variantId: string,
): string | undefined {
  const values = Object.values(variants.find((v) => v.id === variantId)?.axisValues ?? {})
  return values.length > 0 ? `Bought: ${values.join(' · ')}` : undefined
}
