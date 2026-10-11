package com.ecomm.inventory.application.port.out;

import com.ecomm.inventory.domain.Stock;
import java.time.Instant;
import java.util.Optional;

/**
 * A Variant's entry in the Stock cache: its Stock as of {@code version}, or that Inventory no
 * longer stocks it, good until {@code freshUntil}. After that it may be wrong, since a Reservation
 * holding some of the Variant may have expired, so it reads as a miss.
 */
public record CachedStock(
    String variantId, int onHand, int reserved, boolean stocked, long version, Instant freshUntil) {

  public static CachedStock of(Stock stock, long version, Instant freshUntil) {
    return new CachedStock(
        stock.variantId(), stock.onHand(), stock.reserved(), true, version, freshUntil);
  }

  /** A Variant Inventory stopped stocking at {@code version}. */
  public static CachedStock removed(String variantId, long version, Instant freshUntil) {
    return new CachedStock(variantId, 0, 0, false, version, freshUntil);
  }

  public int available() {
    return onHand - reserved;
  }

  /** Empty when Inventory no longer stocks the Variant. */
  public Optional<Stock> stock() {
    return stocked ? Optional.of(new Stock(variantId, onHand, reserved)) : Optional.empty();
  }

  public boolean isFreshAt(Instant now) {
    return now.isBefore(freshUntil);
  }
}
