package com.ecomm.reviewsratings.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A Product's ratings: how many reviews it has, their average to one decimal, rounding a half up,
 * and how many gave each star from 1 to 5. A Product without reviews has no average.
 */
public record RatingSummary(
    String sku, long count, Optional<BigDecimal> average, Map<Integer, Long> perStar) {

  /** From how many reviews gave each rating; a rating with none may be left out. */
  public static RatingSummary of(String sku, Map<Integer, Long> countsByRating) {
    var perStar = new LinkedHashMap<Integer, Long>();
    long count = 0;
    long total = 0;
    for (int star = 1; star <= 5; star++) {
      long n = countsByRating.getOrDefault(star, 0L);
      perStar.put(star, n);
      count += n;
      total += n * star;
    }
    var average =
        count == 0
            ? Optional.<BigDecimal>empty()
            : Optional.of(
                BigDecimal.valueOf(total)
                    .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
    return new RatingSummary(sku, count, average, Collections.unmodifiableMap(perStar));
  }
}
