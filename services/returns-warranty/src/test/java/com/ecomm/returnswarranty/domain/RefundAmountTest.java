package com.ecomm.returnswarranty.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An RMA's Refund amount: its units' Prices, less their share of the Order's Discounts, plus their
 * share of its Tax, so that refunding every unit refunds the Order's total less its Shipping cost.
 */
class RefundAmountTest {

  /**
   * Two headphones at 10.00 and a case at 5.00: a subtotal of 25.00, 2.50 of Discounts and 4.76 of
   * Tax. The Discounts split 2.00 and 0.50; the Tax 3.808 and 0.952, so the headphones' 3.80 gets
   * the cent left over: 3.81 and 0.95.
   */
  private static final Order ORDER =
      new Order(
          "order-1",
          3,
          "EUR",
          List.of(new OrderLine("HEADPHONES", 2, 1000), new OrderLine("CASE", 1, 500)),
          250,
          476);

  @Test
  void eachLineGetsItsShareOfTheDiscountsAndTax() {
    assertThat(ORDER.refundableOf("HEADPHONES")).isEqualTo(new Money(2181, "EUR"));
    assertThat(ORDER.refundableOf("CASE")).isEqualTo(new Money(545, "EUR"));
  }

  @Test
  void aLeftoverCentGoesToTheLargestRemainderAndATieToTheFirstLine() {
    var order =
        new Order(
            "order-2",
            1,
            "EUR",
            List.of(
                new OrderLine("A", 1, 100), new OrderLine("B", 1, 100), new OrderLine("C", 1, 100)),
            100,
            0);

    assertThat(order.refundableOf("A")).isEqualTo(new Money(66, "EUR"));
    assertThat(order.refundableOf("B")).isEqualTo(new Money(67, "EUR"));
    assertThat(order.refundableOf("C")).isEqualTo(new Money(67, "EUR"));
  }

  @Test
  void aLinesUnitsShareItsAmountSoThatTheLastUnitTakesTheCentLeft() {
    assertThat(ORDER.refundAmount("HEADPHONES", 1, List.of())).isEqualTo(new Money(1090, "EUR"));
    assertThat(
            ORDER.refundAmount("HEADPHONES", 1, List.of(new Order.Held(1, new Money(1090, "EUR")))))
        .isEqualTo(new Money(1091, "EUR"));
    assertThat(ORDER.refundAmount("HEADPHONES", 2, List.of())).isEqualTo(new Money(2181, "EUR"));
  }

  @Test
  void refundingEveryUnitRefundsTheTotalLessShipping() {
    var first = ORDER.refundAmount("HEADPHONES", 1, List.of());
    var second = ORDER.refundAmount("HEADPHONES", 1, List.of(new Order.Held(1, first)));
    var theCase = ORDER.refundAmount("CASE", 1, List.of());

    assertThat(first.amountMinor() + second.amountMinor() + theCase.amountMinor())
        .isEqualTo(2500 - 250 + 476);
  }

  @Test
  void aUnitReleasedAndClaimedAgainKeepsTheSumExact() {
    // Three units of 4.00, less 1.00 of Discounts, split 3.66, 3.67, 3.67. The first two are
    // claimed, the first is
    // released,
    // and the last two are claimed after it: the line still comes to 11.00.
    var order = new Order("order-3", 1, "EUR", List.of(new OrderLine("CABLE", 3, 400)), 100, 0);
    var first = order.refundAmount("CABLE", 1, List.of());
    var second = order.refundAmount("CABLE", 1, List.of(new Order.Held(1, first)));
    // first is released: only second still holds its unit.
    var third = order.refundAmount("CABLE", 1, List.of(new Order.Held(1, second)));
    var fourth =
        order.refundAmount(
            "CABLE", 1, List.of(new Order.Held(1, second), new Order.Held(1, third)));

    assertThat(List.of(first, second, third, fourth))
        .extracting(Money::amountMinor)
        .containsExactly(366L, 367L, 366L, 367L);
    assertThat(second.amountMinor() + third.amountMinor() + fourth.amountMinor()).isEqualTo(1100);
  }
}
