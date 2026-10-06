package com.ecomm.reviewsratings.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RatingSummaryTest {

  @Test
  void aProductWithNoReviewsHasNoAverage() {
    var summary = RatingSummary.of("SKU", Map.of());

    assertThat(summary.count()).isZero();
    assertThat(summary.average()).isEmpty();
    assertThat(summary.perStar())
        .containsExactly(
            Map.entry(1, 0L),
            Map.entry(2, 0L),
            Map.entry(3, 0L),
            Map.entry(4, 0L),
            Map.entry(5, 0L));
  }

  @Test
  void theAverageIsRoundedToOneDecimal() {
    // (5·2 + 4·1) / 3 = 4.666…
    var summary = RatingSummary.of("SKU", Map.of(5, 2L, 4, 1L));

    assertThat(summary.count()).isEqualTo(3);
    assertThat(summary.average()).contains(new BigDecimal("4.7"));
    assertThat(summary.perStar()).containsEntry(5, 2L).containsEntry(4, 1L).containsEntry(1, 0L);
  }

  @Test
  void aHalfRoundsUp() {
    // (3 + 4) / 2 = 3.5; (1·3 + 2) / 4 = 1.25
    assertThat(RatingSummary.of("SKU", Map.of(3, 1L, 4, 1L)).average())
        .contains(new BigDecimal("3.5"));
    assertThat(RatingSummary.of("SKU", Map.of(1, 3L, 2, 1L)).average())
        .contains(new BigDecimal("1.3"));
  }

  @Test
  void aWholeAverageKeepsItsDecimal() {
    assertThat(RatingSummary.of("SKU", Map.of(4, 3L)).average()).contains(new BigDecimal("4.0"));
  }
}
