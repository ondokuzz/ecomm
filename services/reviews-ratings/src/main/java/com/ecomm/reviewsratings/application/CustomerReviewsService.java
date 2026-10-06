package com.ecomm.reviewsratings.application;

import com.ecomm.reviewsratings.application.port.in.CustomerReviewsUseCase;
import com.ecomm.reviewsratings.application.port.out.OrderStore;
import com.ecomm.reviewsratings.application.port.out.ProductStore;
import com.ecomm.reviewsratings.application.port.out.ReviewStore;
import com.ecomm.reviewsratings.application.port.out.TimeSource;
import com.ecomm.reviewsratings.domain.Eligibility;
import com.ecomm.reviewsratings.domain.Order;
import com.ecomm.reviewsratings.domain.ProductVariants;
import com.ecomm.reviewsratings.domain.RefusalReason;
import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewContent;
import com.ecomm.reviewsratings.domain.ReviewNotFoundException;
import com.ecomm.reviewsratings.domain.ReviewRefusedException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CustomerReviewsService implements CustomerReviewsUseCase {

  private final ReviewStore reviews;
  private final OrderStore orders;
  private final ProductStore products;
  private final TimeSource time;

  public CustomerReviewsService(
      ReviewStore reviews, OrderStore orders, ProductStore products, TimeSource time) {
    this.reviews = reviews;
    this.orders = orders;
    this.products = products;
    this.time = time;
  }

  @Override
  public Eligibility eligibility(String customerId, String sku) {
    return Eligibility.of(reviews.find(sku, customerId), variantBought(customerId, sku));
  }

  @Override
  public Review post(String customerId, String author, String sku, ReviewContent content) {
    var eligibility = eligibility(customerId, sku);
    if (eligibility.refusal().isPresent()) {
      throw new ReviewRefusedException(eligibility.refusal().get());
    }
    var review =
        new Review(
            UUID.randomUUID().toString(),
            sku,
            customerId,
            author,
            eligibility.variantBought().orElseThrow(),
            content,
            time.now(),
            Optional.empty());
    // Two posts at once both pass the check above; the store keeps only the first.
    if (!reviews.add(review)) {
      throw new ReviewRefusedException(RefusalReason.ALREADY_REVIEWED);
    }
    return review;
  }

  @Override
  public Review edit(String customerId, String reviewId, ReviewContent content) {
    var edited = own(customerId, reviewId).edit(content, time.now());
    reviews.replace(edited);
    return edited;
  }

  @Override
  public void delete(String customerId, String reviewId) {
    reviews.delete(own(customerId, reviewId).id());
  }

  private Review own(String customerId, String reviewId) {
    return reviews
        .find(reviewId)
        .filter(r -> r.customerId().equals(customerId))
        .orElseThrow(() -> new ReviewNotFoundException(reviewId));
  }

  private Optional<String> variantBought(String customerId, String sku) {
    var variants = products.find(sku).map(ProductVariants::variantIds).orElse(Set.of());
    if (variants.isEmpty()) {
      return Optional.empty();
    }
    return Order.variantBought(orders.ofCustomer(customerId), variants);
  }
}
