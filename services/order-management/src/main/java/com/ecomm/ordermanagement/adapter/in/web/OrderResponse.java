package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.Shipping;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An Order with the Customer it belongs to, its lines, what it comes to, where it goes and how, and
 * its Order Status history, oldest first. {@code discounts} is empty when it has none; {@code
 * shippingAddress} and {@code shippingOption} are left out for an Order placed before Shipping, as
 * are an address's optional fields when it has none.
 */
record OrderResponse(
    UUID id,
    String customerId,
    String status,
    List<Line> lines,
    Amount subtotal,
    List<Discount> discounts,
    Amount tax,
    Amount total,
    @JsonInclude(JsonInclude.Include.NON_NULL) Address shippingAddress,
    @JsonInclude(JsonInclude.Include.NON_NULL) ShippingOption shippingOption,
    Instant placedAt,
    List<HistoryEntry> statusHistory) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Address(
      String recipientName,
      String line1,
      String line2,
      String postcode,
      String city,
      String region,
      String country,
      String phone) {

    static Address of(Shipping.Address a) {
      return new Address(
          a.recipientName(),
          a.line1(),
          a.line2(),
          a.postcode(),
          a.city(),
          a.region(),
          a.country(),
          a.phone());
    }
  }

  record ShippingOption(String id, String carrier, Amount cost) {}

  record HistoryEntry(String status, Instant at, String changedBy, boolean backfilled) {}

  record Line(String variantId, int quantity, Amount unitPrice) {}

  /** A Campaign's Discount names it by ID and name, a Coupon's by its code; the others are null. */
  record Discount(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {}

  record Amount(long amountMinor, String currency) {

    static Amount of(Money money) {
      return new Amount(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  static OrderResponse of(Order order) {
    return new OrderResponse(
        order.id(),
        order.customerId(),
        order.status().name(),
        order.lines().stream()
            .map(l -> new Line(l.variantId(), l.quantity(), Amount.of(l.unitPrice())))
            .toList(),
        Amount.of(order.subtotal()),
        order.discounts().stream()
            .map(
                d ->
                    new Discount(
                        d.source().name(),
                        d.couponCode(),
                        d.campaignId(),
                        d.campaignName(),
                        Amount.of(d.amount())))
            .toList(),
        Amount.of(order.tax()),
        Amount.of(order.total()),
        order.shipping() == null ? null : Address.of(order.shipping().address()),
        order.shipping() == null
            ? null
            : new ShippingOption(
                order.shipping().option().id(),
                order.shipping().option().carrier(),
                Amount.of(order.shipping().option().cost())),
        order.placedAt(),
        order.statusHistory().stream()
            .map(
                e ->
                    new HistoryEntry(
                        e.status().name(), e.at(), e.changedBy().name(), e.backfilled()))
            .toList());
  }
}
