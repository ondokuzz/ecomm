package com.ecomm.ordermanagement.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Customer's confirmed intent to purchase one or more Variants. It belongs to the Customer who
 * placed it, and only they may see it. Its total is the sum of its lines, less its {@code discount}
 * if it has one, plus its {@code tax}, all in one currency.
 */
public record Order(
    UUID id,
    String customerId,
    List<OrderLine> lines,
    Optional<Discount> discount,
    Money tax,
    OrderStatus status,
    Instant placedAt) {

  public Order {
    lines = List.copyOf(lines);
  }

  /** The longest Customer ID an Order can hold. */
  public static final int MAX_CUSTOMER_ID_LENGTH = 255;

  /**
   * A new Order in {@link OrderStatus#PLACED}. Throws {@link InvalidOrderException} unless it names
   * a valid Customer, has at least one line, each Variant appears once, every line, the discount
   * and the tax are in the same currency, the tax isn't negative, and the discount is no larger
   * than the lines plus the tax.
   */
  public static Order place(
      UUID id,
      String customerId,
      List<OrderLine> lines,
      Optional<Discount> discount,
      Money tax,
      Instant placedAt) {
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
    var currency = lines.get(0).unitPrice().currency();
    if (discount.isPresent() && !discount.get().amount().currency().equals(currency)) {
      throw new InvalidOrderException("the discount must be in the order's currency");
    }
    if (tax == null) {
      throw new InvalidOrderException("an order needs a tax, even a zero one");
    }
    if (tax.amountMinor() < 0) {
      throw new InvalidOrderException("the tax can't be negative");
    }
    if (!tax.currency().equals(currency)) {
      throw new InvalidOrderException("the tax must be in the order's currency");
    }
    var order = new Order(id, customerId, lines, discount, tax, OrderStatus.PLACED, placedAt);
    // Rejects a total too large to hold, or below zero, before the Order is recorded.
    if (order.total().amountMinor() < 0) {
      throw new InvalidOrderException("the discount is larger than the lines plus the tax");
    }
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
    return new Order(id, customerId, lines, discount, tax, next, placedAt);
  }

  /** The sum of every line's unit price times its quantity. */
  public Money subtotal() {
    var subtotalMinor = 0L;
    for (var line : lines) {
      subtotalMinor = addExact(subtotalMinor, line.subtotalMinor());
    }
    return new Money(subtotalMinor, currency());
  }

  /** The currency every line, the discount and the tax are in. */
  public Currency currency() {
    return lines.get(0).unitPrice().currency();
  }

  /** What the Customer pays: the {@link #subtotal()}, less the discount, plus the tax. */
  public Money total() {
    var discountMinor = discount.map(d -> d.amount().amountMinor()).orElse(0L);
    var totalMinor = addExact(subtotal().amountMinor() - discountMinor, tax.amountMinor());
    return new Money(totalMinor, currency());
  }

  private static long addExact(long a, long b) {
    try {
      return Math.addExact(a, b);
    } catch (ArithmeticException e) {
      throw new InvalidOrderException("the order's total is too large");
    }
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
