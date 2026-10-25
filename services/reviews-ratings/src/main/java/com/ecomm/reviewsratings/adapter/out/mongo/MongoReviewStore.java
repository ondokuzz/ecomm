package com.ecomm.reviewsratings.adapter.out.mongo;

import com.ecomm.reviewsratings.application.port.out.ReviewStore;
import com.ecomm.reviewsratings.domain.ModerationAction;
import com.ecomm.reviewsratings.domain.ModerationEntry;
import com.ecomm.reviewsratings.domain.Report;
import com.ecomm.reviewsratings.domain.ReportReason;
import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewContent;
import com.ecomm.reviewsratings.domain.ReviewPage;
import com.ecomm.reviewsratings.domain.ReviewStatus;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Reviews in the {@code reviews} collection, keyed by review ID, each with its reports and
 * moderation history. A unique index on the SKU and the Customer keeps one review per Customer and
 * Product, another serves a Product's newest first, and a partial one the moderation queue, oldest
 * first, from {@code awaitingModeration}, which each write keeps.
 */
@Component
class MongoReviewStore implements ReviewStore {

  /**
   * Matches a published review, and one from before moderation, which has no status and reads as
   * published.
   */
  private static final Document PUBLISHED =
      new Document("$in", Arrays.asList(ReviewStatus.PUBLISHED.name(), null));

  private final MongoCollection<Document> reviews;

  MongoReviewStore(MongoTemplate mongo) {
    this.reviews = mongo.getCollection("reviews");
    reviews.createIndex(
        Indexes.ascending("sku", "customerId"),
        new IndexOptions().name("oneReviewPerCustomer").unique(true));
    reviews.createIndex(
        Indexes.compoundIndex(
            Indexes.ascending("sku"), Indexes.descending("createdAt"), Indexes.descending("_id")),
        new IndexOptions().name("newestFirst"));
    reviews.createIndex(
        Indexes.ascending("createdAt", "_id"),
        new IndexOptions()
            .name("moderationQueue")
            .partialFilterExpression(new Document("awaitingModeration", true)));
  }

  @Override
  public Optional<Review> find(String reviewId) {
    return Optional.ofNullable(reviews.find(new Document("_id", reviewId)).first())
        .map(MongoReviewStore::review);
  }

  @Override
  public Optional<Review> find(String sku, String customerId) {
    return Optional.ofNullable(
            reviews.find(new Document("sku", sku).append("customerId", customerId)).first())
        .map(MongoReviewStore::review);
  }

