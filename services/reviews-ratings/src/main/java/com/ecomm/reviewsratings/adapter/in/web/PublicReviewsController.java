package com.ecomm.reviewsratings.adapter.in.web;

import com.ecomm.reviewsratings.application.port.in.ReadReviewsUseCase;
import com.ecomm.reviewsratings.domain.InvalidQueryException;
import com.ecomm.reviewsratings.domain.RatingSummary;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anyone may read a Product's reviews and its rating summary, or many Products' summaries. */
@RestController
class PublicReviewsController {

  static final int DEFAULT_PAGE_SIZE = 10;

  /** A summary; {@code average} is null without reviews, and {@code perStar} counts 1 to 5. */
  record SummaryResponse(String sku, long count, BigDecimal average, Map<String, Long> perStar) {

    static SummaryResponse of(RatingSummary summary) {
      var perStar = new LinkedHashMap<String, Long>();
      summary.perStar().forEach((star, count) -> perStar.put(String.valueOf(star), count));
      return new SummaryResponse(
          summary.sku(), summary.count(), summary.average().orElse(null), perStar);
    }
  }

  record PageResponse(List<ReviewResponse> items, int page, int size, long total) {}

  private final ReadReviewsUseCase reads;

  PublicReviewsController(ReadReviewsUseCase reads) {
    this.reads = reads;
  }

  /** A page of the Product's reviews, newest first; {@code page} counts from 0. */
  @GetMapping("/products/{sku}/reviews")
  PageResponse reviews(
      @PathVariable String sku,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    var found = reads.reviews(sku, page, size);
    return new PageResponse(
        found.items().stream().map(ReviewResponse::of).toList(),
        found.page(),
        found.size(),
        found.total());
  }

  @GetMapping("/products/{sku}/rating-summary")
  SummaryResponse summary(@PathVariable String sku) {
    return SummaryResponse.of(reads.summary(sku));
  }

  /** Each SKU's summary, in the order asked: {@code ?sku=A&sku=B}, up to 50. */
  @GetMapping("/rating-summaries")
  List<SummaryResponse> summaries(@RequestParam(name = "sku", required = false) List<String> skus) {
    return reads.summaries(skus == null ? List.of() : skus).stream()
        .map(SummaryResponse::of)
        .toList();
  }

  @ExceptionHandler(InvalidQueryException.class)
  ProblemDetail invalid(InvalidQueryException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
