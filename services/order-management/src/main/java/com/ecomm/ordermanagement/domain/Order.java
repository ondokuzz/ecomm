package com.ecomm.ordermanagement.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * A Customer's confirmed intent to purchase one or more Variants. It belongs to the Customer who
 * placed it, and only they may see it. Its total is the sum of its lines, all in one currency.
 */
public record Order(
    UUID id, String customerId, List<OrderLine> lines, OrderStatus status, Instant placedAt) {

  public Order {
    lines = List.copyOf(lines);
  }

  /** The longest Customer ID an Order can hold. */
  public static final int MAX_CUSTOMER_ID_LENGTH = 255;

  /**
   * A new Order in {@link OrderStatus#PLACED}. Throws {@link InvalidOrderException} unless it names
   * a valid Customer, has at least one line, each Variant appears once, and every line is in the
   * same currency.
   */
  public static Order place(UUID id, String customerId, List<OrderLine> lines, Instant placedAt) {
    requireValidCustomerId(customerId);
    if (lines == null || lines.isEmpty()) {
      throw new InvalidOrderException("an order needs at least one line");
    }
    var variants = new HashSet<String>();
    for (var line : lines) {
      if (!variants.add(line.variantId())) {
        throw new InvalidOrderException(line.variantId() + " appears on more than one line");
      }
      if (!line.unitPrice().currency().equals(lines.get(0).unitPrice().currency())) {
        throw new InvalidOrderException("every line must be in the same currency");
      }
    }
    var order = new Order(id, customerId, lines, OrderStatus.PLACED, placedAt);
    order.total(); // rejects a total too large to hold, before the Order is recorded
    return order;
  }

  /**
   * This Order in {@code next}. Throws {@link IllegalStatusTransitionException} unless its current
   * status can move there.
   */
  public Order changeStatusTo(OrderStatus next) {
    if (!status.canBecome(next)) {
      throw new IllegalStatusTransitionException(status, next);
    }
    return new Order(id, customerId, lines, next, placedAt);
  }

  /** The sum of every line's unit price times its quantity. */
  public Money total() {
    var totalMinor = 0L;
    for (var line : lines) {
      try {
        totalMinor = Math.addExact(totalMinor, line.subtotalMinor());
      } catch (ArithmeticException e) {
        throw new InvalidOrderException("the order's total is too large");
      }
    }
    return new Money(totalMinor, lines.get(0).unitPrice().currency());
  }

  /**
   * Throws {@link InvalidOrderException} unless {@code customerId} could name an Order's owner:
   * present, not blank, and at most {@link #MAX_CUSTOMER_ID_LENGTH} characters.
   */
  public static void requireValidCustomerId(String customerId) {
    if (customerId == null || customerId.isBlank()) {
      throw new InvalidOrderException("an order needs a customerId");
    }
    if (customerId.length() > MAX_CUSTOMER_ID_LENGTH) {
      throw new InvalidOrderException(
          "a customerId is at most " + MAX_CUSTOMER_ID_LENGTH + " characters");
    }
  }

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }
}
