package com.ecomm.reviewsratings.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** What a review may say, how its author is named, and which orders let a Customer write one. */
class ReviewRulesTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 5})
  void aRatingIsFromOneToFive(int rating) {
    assertThat(ReviewContent.of(rating, null, "Fine").rating()).isEqualTo(rating);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 6, -1})
  void anyOtherRatingIsRefused(int rating) {
    assertThatThrownBy(() -> ReviewContent.of(rating, null, "Fine"))
        .isInstanceOf(InvalidReviewException.class)
        .hasMessageContaining("rating");
  }

  @Test
  void theTitleIsOptionalAndABlankOneIsNone() {
    assertThat(ReviewContent.of(4, null, "Fine").title()).isEmpty();
    assertThat(ReviewContent.of(4, "  ", "Fine").title()).isEmpty();
    assertThat(ReviewContent.of(4, " Great ", "Fine").title()).contains("Great");
  }

  @Test
  void theTitleHasAtMost120Characters() {
    assertThat(ReviewContent.of(4, "t".repeat(120), "Fine").title()).isPresent();
    assertThatThrownBy(() -> ReviewContent.of(4, "t".repeat(121), "Fine"))
        .isInstanceOf(InvalidReviewException.class)
        .hasMessageContaining("title");
  }

  @Test
  void theBodyIsRequiredWithAtMost2000Characters() {
    assertThat(ReviewContent.of(4, null, "b".repeat(2000)).body()).hasSize(2000);
    assertThat(ReviewContent.of(4, null, "  Fine \n").body()).isEqualTo("Fine");
    for (var body : new String[] {null, "", "   ", "b".repeat(2001)}) {
      assertThatThrownBy(() -> ReviewContent.of(4, null, body))
          .isInstanceOf(InvalidReviewException.class)
          .hasMessageContaining("body");
    }
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "Demo, Customer, Demo C.",
        "  ada , lovelace, ada L.",
        "Ada, null, Ada",
        "Ada, ' ', Ada",
        "null, Lovelace, L.",
        "null, null, A Customer",
        "'', '', A Customer",
        "Zoë, Øster, Zoë Ø."
      })
  void theDisplayNameIsTheGivenNameAndTheFamilyNamesInitial(
      String given, String family, String expected) {
    assertThat(DisplayName.of(given, family)).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PAID", "FULFILLED", "SHIPPED", "DELIVERED"})
  void anOrderCountsOncePaidUntilCancelledOrReturned(OrderStatus status) {
    assertThat(status.counts()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"PLACED", "CANCELLED", "RETURNED", "UNKNOWN"})
  void anOrderThatIsOnlyPlacedCancelledOrReturnedDoesNotCount(OrderStatus status) {
    assertThat(status.counts()).isFalse();
  }

  @Test
  void aStatusAddedLaterIsUnknownAndDoesNotCount() {
    assertThat(OrderStatus.of("PAID")).isEqualTo(OrderStatus.PAID);
    assertThat(OrderStatus.of("PARTIALLY_REFUNDED")).isEqualTo(OrderStatus.UNKNOWN);
    assertThat(OrderStatus.of("UNKNOWN")).isEqualTo(OrderStatus.UNKNOWN);
    assertThat(OrderStatus.UNKNOWN.counts()).isFalse();
  }

  @Test
  void theVariantBoughtIsTheProductsVariantFromTheLatestCountingOrder() {
    var product = Set.of("P-RED", "P-BLUE");
    var orders =
        List.of(
            order("o1", OrderStatus.DELIVERED, "2026-01-01T00:00:00Z", "X-1", "P-RED"),
            order("o2", OrderStatus.PAID, "2026-03-01T00:00:00Z", "P-BLUE"),
            order("o3", OrderStatus.CANCELLED, "2026-04-01T00:00:00Z", "P-RED"),
            order("o4", OrderStatus.PLACED, "2026-05-01T00:00:00Z", "P-RED"));

    assertThat(Order.variantBought(orders, product)).contains("P-BLUE");
  }

  @Test
  void noCountingOrderOfTheProductMeansNothingBought() {
    var orders =
        List.of(
            order("o1", OrderStatus.RETURNED, "2026-01-01T00:00:00Z", "P-RED"),
            order("o2", OrderStatus.SHIPPED, "2026-02-01T00:00:00Z", "X-1"));

    assertThat(Order.variantBought(orders, Set.of("P-RED"))).isEmpty();
    assertThat(Order.variantBought(orders, Set.of())).isEmpty();
  }

  private static Order order(
      String orderId, OrderStatus status, String placedAt, String... variantIds) {
    return new Order(orderId, 1, "customer", status, Instant.parse(placedAt), List.of(variantIds));
  }
}
