package com.ecomm.inventory.domain;

import java.time.Instant;
import java.util.Collection;

/**
 * A Variant's Stock, {@link #available}: its on-hand units less how many of them Reservations hold.
 * Neither count is ever negative, and Reservations never hold more than is on hand.
 */
public record Stock(String variantId, int onHand, int reserved) {

  /** The longest Variant ID Inventory stores. */
  public static final int MAX_VARIANT_ID_LENGTH = 64;

  public Stock {
    if (reserved < 0 || onHand < reserved) {
      throw new IllegalArgumentException(
          "Stock of " + variantId + " cannot hold " + reserved + " of " + onHand + " on hand");
    }
  }

  /**
   * The Stock of a Variant with {@code onHand} units, less what {@code reservations} hold of it at
   * {@code now}. A Reservation that no longer holds Stock, or doesn't name the Variant, counts for
   * nothing.
   */
  public static Stock of(
      String variantId, int onHand, Collection<Reservation> reservations, Instant now) {
    var reserved =
        reservations.stream()
            .filter(r -> r.holdsStockAt(now))
            .mapToInt(r -> r.items().quantityOf(variantId))
            .sum();
    return new Stock(variantId, onHand, reserved);
  }

  /**
   * A Variant new to Inventory, with nothing reserved. Throws {@link InvalidStockRequestException}
   * for a blank or overlong Variant ID or a negative count.
   */
  public static Stock newVariant(String variantId, int onHand) {
    if (variantId == null || variantId.isBlank() || variantId.length() > MAX_VARIANT_ID_LENGTH) {
      throw new InvalidStockRequestException(
          "a variantId is 1 to " + MAX_VARIANT_ID_LENGTH + " characters");
    }
    requireValidOnHand(onHand);
    return new Stock(variantId, onHand, 0);
  }

  /** How many units can still be sold or reserved. */
  public int available() {
    return onHand - reserved;
  }

  public boolean covers(int units) {
    return units <= available();
  }

  /** Callers check {@link #covers} first; taking more than is available throws. */
  public Stock decrementBy(int units) {
    if (!covers(units)) {
      throw new IllegalArgumentException("Only " + available() + " of " + variantId + " available");
    }
    return new Stock(variantId, onHand - units, reserved);
  }

  /**
   * The same Stock with a new on-hand count. Throws {@link OnHandBelowReservedException} when
   * Reservations hold more than that.
   */
  public Stock withOnHand(int onHand) {
    requireValidOnHand(onHand);
    if (onHand < reserved) {
      throw new OnHandBelowReservedException(variantId, reserved);
    }
    return new Stock(variantId, onHand, reserved);
  }

  private static void requireValidOnHand(int onHand) {
    if (onHand < 0) {
      throw new InvalidStockRequestException("onHand cannot be negative");
    }
  }
}
