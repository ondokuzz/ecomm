package com.ecomm.returnswarranty.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * What one delivered line of an Order is still eligible for: when its Return Period ends, when its
 * Warranty Window ends, and the units still free to claim. Either window may have ended already.
 *
 * @param warrantyWindowEndsAt null when its Product has no Warranty terms, or isn't projected yet
 */
public record Eligibility(
    String variantId,
    String productName,
    Instant returnPeriodEndsAt,
    Instant warrantyWindowEndsAt,
    Availability available) {

  /**
   * The line's eligibility, given the Order's RMAs.
   *
   * @param warranty null when its Product has no Warranty terms, or isn't projected yet
   */
  public static Eligibility of(
      Delivery delivery,
      DeliveredLine line,
      Duration returnPeriod,
      WarrantyTerms warranty,
      Collection<Rma> rmas) {
    return new Eligibility(
        line.variantId(),
        line.productName(),
        Window.returnPeriod(delivery.deliveredAt(), returnPeriod).endsAt(),
        warranty == null ? null : Window.warrantyWindow(delivery.deliveredAt(), warranty).endsAt(),
        Availability.of(line, Rma.holding(rmas, delivery.orderId(), line.variantId())));
  }
}
