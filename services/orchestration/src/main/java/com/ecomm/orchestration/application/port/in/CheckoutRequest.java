package com.ecomm.orchestration.application.port.in;

import java.util.List;

/**
 * What the checkout Saga is started with: the Checkout Session as it stood when the Customer paid
 * it, the Payment method they paid with, and the Correlation ID of their Pay request. Checkout
 * sends it as JSON, in this shape; the two services share no code.
 *
 * <p>The Payment method is the gateway's opaque token for the Customer's card, never a card number,
 * so it may sit in the workflow's history.
 */
public record CheckoutRequest(
    String checkoutSessionId,
    String customerId,
    List<Line> lines,
    List<Discount> discounts,
    Amount tax,
    String reservationId,
    String paymentMethod,
    String correlationId) {

  /** A line at the Price the session captured. */
  public record Line(String variantId, int quantity, Amount unitPrice) {}

  /**
   * A Campaign's or the Coupon's Discount ({@code source} {@code CAMPAIGN} or {@code COUPON}): a
   * Campaign's names it by ID and name, a Coupon's by its code, and the other fields are null.
   */
  public record Discount(
      String source, String couponCode, String campaignId, String campaignName, Amount amount) {}

  /** Money: an integer in the currency's minor unit, and its ISO 4217 code. */
  public record Amount(long amountMinor, String currency) {}
}