  @Override
  public boolean add(Review review) {
    try {
      reviews.insertOne(document(review).append("version", 0L));
      return true;
    } catch (MongoWriteException e) {
      if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
        return false;
      }
      throw e;
    }
  }

  /**
   * Replaces the document only if its {@code version} is still the one read, and moves it on by
   * one. A review from before moderation has none, which the filter's {@code null} matches.
   */
  @Override
  public Optional<Review> update(String reviewId, UnaryOperator<Review> change) {
    while (true) {
      var stored = reviews.find(new Document("_id", reviewId)).first();
      if (stored == null) {
        return Optional.empty();
      }
      var review = review(stored);
      var changed = change.apply(review);
      if (changed == review) {
        return Optional.of(review);
      }
      var version = stored.get("version", Number.class);
      var kept =
          reviews.replaceOne(
              new Document("_id", reviewId).append("version", version),
              document(changed).append("version", version == null ? 1L : version.longValue() + 1));
      if (kept.getMatchedCount() == 1) {
        return Optional.of(changed);
      }
    }
  }

  @Override
  public void delete(String reviewId) {
    reviews.deleteOne(new Document("_id", reviewId));
  }

  @Override
  public ReviewPage page(String sku, int page, int size) {
    var filter = new Document("sku", sku).append("status", PUBLISHED);
    var items = new ArrayList<Review>();
    for (var document :
        reviews
            .find(filter)
            .sort(Sorts.descending("createdAt", "_id"))
            .skip(page * size)
            .limit(size)) {
      items.add(review(document));
    }
    return new ReviewPage(items, page, size, reviews.countDocuments(filter));
  }

  @Override
  public ReviewPage awaitingModeration(int page, int size) {
    var filter = new Document("awaitingModeration", true);
    var items = new ArrayList<Review>();
    for (var document :
        reviews
            .find(filter)
            .sort(Sorts.ascending("createdAt", "_id"))
            .skip(page * size)
            .limit(size)) {
      items.add(review(document));
    }
    return new ReviewPage(items, page, size, reviews.countDocuments(filter));
  }

  @Override
  public Map<String, Map<Integer, Long>> ratingCounts(Collection<String> skus) {
    var pipeline =
        List.of(
            new Document(
                "$match",
                new Document("sku", new Document("$in", List.copyOf(skus)))
                    .append("status", PUBLISHED)),
            new Document(
                "$group",
                new Document("_id", new Document("sku", "$sku").append("rating", "$rating"))
                    .append("count", new Document("$sum", 1L))));
    var counts = new HashMap<String, Map<Integer, Long>>();
    for (var group : reviews.aggregate(pipeline)) {
      var key = group.get("_id", Document.class);
      counts
          .computeIfAbsent(key.getString("sku"), sku -> new HashMap<>())
          .put(key.getInteger("rating"), group.getLong("count"));
    }
    return counts;
  }

  private static Document document(Review review) {
    var content = review.content();
    return new Document("_id", review.id())
        .append("sku", review.sku())
        .append("customerId", review.customerId())
        .append("author", review.author())
        .append("variantId", review.variantId())
        .append("rating", content.rating())
        .append("title", content.title().orElse(null))
        .append("body", content.body())
        .append("createdAt", Date.from(review.createdAt()))
        .append("editedAt", review.editedAt().map(Date::from).orElse(null))
        .append("status", review.status().name())
        .append("reports", review.reports().stream().map(MongoReviewStore::document).toList())
        .append("history", review.history().stream().map(MongoReviewStore::document).toList())
        .append("awaitingModeration", review.awaitsModeration());
  }

  private static Review review(Document document) {
    return new Review(
        document.getString("_id"),
        document.getString("sku"),
        document.getString("customerId"),
        document.getString("author"),
        document.getString("variantId"),
        ReviewContent.of(
            document.getInteger("rating"), document.getString("title"), document.getString("body")),
        document.getDate("createdAt").toInstant(),
        Optional.ofNullable(document.getDate("editedAt")).map(Date::toInstant),
        Optional.ofNullable(document.getString("status"))
            .map(ReviewStatus::valueOf)
            .orElse(ReviewStatus.PUBLISHED),
        document.getList("reports", Document.class, List.of()).stream()
            .map(MongoReviewStore::report)
            .toList(),
        document.getList("history", Document.class, List.of()).stream()
            .map(MongoReviewStore::entry)
            .toList());
  }

  private static Document document(ModerationEntry entry) {
    return new Document("action", entry.action().name())
        .append("staffId", entry.staffId())
        .append("at", Date.from(entry.at()))
        .append("reason", entry.reason().orElse(null));
  }

  private static ModerationEntry entry(Document document) {
    return new ModerationEntry(
        ModerationAction.valueOf(document.getString("action")),
        document.getString("staffId"),
        document.getDate("at").toInstant(),
        Optional.ofNullable(document.getString("reason")));
  }

  private static Document document(Report report) {
    return new Document("customerId", report.customerId())
        .append("reason", report.reason().name())
        .append("note", report.note().orElse(null))
        .append("reportedAt", Date.from(report.reportedAt()))
        .append("resolved", report.resolved());
  }

  private static Report report(Document document) {
    return new Report(
        document.getString("customerId"),
        ReportReason.valueOf(document.getString("reason")),
        Optional.ofNullable(document.getString("note")),
        document.getDate("reportedAt").toInstant(),
        document.getBoolean("resolved"));
  }
}
