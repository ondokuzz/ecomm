import { formatTime } from './order'

export type ReviewStatus = 'PUBLISHED' | 'HELD' | 'REMOVED'

export type ReportReason = 'SPAM' | 'OFFENSIVE' | 'OFF_TOPIC' | 'OTHER'

export type ModerationAction = 'PUBLISHED' | 'REPORTS_DISMISSED' | 'REMOVED'

/** A Customer's report of a review: open until Staff publish, remove or dismiss the reports. */
export interface Report {
  customerId: string
  reason: ReportReason
  note: string | null
  reportedAt: string
  resolved: boolean
}

/** One entry of a review's moderation history; `reason` is a removal's. */
export interface ModerationEntry {
  action: ModerationAction
  staffId: string
  at: string
  reason: string | null
}

/** A review as Staff moderate it, as Reviews & Ratings answers it. */
export interface ModeratedReview {
  id: string
  sku: string
  /** The author's Customer ID, which only Staff see. */
  customerId: string
  /** The author's display name, as anyone sees it. */
  author: string
  variantId: string
  rating: number
  title: string | null
  body: string
  createdAt: string
  editedAt: string | null
  status: ReviewStatus
  /** How many open reports give each reason, every reason counted from 0. */
  openReports: Record<ReportReason, number>
  /** Every report, open or resolved. */
  reports: Report[]
  /** Oldest first. */
  history: ModerationEntry[]
}

/** How many reviews a page of the moderation queue shows. */
export const moderationPageSize = 20

const statusLabels: Record<ReviewStatus, string> = { PUBLISHED: 'Published', HELD: 'Held', REMOVED: 'Removed' }

export function reviewStatusLabel(status: ReviewStatus): string {
  return statusLabels[status]
}

const reasonLabels: Record<ReportReason, string> = {
  SPAM: 'Spam',
  OFFENSIVE: 'Offensive',
  OFF_TOPIC: 'Off-topic',
  OTHER: 'Other',
}

export function reportReasonLabel(reason: ReportReason): string {
  return reasonLabels[reason]
}

/** A review's open reports as the queue shows them: "3 offensive · 1 spam", most first; "None" without any. */
export function openReportsLabel(openReports: Record<ReportReason, number>): string {
  const counted = (Object.keys(reasonLabels) as ReportReason[])
    .filter((reason) => openReports[reason] > 0)
    .sort((a, b) => openReports[b] - openReports[a])
  if (counted.length === 0) return 'None'
  return counted.map((reason) => `${openReports[reason]} ${reasonLabels[reason].toLowerCase()}`).join(' · ')
}

/** What Staff may do to a review, by the endpoint each is. */
export type Action = 'publish' | 'dismiss-reports' | 'remove'

/**
 * What Staff may do to the review as it is now: publish a held one, dismiss a published one's open
 * reports, and remove any that isn't removed already.
 */
export function actionsOf(review: Pick<ModeratedReview, 'status' | 'openReports'>): Action[] {
  if (review.status === 'REMOVED') return []
  if (review.status === 'HELD') return ['publish', 'remove']
  const reported = Object.values(review.openReports).some((count) => count > 0)
  return reported ? ['dismiss-reports', 'remove'] : ['remove']
}

const maxReason = 1000

/** A removal's body: a reason of up to 1,000 characters, trimmed, which the author is told. */
export function removalOf(text: string): { body: { reason: string } } | { error: string } {
  const reason = text.trim()
  if (!reason) return { error: 'Say why it is removed: its author is told.' }
  if (reason.length > maxReason) return { error: 'Keep it to 1,000 characters.' }
  return { body: { reason } }
}

const actionLabels: Record<ModerationAction, string> = {
  PUBLISHED: 'Published',
  REPORTS_DISMISSED: 'Reports dismissed',
  REMOVED: 'Removed',
}

export interface HistoryRow {
  action: string
  staffId: string
  at: string
  time: string
  reason: string | null
}

/** The review's moderation history, newest first, each action with its Staff member, time and reason. */
export function historyRowsOf(review: Pick<ModeratedReview, 'history'>, locale?: string): HistoryRow[] {
  return review.history
    .map((entry) => ({
      action: actionLabels[entry.action],
      staffId: entry.staffId,
      at: entry.at,
      time: formatTime(entry.at, locale),
      reason: entry.reason,
    }))
    .reverse()
}
