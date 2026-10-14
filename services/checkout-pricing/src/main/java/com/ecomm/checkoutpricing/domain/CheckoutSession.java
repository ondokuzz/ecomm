package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A Customer's Cart held for checkout: its lines at the Prices captured when it started, every
 * Discount it is due (the running Campaigns', then the applied Coupon's, if any), their tax, and
 * the Reservation holding their Stock, until {@code expiresAt}. Paying it honours those Prices
 * whatever Catalog says by then.
 *
 * <p>Its Shipping address, once given, is validated and normalised, and {@code shippingOptions} are
 * the Shipping options quoted for it. {@code shippingOption} is the one chosen, whose Shipping cost
 * it keeps, as it keeps Prices, until the address changes. Without an address, it has no options
 * and none chosen; it can't be paid until it has both.
 */
public record CheckoutSession(
    String id,
    String customerId,
    PricedCart cart,
    List<Discount> discounts,
    Money tax,
    String reservationId,
    Instant expiresAt,
    ShippingAddress shippingAddress,
    List<ShippingOption> shippingOptions,
    ShippingOption shippingOption) {

  public CheckoutSession {
    discounts = List.copyOf(discounts);
    shippingOptions = shippingOptions == null ? List.of() : List.copyOf(shippingOptions);
  }

  /** A session without Shipping, as one starts. */
  public CheckoutSession(
      String id,
      String customerId,
      PricedCart cart,
      List<Discount> discounts,
      Money tax,
      String reservationId,
      Instant expiresAt) {
    this(id, customerId, cart, discounts, tax, reservationId, expiresAt, null, List.of(), null);
  }

  /** How long a Checkout Session holds the Cart. */
  public static final Duration LIFETIME = Duration.ofMinutes(15);

  /**
   * How much longer the Reservation holds the Stock than the session, so a payment that starts just
   * before the session expires can still commit it.
   */
  public static final Duration RESERVATION_GRACE = Duration.ofMinutes(2);

  /** When a Reservation for a session that expires at {@code expiresAt} should expire. */
  public static Instant reservationExpiresAt(Instant expiresAt) {
    return expiresAt.plus(RESERVATION_GRACE);
  }

  public Money subtotal() {
    return cart.subtotal();
  }

  /** What every Discount together takes off the subtotal: nothing without one. */
  public Money discountAmount() {
    return Discount.total(discounts, subtotal().currency());
  }

  /** The code of the Coupon applied, as Promotions upper-cased it; empty without one. */
  public Optional<String> couponCode() {
    return discounts.stream()
        .filter(d -> d.source() == Discount.Source.COUPON)
        .map(Discount::couponCode)
        .findFirst();
  }

  /** The chosen Shipping option's cost; nothing until one is chosen. */
  public Money shippingCost() {
    return shippingOption != null ? shippingOption.cost() : new Money(0, subtotal().currency());
  }

  /**
   * The subtotal less every Discount, plus the tax, plus the Shipping cost, as the Customer is
   * shown it; Discounts never touch the Shipping cost. The Order's own total is what's paid.
   */
  public Money total() {
    var subtotal = cart.subtotal();
    return new Money(
        Math.addExact(
            Math.addExact(
                Math.subtractExact(subtotal.amountMinor(), discountAmount().amountMinor()),
                tax.amountMinor()),
            shippingCost().amountMinor()),
        subtotal.currency());
  }

  /** This session with {@code discounts} in place of its own, and the tax that comes with them. */
  public CheckoutSession withDiscounts(List<Discount> discounts, Money tax) {
    return new CheckoutSession(
        id,
        customerId,
        cart,
        discounts,
        tax,
        reservationId,
        expiresAt,
        shippingAddress,
        shippingOptions,
        shippingOption);
  }

  /**
   * This session sent to {@code address}, with the {@code options} quoted for it. A chosen option
   * still offered stays chosen, at its new cost; one no longer offered is cleared.
   */
  public CheckoutSession withShippingAddress(
      ShippingAddress address, List<ShippingOption> options) {
    var stillChosen =
        shippingOption == null
            ? null
            : options.stream()
                .filter(o -> o.id().equals(shippingOption.id()))
                .findFirst()
                .orElse(null);
    return new CheckoutSession(
        id,
        customerId,
        cart,
        discounts,
        tax,
        reservationId,
        expiresAt,
        address,
        options,
        stillChosen);
  }

  /**
   * This session with the quoted Shipping option {@code optionId} chosen.
   *
   * @throws ShippingOptionNotQuotedException unless it was quoted for the session's address
   */
  public CheckoutSession withShippingOption(String optionId) {
    var chosen =
        shippingOptions.stream()
            .filter(o -> o.id().equals(optionId))
            .findFirst()
            .orElseThrow(() -> new ShippingOptionNotQuotedException(optionId));
    return new CheckoutSession(
        id,
        customerId,
        cart,
        discounts,
        tax,
        reservationId,
        expiresAt,
        shippingAddress,
        shippingOptions,
        chosen);
  }

  /** Whether it has a Shipping address and a chosen Shipping option, which paying needs. */
  public boolean hasShipping() {
    return shippingAddress != null && shippingOption != null;
  }

  public boolean isExpiredAt(Instant now) {
    return !now.isBefore(expiresAt);
  }
}
