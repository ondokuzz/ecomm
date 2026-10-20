package com.ecomm.returnswarranty.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * What Returns & Warranty keeps of an Order Management Order, for RMAs' Refund amounts: its lines'
 * Prices, and the sums of its Discounts and its Tax, all in one Currency. Its Shipping cost is
 * never refunded, so it isn't kept.
 *
 * @param version the newest version of Order Management's events applied
 */
public record Order(
    String id,
    long version,
    String currency,
    List<OrderLine> lines,
    long discountsMinor,
    long taxMinor) {

  /** Units of a Variant another RMA holds, and the Refund amount it was given for them. */
  public record Held(int quantity, Money refundAmount) {}

  public Order {
    lines = List.copyOf(lines);
  }

  /**
   * What refunding every unit of the Variant's line gives back: its subtotal, less its share of the
   * Discounts, plus its share of the Tax. Each is shared over the lines in proportion to their
   * subtotals, a Minor unit left over going to the largest remainder, and a tie to the first line,
   * so the lines together come to the Order's total less its Shipping cost.
   *
   * @throws IllegalArgumentException when the Order has no line for the Variant
   */
  public Money refundableOf(String variantId) {
    var index = indexOf(variantId);
    var discounts = shares(discountsMinor);
    var tax = shares(taxMinor);
    return new Money(lines.get(index).subtotalMinor() - discounts[index] + tax[index], currency);
  }

  /**
   * The Refund amount of {@code quantity} more units of the Variant, given the units other RMAs
   * hold. The line's refundable amount is shared over its units in order: the first {@code n} come
   * to {@code floor(refundable × n / quantity)}, so the last unit takes what is left. An RMA gets
   * what the units held with its own come to, less what the others were given, so the Refund
   * amounts of the RMAs holding every unit come to the line's refundable amount exactly, even after
   * some were released and their units claimed again.
   */
  public Money refundAmount(String variantId, int quantity, Collection<Held> held) {
    var line = lines.get(indexOf(variantId));
    var refundable = refundableOf(variantId).amountMinor();
    var units = held.stream().mapToInt(Held::quantity).sum() + quantity;
    var given = held.stream().mapToLong(h -> h.refundAmount().amountMinor()).sum();
    var upTo = Math.floorDiv(refundable * Math.min(units, line.quantity()), line.quantity());
    return new Money(Math.max(upTo - given, 0), currency);
  }

  private int indexOf(String variantId) {
    return IntStream.range(0, lines.size())
        .filter(i -> lines.get(i).variantId().equals(variantId))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Order " + id + " has no line for " + variantId));
  }

  /** {@code amount} shared over the lines by subtotal, by the largest-remainder rule. */
  private long[] shares(long amount) {
    var subtotal = lines.stream().mapToLong(OrderLine::subtotalMinor).sum();
    var shares = new long[lines.size()];
    if (subtotal == 0) {
      return shares;
    }
    var remainders = new long[lines.size()];
    var left = amount;
    for (var i = 0; i < lines.size(); i++) {
      var exact = amount * lines.get(i).subtotalMinor();
      shares[i] = exact / subtotal;
      remainders[i] = exact % subtotal;
      left -= shares[i];
    }
    var byRemainder =
        IntStream.range(0, lines.size())
            .boxed()
            .sorted(Comparator.comparingLong((Integer i) -> remainders[i]).reversed())
            .toList();
    for (var i = 0; i < left; i++) {
      shares[byRemainder.get(i)]++;
    }
    return shares;
  }
}
