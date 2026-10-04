package com.ecomm.ordermanagement.domain;

import static com.ecomm.ordermanagement.domain.OrderStatus.CANCELLED;
import static com.ecomm.ordermanagement.domain.OrderStatus.DELIVERED;
import static com.ecomm.ordermanagement.domain.OrderStatus.FULFILLED;
import static com.ecomm.ordermanagement.domain.OrderStatus.PAID;
import static com.ecomm.ordermanagement.domain.OrderStatus.PLACED;
import static com.ecomm.ordermanagement.domain.OrderStatus.RETURNED;
import static com.ecomm.ordermanagement.domain.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Order Status transition table: the main path {@code PLACED → PAID → FULFILLED → SHIPPED →
 * DELIVERED}, cancellation before anything leaves the warehouse, and a return once delivered. Every
 * pair not listed here is illegal. Every change appends to the Order Status history and raises the
 * Order's version.
 */
class OrderStatusTransitionTest {

  private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED =
      Map.of(
          PLACED, EnumSet.of(PAID, CANCELLED),
          PAID, EnumSet.of(FULFILLED, CANCELLED),
          FULFILLED, EnumSet.of(SHIPPED),
          SHIPPED, EnumSet.of(DELIVERED),
          DELIVERED, EnumSet.of(RETURNED),
          CANCELLED, EnumSet.noneOf(OrderStatus.class),
          RETURNED, EnumSet.noneOf(OrderStatus.class));

  static Stream<Arguments> allowed() {
    return pairs(true);
  }

  static Stream<Arguments> illegal() {
    return pairs(false);
  }

  private static final Instant PLACED_AT = Instant.parse("2026-09-27T10:00:00Z");
  private static final Instant LATER = Instant.parse("2026-09-27T10:05:00Z");

  @Test
  void aNewOrderIsPlacedWithItsPlacementAsItsOnlyHistoryAtVersionOne() {
    var order =
        Order.place(
            UUID.randomUUID(),
            "customer-42",
            List.of(new OrderLine("PHN-PIXEL-9", 1, Money.of(79900, "EUR"))),
            Optional.empty(),
            Money.of(0, "EUR"),
            Caller.CHECKOUT,
            PLACED_AT);

    assertThat(order.status()).isEqualTo(PLACED);
    assertThat(order.placedAt()).isEqualTo(PLACED_AT);
    assertThat(order.version()).isEqualTo(1);
    assertThat(order.statusHistory())
        .containsExactly(new StatusHistoryEntry(PLACED, PLACED_AT, Caller.CHECKOUT, false));
  }

  @ParameterizedTest(name = "{0} → {1}")
  @MethodSource("allowed")
  void anAllowedTransitionChangesTheStatusAndAppendsToTheHistory(OrderStatus from, OrderStatus to) {
    var order = orderIn(from);

    var changed = order.changeStatusTo(to, Caller.CHECKOUT, LATER);

    assertThat(changed.status()).isEqualTo(to);
    assertThat(changed.version()).isEqualTo(order.version() + 1);
    assertThat(changed.statusHistory())
        .startsWith(order.statusHistory().toArray(StatusHistoryEntry[]::new))
        .endsWith(new StatusHistoryEntry(to, LATER, Caller.CHECKOUT, false))
        .hasSize(order.statusHistory().size() + 1);
  }

  @Test
  void aPaidThenCancelledOrderKeepsEveryStatusItWasIn() {
    var cancelled =
        orderIn(PLACED)
            .changeStatusTo(PAID, Caller.CHECKOUT, LATER)
            .changeStatusTo(CANCELLED, Caller.CHECKOUT, LATER.plusSeconds(60));

    assertThat(cancelled.statusHistory())
        .extracting(StatusHistoryEntry::status)
        .containsExactly(PLACED, PAID, CANCELLED);
    assertThat(cancelled.version()).isEqualTo(3);
    assertThat(cancelled.placedAt()).isEqualTo(PLACED_AT);
  }

  @ParameterizedTest(name = "{0} → {1}")
  @MethodSource("illegal")
  void anIllegalTransitionIsRejected(OrderStatus from, OrderStatus to) {
    var order = orderIn(from);

    assertThatThrownBy(() -> order.changeStatusTo(to, Caller.CHECKOUT, LATER))
        .isInstanceOf(IllegalStatusTransitionException.class)
        .hasMessageContaining(from.name())
        .hasMessageContaining(to.name());
  }

  private static Stream<Arguments> pairs(boolean allowed) {
    var pairs = new ArrayList<Arguments>();
    for (var from : OrderStatus.values()) {
      for (var to : OrderStatus.values()) {
        if (ALLOWED.get(from).contains(to) == allowed) {
          pairs.add(Arguments.of(from, to));
        }
      }
    }
    return pairs.stream();
  }

  /**
   * An Order placed at {@link #PLACED_AT} and now in {@code status}, at version 2 unless placed.
   */
  private static Order orderIn(OrderStatus status) {
    var history = new ArrayList<StatusHistoryEntry>();
    history.add(new StatusHistoryEntry(PLACED, PLACED_AT, Caller.CHECKOUT, false));
    if (status != PLACED) {
      history.add(new StatusHistoryEntry(status, PLACED_AT, Caller.CHECKOUT, true));
    }
    return new Order(
        UUID.randomUUID(),
        "customer-42",
        List.of(new OrderLine("PHN-PIXEL-9", 1, Money.of(79900, "EUR"))),
        Optional.empty(),
        Money.of(0, "EUR"),
        history,
        history.size());
  }
}
