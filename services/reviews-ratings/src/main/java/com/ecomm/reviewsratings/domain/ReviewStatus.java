package com.ecomm.reviewsratings.domain;

/** Whether a review is shown: only a published one is, and only it counts in Rating summaries. */
public enum ReviewStatus {
  /** Shown and counted. A review from before moderation is published. */
  PUBLISHED,
  /** Its text tripped the screen, so it waits for Staff to publish or remove it. */
  HELD,
  /** Staff took it down, with a reason its author sees. */
  REMOVED
}
