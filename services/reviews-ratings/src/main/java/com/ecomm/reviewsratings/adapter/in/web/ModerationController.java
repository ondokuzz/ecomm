package com.ecomm.reviewsratings.adapter.in.web;

import com.ecomm.reviewsratings.application.port.in.ModerateReviewsUseCase;
import com.ecomm.reviewsratings.domain.InvalidQueryException;
import com.ecomm.reviewsratings.domain.InvalidReviewException;
import com.ecomm.reviewsratings.domain.ModerationRefusedException;
import com.ecomm.reviewsratings.domain.ReviewNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff moderate reviews: the queue, any one review, and the three actions, each recorded in its
 * moderation history under the token's {@code sub}. Every endpoint needs the {@code STAFF} role.
 */
@RestController
@PreAuthorize("hasRole('STAFF')")
class ModerationController {

  private static final Logger log = LoggerFactory.getLogger(ModerationController.class);

  static final int DEFAULT_PAGE_SIZE = 20;

  record PageResponse(List<ModerationResponse> items, int page, int size, long total) {}

  private final ModerateReviewsUseCase moderation;

  ModerationController(ModerateReviewsUseCase moderation) {
    this.moderation = moderation;
  }

  /**
   * A page of the moderation queue: held reviews and published ones with open reports, oldest
   * first; {@code page} counts from 0.
   */
  @GetMapping("/moderation/reviews")
  PageResponse queue(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    var found = moderation.queue(page, size);
    return new PageResponse(
        found.items().stream().map(ModerationResponse::of).toList(),
        found.page(),
        found.size(),
        found.total());
  }

  /** Any review, whatever its status, as Staff moderate it. */
  @GetMapping("/moderation/reviews/{reviewId}")
  ModerationResponse review(@PathVariable String reviewId) {
    return ModerationResponse.of(moderation.review(reviewId));
  }

  /** What Staff remove a review with: {@code {"reason"}}, which its author sees. */
  record RemoveRequest(String reason) {}

  /** Publishes a held review: 409 {@code notHeld} for any other. */
  @PostMapping("/reviews/{reviewId}/publish")
  ModerationResponse publish(@PathVariable String reviewId, @AuthenticationPrincipal Jwt staff) {
    var review = moderation.publish(reviewId, staff.getSubject());
    log.info("Published review {}", reviewId);
    return ModerationResponse.of(review);
  }

  /** Resolves a published review's open reports: 409 {@code noOpenReports} without any. */
  @PostMapping("/reviews/{reviewId}/dismiss-reports")
  ModerationResponse dismissReports(
      @PathVariable String reviewId, @AuthenticationPrincipal Jwt staff) {
    var review = moderation.dismissReports(reviewId, staff.getSubject());
    log.info("Dismissed the reports of review {}", reviewId);
    return ModerationResponse.of(review);
  }

  /**
   * Removes a review, with a required reason of up to 1,000 characters: 400 without one, 409 {@code
   * alreadyRemoved} once removed.
   */
  @PostMapping("/reviews/{reviewId}/remove")
  ModerationResponse remove(
      @PathVariable String reviewId,
      @RequestBody(required = false) RemoveRequest request,
      @AuthenticationPrincipal Jwt staff) {
    var review =
        moderation.remove(reviewId, staff.getSubject(), request == null ? null : request.reason());
    log.info("Removed review {}", reviewId);
    return ModerationResponse.of(review);
  }

  /** The review's status rules the action out, perhaps since another Staff member's came first. */
  @ExceptionHandler(ModerationRefusedException.class)
  ProblemDetail refused(ModerationRefusedException e) {
    log.info("Moderation refused: {}", e.refusal().code());
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", e.refusal().code());
    return problem;
  }

  /** A page outside the queue's limits, or a removal without a reason. */
  @ExceptionHandler({InvalidQueryException.class, InvalidReviewException.class})
  ProblemDetail invalid(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(ReviewNotFoundException.class)
  ProblemDetail notFound(ReviewNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }
}
