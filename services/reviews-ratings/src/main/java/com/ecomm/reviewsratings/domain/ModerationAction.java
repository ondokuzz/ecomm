package com.ecomm.reviewsratings.domain;

/** What a Staff member did to a review. */
public enum ModerationAction {
  /** Published a held review, resolving its reports. */
  PUBLISHED,
  /** Resolved a published review's reports, leaving it up. */
  REPORTS_DISMISSED,
  /** Took a review down, with a reason, resolving its reports. */
  REMOVED
}
