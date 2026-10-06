package com.ecomm.reviewsratings.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.reviewsratings.application.port.in.CustomerReviewsUseCase;
import com.ecomm.reviewsratings.domain.DisplayName;
import com.ecomm.reviewsratings.domain.InvalidReviewException;
import com.ecomm.reviewsratings.domain.RefusalReason;
import com.ecomm.reviewsratings.domain.ReviewNotFoundException;
import com.ecomm.reviewsratings.domain.ReviewRefusedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Customer's own reviews. Every endpoint needs a token with the {@code CUSTOMER} role,
 * whose {@code sub} is the author; another Customer's review is a 404.
 */
@RestController
@PreAuthorize("hasRole('CUSTOMER')")
class CustomerReviewsController {

  private static final Logger log = LoggerFactory.getLogger(CustomerReviewsController.class);

  /**
   * Whether the Customer may review the Product; if not, why, and their review if they have one.
   */
  record EligibilityResponse(boolean eligible, String reason, ReviewResponse review) {}

  private final CustomerReviewsUseCase reviews;

  CustomerReviewsController(CustomerReviewsUseCase reviews) {
    this.reviews = reviews;
  }

  @GetMapping("/products/{sku}/eligibility")
  EligibilityResponse eligibility(CurrentCustomer customer, @PathVariable String sku) {
    var eligibility = reviews.eligibility(customer.id(), sku);
    return new EligibilityResponse(
        eligibility.eligible(),
        eligibility.refusal().map(RefusalReason::code).orElse(null),
        eligibility.review().map(ReviewResponse::of).orElse(null));
  }

  /** Posts a review under the token's given name and family name's initial, as they are now. */
  @PostMapping("/products/{sku}/reviews")
  @ResponseStatus(HttpStatus.CREATED)
  ReviewResponse post(
      CurrentCustomer customer,
      @AuthenticationPrincipal Jwt token,
      @PathVariable String sku,
      @RequestBody ReviewRequest request) {
    var author =
        DisplayName.of(token.getClaimAsString("given_name"), token.getClaimAsString("family_name"));
    var review = reviews.post(customer.id(), author, sku, request.toContent());
    log.info("Posted review {} of {}", review.id(), sku);
    return ReviewResponse.of(review);
  }

  @PutMapping("/reviews/{reviewId}")
  ReviewResponse edit(
      CurrentCustomer customer, @PathVariable String reviewId, @RequestBody ReviewRequest request) {
    var review = reviews.edit(customer.id(), reviewId, request.toContent());
    log.info("Edited review {}", reviewId);
    return ReviewResponse.of(review);
  }

  @DeleteMapping("/reviews/{reviewId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(CurrentCustomer customer, @PathVariable String reviewId) {
    reviews.delete(customer.id(), reviewId);
    log.info("Deleted review {}", reviewId);
  }

  @ExceptionHandler(InvalidReviewException.class)
  ProblemDetail invalid(InvalidReviewException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  /**
   * A 403 when they haven't bought it, a 409 when they've reviewed it; {@code reason} says which.
   */
  @ExceptionHandler(ReviewRefusedException.class)
  ProblemDetail refused(ReviewRefusedException e) {
    log.info("Review refused: {}", e.reason().code());
    var status =
        switch (e.reason()) {
          case NOT_PURCHASED -> HttpStatus.FORBIDDEN;
          case ALREADY_REVIEWED -> HttpStatus.CONFLICT;
        };
    var problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
    problem.setProperty("reason", e.reason().code());
    return problem;
  }

  @ExceptionHandler(ReviewNotFoundException.class)
  ProblemDetail notFound(ReviewNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }
}
