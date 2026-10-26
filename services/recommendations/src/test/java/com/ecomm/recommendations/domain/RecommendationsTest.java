package com.ecomm.recommendations.domain;

import static com.ecomm.recommendations.domain.Reason.BOUGHT_TOGETHER;
import static com.ecomm.recommendations.domain.Reason.POPULAR_IN_CATEGORY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecommendationsTest {

  private static final Instant T = Instant.parse("2026-10-01T12:00:00Z");
  private static final Optional<Category> AUDIO = Optional.of(new Category("audio", "Audio"));

  @Test
  void boughtTogetherIsRankedByOrdersThenTheLatestOrderThenSku() {
    var ranked =
        Recommendations.rank(
            "P",
            List.of(
                new Candidate("D", 1, T.minusSeconds(10)),
                new Candidate("A", 2, T.minusSeconds(500)),
                new Candidate("C", 1, T),
                new Candidate("B", 1, T.minusSeconds(10))),
            AUDIO,
            List.of(),
            4);

    assertThat(ranked)
        .containsExactly(
            new Recommendation("A", 2, BOUGHT_TOGETHER, null),
            new Recommendation("C", 1, BOUGHT_TOGETHER, null),
            new Recommendation("B", 1, BOUGHT_TOGETHER, null),
            new Recommendation("D", 1, BOUGHT_TOGETHER, null));
  }

  @Test
  void fewerThanTheLimitBoughtTogetherAreFilledWithTheCategorysBestSellersByOrdersThenSku() {
    var ranked =
        Recommendations.rank(
            "P",
            List.of(new Candidate("X", 1, T)),
            AUDIO,
            List.of(
                new Candidate("B", 3, T.minusSeconds(900)),
                new Candidate("C", 7, T.minusSeconds(900)),
                new Candidate("A", 3, T)),
            4);

    assertThat(ranked)
        .containsExactly(
            new Recommendation("X", 1, BOUGHT_TOGETHER, null),
            new Recommendation("C", 7, POPULAR_IN_CATEGORY, "Audio"),
            new Recommendation("A", 3, POPULAR_IN_CATEGORY, "Audio"),
            new Recommendation("B", 3, POPULAR_IN_CATEGORY, "Audio"));
  }

  @Test
  void theFillLeavesOutTheProductItselfAndThoseAlreadyListed() {
    var ranked =
        Recommendations.rank(
            "P",
            List.of(new Candidate("X", 1, T)),
            AUDIO,
            List.of(new Candidate("P", 9, T), new Candidate("X", 8, T), new Candidate("A", 1, T)),
            4);

    assertThat(ranked)
        .containsExactly(
            new Recommendation("X", 1, BOUGHT_TOGETHER, null),
            new Recommendation("A", 1, POPULAR_IN_CATEGORY, "Audio"));
  }

  @Test
  void atTheLimitBoughtTogetherNothingIsFilled() {
    var ranked =
        Recommendations.rank(
            "P",
            List.of(new Candidate("X", 2, T), new Candidate("Y", 1, T)),
            AUDIO,
            List.of(new Candidate("A", 9, T)),
            2);

    assertThat(ranked).extracting(Recommendation::sku).containsExactly("X", "Y");
  }

  @Test
  void moreBoughtTogetherThanTheLimitAreCutToIt() {
    var ranked =
        Recommendations.rank(
            "P",
            List.of(new Candidate("X", 2, T), new Candidate("Y", 1, T), new Candidate("Z", 3, T)),
            AUDIO,
            List.of(),
            1);

    assertThat(ranked).extracting(Recommendation::sku).containsExactly("Z");
  }

  @Test
  void aProductWithNothingBoughtTogetherIsAllFill() {
    var ranked =
        Recommendations.rank(
            "P", List.of(), AUDIO, List.of(new Candidate("A", 1, T), new Candidate("B", 2, T)), 4);

    assertThat(ranked)
        .containsExactly(
            new Recommendation("B", 2, POPULAR_IN_CATEGORY, "Audio"),
            new Recommendation("A", 1, POPULAR_IN_CATEGORY, "Audio"));
  }

  @Test
  void withoutAKnownCategoryNothingIsFilled() {
    var ranked =
        Recommendations.rank(
            "P",
            List.of(new Candidate("X", 1, T)),
            Optional.empty(),
            List.of(new Candidate("A", 1, T)),
            4);

    assertThat(ranked).extracting(Recommendation::sku).containsExactly("X");
  }
}
