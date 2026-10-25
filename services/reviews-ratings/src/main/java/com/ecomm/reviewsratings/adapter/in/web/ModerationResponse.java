package com.ecomm.reviewsratings.adapter.in.web;

import com.ecomm.reviewsratings.domain.ModerationEntry;
import com.ecomm.reviewsratings.domain.Report;
import com.ecomm.reviewsratings.domain.Review;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A review as Staff moderate it: what anyone sees, with its author's Customer ID, its open reports
 * counted by reason, every report, open or resolved, and its moderation history, oldest first.
 */
record ModerationResponse(
    String id,
    String sku,
    String customerId,
    String author,
    String variantId,
    int rating,
    String title,
    String body,
    Instant createdAt,
    Instant editedAt,
    String status,
    Map<String, Long> openReports,
    List<ReportView> reports,
    List<HistoryEntry> history) {

  record ReportView(
      String customerId, String reason, String note, Instant reportedAt, boolean resolved) {

    static ReportView of(Report report) {
      return new ReportView(
          report.customerId(),
          report.reason().name(),
          report.note().orElse(null),
          report.reportedAt(),
          report.resolved());
    }
  }

  /** {@code reason} is null for an action that takes none. */
  record HistoryEntry(String action, String staffId, Instant at, String reason) {

    static HistoryEntry of(ModerationEntry entry) {
      return new HistoryEntry(
          entry.action().name(), entry.staffId(), entry.at(), entry.reason().orElse(null));
    }
  }

  static ModerationResponse of(Review review) {
    var content = review.content();
    var openReports = new LinkedHashMap<String, Long>();
    review.openReports().forEach((reason, count) -> openReports.put(reason.name(), count));
    return new ModerationResponse(
        review.id(),
        review.sku(),
        review.customerId(),
        review.author(),
        review.variantId(),
        content.rating(),
        content.title().orElse(null),
        content.body(),
        review.createdAt(),
        review.editedAt().orElse(null),
        review.status().name(),
        openReports,
        review.reports().stream().map(ReportView::of).toList(),
        review.history().stream().map(HistoryEntry::of).toList());
  }
}
