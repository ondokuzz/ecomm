package com.ecomm.reviewsratings.adapter.out.mongo;

import com.ecomm.reviewsratings.application.port.out.ReviewStore;
import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewContent;
import com.ecomm.reviewsratings.domain.ReviewPage;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Reviews in the {@code reviews} collection, keyed by review ID. A unique index on the SKU and the
 * Customer keeps one review per Customer and Product, and another serves a Product's newest first.
 */
@Component
class MongoReviewStore implements ReviewStore {

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
      reviews.insertOne(document(review));
      return true;
    } catch (MongoWriteException e) {
      if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
        return false;
      }
      throw e;
    }
  }

  @Override
  public void replace(Review review) {
    reviews.replaceOne(new Document("_id", review.id()), document(review));
  }

  @Override
  public void delete(String reviewId) {
    reviews.deleteOne(new Document("_id", reviewId));
  }

  @Override
  public ReviewPage page(String sku, int page, int size) {
    var filter = new Document("sku", sku);
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
  public Map<String, Map<Integer, Long>> ratingCounts(Collection<String> skus) {
    var pipeline =
        List.of(
            new Document("$match", new Document("sku", new Document("$in", List.copyOf(skus)))),
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
        .append("editedAt", review.editedAt().map(Date::from).orElse(null));
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
        Optional.ofNullable(document.getDate("editedAt")).map(Date::toInstant));
  }
}
