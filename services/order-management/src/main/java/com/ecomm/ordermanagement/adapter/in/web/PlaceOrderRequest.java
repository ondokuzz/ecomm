package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.OrderLine;
import java.util.List;
import java.util.Optional;

/**
 * The Customer, the Order Lines priced at checkout, an optional discount and the tax: {@code
 * {"customerId", "lines": [{"variantId", "quantity", "unitPrice": {"amountMinor", "currency"}}],
 * "discount": {"couponCode", "amount"}, "tax": {"amountMinor", "currency"}}}. The values are taken
 * raw so that {@code 1.5} or {@code "2"} are rejected rather than coerced.
 */
record PlaceOrderRequest(Object customerId, List<Line> lines, DiscountBody discount, Amount tax) {

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

  record DiscountBody(Object couponCode, Amount amount) {

    Discount toDiscount() {
      if (couponCode != null && !(couponCode instanceof String)) {
        throw new InvalidOrderException("couponCode must be a string");
      }
      return new Discount((String) couponCode, amount == null ? null : amount.toMoney());
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

  Optional<Discount> toDiscount() {
    return Optional.ofNullable(discount).map(DiscountBody::toDiscount);
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
