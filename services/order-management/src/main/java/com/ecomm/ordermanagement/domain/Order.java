package com.ecomm.ordermanagement.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * A Customer's confirmed intent to purchase one or more Variants. It belongs to the Customer who
 * placed it, and only they may see it. Its total is the sum of its lines, less each of its {@code
 * discounts} (a Campaign's or a Coupon's, in the order they applied), plus its {@code tax}, all in
 * one currency, and never below zero.
 *
 * <p>Its {@code statusHistory} holds every Order Status it has been in, oldest first, starting with
 * its placement; entries are only ever appended. Its {@code version} starts at 1 and goes up by one
 * with every change.
 */
public record Order(
    UUID id,
    String customerId,
    List<OrderLine> lines,
    List<Discount> discounts,
    Money tax,
    List<StatusHistoryEntry> statusHistory,
    long version) {

  public Order {
    lines = List.copyOf(lines);
    discounts = List.copyOf(discounts);
    statusHistory = List.copyOf(statusHistory);
    if (statusHistory.isEmpty() || statusHistory.getFirst().status() != OrderStatus.PLACED) {
      throw new IllegalArgumentException("an order's history starts with its placement");
    }
  }

  /** The longest Customer ID an Order can hold. */
  public static final int MAX_CUSTOMER_ID_LENGTH = 255;

  /**
   * A new Order in {@link OrderStatus#PLACED}. Throws {@link InvalidOrderException} unless it names
   * a valid Customer, has at least one line, each Variant appears once, every line, Discount and
   * the tax are in the same currency, the tax isn't negative, and the Discounts together are no
   * larger than the lines plus the tax.
   */
  public static Order place(
      UUID id,
      String customerId,
      List<OrderLine> lines,
      List<Discount> discounts,
      Money tax,
      Caller placedBy,
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
    if (discounts == null) {
      throw new InvalidOrderException("discounts must be a list, even an empty one");
    }
    for (var discount : discounts) {
      if (discount == null) {
        throw new InvalidOrderException("a discount can't be null");
      }
      if (!discount.amount().currency().equals(currency)) {
        throw new InvalidOrderException("every discount must be in the order's currency");
      }
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
    var placement = StatusHistoryEntry.of(OrderStatus.PLACED, placedAt, placedBy);
    var order = new Order(id, customerId, lines, discounts, tax, List.of(placement), 1);
    // Rejects a total too large to hold, or below zero, before the Order is recorded.
    if (order.total().amountMinor() < 0) {
      throw new InvalidOrderException("the discounts are larger than the lines plus the tax");
    }
    return order;
  }

  /**
   * This Order in {@code next}, with the change appended to its history and its version one higher.
   * Throws {@link IllegalStatusTransitionException} unless its current status can move there.
   */
  public Order changeStatusTo(OrderStatus next, Caller changedBy, Instant at) {
    var status = status();
    if (!status.canBecome(next)) {
      throw new IllegalStatusTransitionException(status, next);
    }
    var history = new ArrayList<>(statusHistory);
    history.add(StatusHistoryEntry.of(next, at, changedBy));
    return new Order(id, customerId, lines, discounts, tax, history, version + 1);
  }

  /** The Order Status it is in now: the last one in its history. */
  public OrderStatus status() {
    return statusHistory.getLast().status();
  }

  /** When it was placed: the first entry in its history. */
  public Instant placedAt() {
    return statusHistory.getFirst().at();
  }

  /** The sum of every line's unit price times its quantity. */
  public Money subtotal() {
    var subtotalMinor = 0L;
    for (var line : lines) {
      subtotalMinor = addExact(subtotalMinor, line.subtotalMinor());
    }
    return new Money(subtotalMinor, currency());
  }

  /** The currency every line, Discount and the tax are in. */
  public Currency currency() {
    return lines.get(0).unitPrice().currency();
  }

  /** What the Customer pays: the {@link #subtotal()}, less every Discount, plus the tax. */
  public Money total() {
    var discountedMinor = subtotal().amountMinor();
    for (var discount : discounts) {
      discountedMinor = addExact(discountedMinor, -discount.amount().amountMinor());
    }
    var totalMinor = addExact(discountedMinor, tax.amountMinor());
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
