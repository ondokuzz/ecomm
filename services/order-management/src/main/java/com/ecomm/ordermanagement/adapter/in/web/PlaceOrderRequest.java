package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.OrderLine;
import java.util.List;

/**
 * The Customer, the Order Lines priced at checkout, every Discount in the order they applied, and
 * the tax: {@code {"customerId", "lines": [{"variantId", "quantity", "unitPrice": {"amountMinor",
 * "currency"}}], "discounts": [{"source", "couponCode" | "campaignId" and "campaignName",
 * "amount"}], "tax": {"amountMinor", "currency"}}}. Leaving out {@code discounts} means none. The
 * values are taken raw so that {@code 1.5} or {@code "2"} are rejected rather than coerced.
 */
record PlaceOrderRequest(
    Object customerId,
    List<Line> lines,
    Object discount,
    List<DiscountBody> discounts,
    Amount tax) {

  record Line(Object variantId, Object quantity, Amount unitPrice) {

    OrderLine toOrderLine() {
      if (variantId != null && !(variantId instanceof String)) {
        throw new InvalidOrderException("variantId must be a string");
      }
      if (!(quantity instanceof Integer count)) {
        throw new InvalidOrderException("every line needs an integer quantity");
      }
      return new OrderLine(
          (String) variantId, count, unitPrice == null ? null : unitPrice.toMoney());
    }
  }

  record DiscountBody(
      Object source, Object couponCode, Object campaignId, Object campaignName, Amount amount) {

    Discount toDiscount() {
      Discount.Source parsed;
      try {
        parsed = Discount.Source.valueOf(text(source, "source"));
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new InvalidOrderException("a discount's source is CAMPAIGN or COUPON");
      }
      return new Discount(
          parsed,
          text(couponCode, "couponCode"),
          text(campaignId, "campaignId"),
          text(campaignName, "campaignName"),
          amount == null ? null : amount.toMoney());
    }

    private static String text(Object value, String field) {
      if (value != null && !(value instanceof String)) {
        throw new InvalidOrderException(field + " must be a string");
      }
      return (String) value;
    }
  }

  record Amount(Object amountMinor, Object currency) {

    Money toMoney() {
      if (!(amountMinor instanceof Integer || amountMinor instanceof Long)) {
        throw new InvalidOrderException("amountMinor must be an integer in the minor unit");
      }
      if (!(currency instanceof String code)) {
        throw new InvalidOrderException("currency must be an ISO 4217 code");
      }
      try {
        return Money.of(((Number) amountMinor).longValue(), code);
      } catch (IllegalArgumentException e) {
        throw new InvalidOrderException("unknown currency " + code);
      }
    }
  }

  String toCustomerId() {
    return CustomerIds.from(customerId);
  }

  /**
   * Every Discount, in order; none when {@code discounts} is left out. A body that still sends the
   * single {@code discount} of before Sprint 3 is refused rather than read as having none, which
   * would charge the Customer for a Discount they were given.
   */
  List<Discount> toDiscounts() {
    if (discount != null) {
      throw new InvalidOrderException("an order takes a list of discounts, not one discount");
    }
    if (discounts == null) {
      return List.of();
    }
    return discounts.stream()
        .map(
            body -> {
              if (body == null) {
                throw new InvalidOrderException("a discount can't be null");
              }
              return body.toDiscount();
            })
        .toList();
  }

  /**
   * The tax, which Checkout always sends, even when it is zero; the Order rejects a missing one.
   */
  Money toTax() {
    return tax == null ? null : tax.toMoney();
  }

  List<OrderLine> toOrderLines() {
    if (lines == null) {
      return List.of();
    }
    return lines.stream()
        .map(
            line -> {
              if (line == null) {
                throw new InvalidOrderException("a line can't be null");
              }
              return line.toOrderLine();
            })
        .toList();
  }
}
