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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Order Status transition table: the main path {@code PLACED → PAID → FULFILLED → SHIPPED →
 * DELIVERED}, cancellation before anything leaves the warehouse, and a return once delivered. Every
 * pair not listed here is illegal.
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

  @ParameterizedTest(name = "{0} → {1}")
  @MethodSource("allowed")
  void anAllowedTransitionChangesTheStatus(OrderStatus from, OrderStatus to) {
    var changed = orderIn(from).changeStatusTo(to);

    assertThat(changed.status()).isEqualTo(to);
  }

  @ParameterizedTest(name = "{0} → {1}")
  @MethodSource("illegal")
  void anIllegalTransitionIsRejected(OrderStatus from, OrderStatus to) {
    var order = orderIn(from);

    assertThatThrownBy(() -> order.changeStatusTo(to))
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

  private static Order orderIn(OrderStatus status) {
    return new Order(
        UUID.randomUUID(),
        "customer-42",
        List.of(new OrderLine("PHN-PIXEL-9", 1, Money.of(79900, "EUR"))),
        status,
        Instant.parse("2026-09-27T10:00:00Z"));
  }
}
