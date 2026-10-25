package com.ecomm.reviewsratings.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A Customer's review of a Product: what they said, the Variant they bought, and their display name
 * as it was when they posted it. A Customer has at most one per Product. {@code editedAt} is empty
 * until they first edit it. Only a {@link ReviewStatus#PUBLISHED} one is shown. Other Customers'
 * reports of it are kept with it, at most one each, and so is its moderation history, oldest first.
 */
public record Review(
    String id,
    String sku,
    String customerId,
    String author,
    String variantId,
    ReviewContent content,
    Instant createdAt,
    Optional<Instant> editedAt,
    ReviewStatus status,
    List<Report> reports,
    List<ModerationEntry> history) {

  /** The most a removal's reason may have. */
  public static final int MAX_REMOVAL_REASON = 1_000;

  public Review {
    reports = List.copyOf(reports);
    history = List.copyOf(history);
  }

  /** A new review, held if the screen {@code held} it and published otherwise, with no reports. */
  public static Review posted(
      String id,
      String sku,
      String customerId,
      String author,
      String variantId,
      ReviewContent content,
      Instant at,
      boolean held) {
    return new Review(
        id,
        sku,
        customerId,
        author,
        variantId,
        content,
        at,
        Optional.empty(),
        statusOf(held),
        List.of(),
        List.of());
  }

  /**
   * The same review saying {@code content} instead, edited {@code at}: held if the screen {@code
   * held} it, and published otherwise.
   *
   * @throws ReviewRefusedException if Staff removed it
   */
  public Review edit(ReviewContent content, Instant at, boolean held) {
    if (status == ReviewStatus.REMOVED) {
      throw new ReviewRefusedException(RefusalReason.REVIEW_REMOVED);
    }
    return new Review(
        id,
        sku,
        customerId,
        author,
        variantId,
        content,
        createdAt,
        Optional.of(at),
        statusOf(held),
        reports,
        history);
  }

  /**
   * The review with the Customer's report of it; this same review if they have reported it already.
   *
   * @throws ReviewRefusedException if the review is theirs
   * @throws ReviewNotFoundException if it isn't published
   */
  public Review report(String reporterId, ReportReason reason, Optional<String> note, Instant at) {
    if (reporterId.equals(customerId)) {
      throw new ReviewRefusedException(RefusalReason.OWN_REVIEW);
    }
    if (status != ReviewStatus.PUBLISHED) {
      throw new ReviewNotFoundException(id);
    }
    if (reportOf(reporterId).isPresent()) {
      return this;
    }
    var reported = new ArrayList<>(reports);
    reported.add(new Report(reporterId, reason, note, at, false));
    return new Review(
        id,
        sku,
        customerId,
        author,
        variantId,
        content,
        createdAt,
        editedAt,
        status,
        reported,
        history);
  }

  /**
   * The held review published by a Staff member, its reports resolved.
   *
   * @throws ModerationRefusedException unless it is held
   */
  public Review publish(String staffId, Instant at) {
    if (status != ReviewStatus.HELD) {
      throw new ModerationRefusedException(ModerationRefusal.NOT_HELD);
    }
    return moderated(
        ReviewStatus.PUBLISHED,
        new ModerationEntry(ModerationAction.PUBLISHED, staffId, at, Optional.empty()));
  }

  /**
   * The published review with its reports resolved by a Staff member, so it leaves the queue.
   *
   * @throws ModerationRefusedException unless it is published with a report open
   */
  public Review dismissReports(String staffId, Instant at) {
    if (status != ReviewStatus.PUBLISHED || !awaitsModeration()) {
      throw new ModerationRefusedException(ModerationRefusal.NO_OPEN_REPORTS);
    }
    return moderated(
        ReviewStatus.PUBLISHED,
        new ModerationEntry(ModerationAction.REPORTS_DISMISSED, staffId, at, Optional.empty()));
  }

  /**
   * The review taken down by a Staff member for {@code reason}, 1 to 1,000 characters once trimmed,
   * which its author sees; its reports are resolved.
   *
   * @throws InvalidReviewException if the reason is missing, blank or too long
   * @throws ModerationRefusedException if it is removed already
   */
  public Review remove(String staffId, String reason, Instant at) {
    var trimmed = reason == null ? "" : reason.strip();
    if (trimmed.isEmpty() || trimmed.length() > MAX_REMOVAL_REASON) {
      throw new InvalidReviewException(
          String.format(
              Locale.ROOT,
              "A reason of 1 to %,d characters is required to remove a review.",
              MAX_REMOVAL_REASON));
    }
    if (status == ReviewStatus.REMOVED) {
      throw new ModerationRefusedException(ModerationRefusal.ALREADY_REMOVED);
    }
    return moderated(
        ReviewStatus.REMOVED,
        new ModerationEntry(ModerationAction.REMOVED, staffId, at, Optional.of(trimmed)));
  }

  /** Why Staff removed it, while it is removed. */
  public Optional<String> removalReason() {
    if (status != ReviewStatus.REMOVED) {
      return Optional.empty();
    }
    return history.reversed().stream()
        .filter(e -> e.action() == ModerationAction.REMOVED)
        .findFirst()
        .flatMap(ModerationEntry::reason);
  }

  public Optional<Report> reportOf(String reporterId) {
    return reports.stream().filter(r -> r.customerId().equals(reporterId)).findFirst();
  }

  /** How many of its reports are still open, for each reason, every reason counted from 0. */
  public Map<ReportReason, Long> openReports() {
    var counts = new EnumMap<ReportReason, Long>(ReportReason.class);
    for (var reason : ReportReason.values()) {
      counts.put(reason, 0L);
    }
    reports.stream()
        .filter(r -> !r.resolved())
        .forEach(r -> counts.merge(r.reason(), 1L, Long::sum));
    return Collections.unmodifiableMap(counts);
  }

  /** Whether it is in the moderation queue: held, or published with a report still open. */
  public boolean awaitsModeration() {
    return status == ReviewStatus.HELD
        || (status == ReviewStatus.PUBLISHED && reports.stream().anyMatch(r -> !r.resolved()));
  }

  /** In {@code next} status with every report resolved, {@code entry} added to its history. */
  private Review moderated(ReviewStatus next, ModerationEntry entry) {
    var moderatedHistory = new ArrayList<>(history);
    moderatedHistory.add(entry);
    return new Review(
        id,
        sku,
        customerId,
        author,
        variantId,
        content,
        createdAt,
        editedAt,
        next,
        reports.stream().map(Report::resolve).toList(),
        moderatedHistory);
  }

  private static ReviewStatus statusOf(boolean held) {
    return held ? ReviewStatus.HELD : ReviewStatus.PUBLISHED;
  }
}
